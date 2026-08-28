import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * CamShot —— 供 camshot ELF 内嵌调用的 Android 拍照助手。
 *
 * 通过 app_process 以 root 身份运行。本身没有 Activity/Context，因此先用
 * ActivityThread.systemMain() 拿到系统 Context，再走 Camera2 API 抓拍一张
 * JPEG 并写盘。
 *
 * 用法:  CamShot <输出路径> [摄像头ID] [宽度] [高度]
 *         # 路径为文件则直接写；为目录则生成 camshot_<时间戳>.jpg 存到该目录
 *         # 摄像头ID 为空=自动(优先后置)；宽度/高度为 0=自动(取最大)
 */
public class CamShot {
    static final int TIMEOUT_S = 15;

    public static void main(String[] args) throws Exception {
        if (Build.VERSION.SDK_INT < 21) {
            System.err.println("ERROR: camshot requires Android 5.0 (API 21)+, got API "
                + Build.VERSION.SDK_INT);
            System.exit(1);
        }
        // 显示模式：camshot -show <图片>（不需要相机/Context）
        if (args.length > 0 && args[0] != null && args[0].equals("--show")) {
            try {
                String file = args.length > 1 ? args[1] : "";
                int cols = args.length > 2 ? parseInt(args[2]) : 80;
                int rot = (args.length > 3) ? parseInt(args[3]) : 0;
                String flip = (args.length > 4 && args[4] != null) ? args[4] : "";
                boolean invert = (args.length > 5) && parseInt(args[5]) != 0;
                if (file.length() == 0) throw new Exception("no image file given");
                showImage(file, cols, rot, flip, invert);
                System.exit(0);
            } catch (Throwable t) {
                System.err.println("ERROR: " + t.getMessage());
                t.printStackTrace();
                System.exit(2);
            }
        }
        // 视频录制模式：camvid -time ...（不经过拍照流程）
        if (args.length > 0 && args[0] != null && args[0].equals("--record")) {
            try {
                String out = args.length > 1 ? args[1] : "/tmp";
                String cam = (args.length > 2 && args[2] != null) ? args[2] : "";
                int w = (args.length > 3) ? parseInt(args[3]) : 0;
                int h = (args.length > 4) ? parseInt(args[4]) : 0;
                double sec = (args.length > 5) ? parseDouble(args[5]) : 5.0;
                long expMs = (args.length > 6) ? parseLong(args[6]) : 0;
                int iso = (args.length > 7) ? parseInt(args[7]) : 0;
                double ev = (args.length > 8) ? parseDouble(args[8]) : 0;
                boolean noaudio = (args.length > 12) && parseInt(args[12]) != 0;
                recordVideo(out, cam, w, h, sec, expMs, iso, ev, noaudio);
                System.exit(0);
            } catch (Throwable t) {
                System.err.println("ERROR: " + t.getMessage());
                t.printStackTrace();
                System.exit(2);
            }
        }
        // 视频播放模式：camvid -show <文件|->（第2参数为显示百分比 -%）
        if (args.length > 0 && args[0] != null && args[0].equals("--play")) {
            try {
                String file = args.length > 1 ? args[1] : "-";
                int sizePct = (args.length > 2) ? parseInt(args[2]) : 100;
                int rot = (args.length > 3) ? parseInt(args[3]) : 0;
                String flip = (args.length > 4 && args[4] != null) ? args[4] : "";
                boolean inv = (args.length > 5) && parseInt(args[5]) != 0;
                boolean noaudio = (args.length > 6) && parseInt(args[6]) != 0;
                playVideo(file, sizePct, rot, flip, inv, noaudio);
                System.exit(0);
            } catch (Throwable t) {
                System.err.println("ERROR: " + t.getMessage());
                t.printStackTrace();
                System.exit(2);
            }
        }
        String out = (args.length > 0 && args[0] != null && args[0].length() > 0)
            ? args[0] : "/tmp";
        String wantCam = (args.length > 1 && args[1] != null) ? args[1] : "";
        int wantW = (args.length > 2) ? parseInt(args[2]) : 0;
        int wantH = (args.length > 3) ? parseInt(args[3]) : 0;
        long wantExpMs = (args.length > 4) ? parseLong(args[4]) : 0;   // 0 = 自动曝光
        int wantIso = (args.length > 5) ? parseInt(args[5]) : 0;       // 0 = 自动ISO
        double wantEv = (args.length > 6 && args[6] != null && args[6].length() > 0)
            ? parseDouble(args[6]) : 0.0;                              // 0 = 不补偿
        int wantRot = (args.length > 7) ? parseInt(args[7]) : 0;       // 0/90/180/270
        String wantFlip = (args.length > 8 && args[8] != null) ? args[8] : "";
        boolean wantInvert = (args.length > 9) && parseInt(args[9]) != 0;

        try {
            Context ctx = getSystemContext();
            // 绕过 CameraManager 对 Settings.Global 的读取（root app_process 未注册为 app，
            // 直接读 settings 会抛 SecurityException）。预置 DesktopModeFlags 缓存即可跳过。
            patchDesktopModeFlags();
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) throw new Exception("CameraManager unavailable (no CAMERA service)");

            // 列出模式：只打印所有摄像头与分辨率
            if (out.equals("--list")) {
                listCameras(cm);
                System.exit(0);
            }

            String camId = pickCamera(cm, wantCam);
            System.err.println("using camera id: " + camId);

            // 后台线程承载 Camera2 回调
            HandlerThread ht = new HandlerThread("camshot");
            ht.start();
            Handler handler = new Handler(ht.getLooper());

            // 选 JPEG 输出尺寸：指定了宽高则就近匹配，否则取最大
            CameraCharacteristics chars = cm.getCameraCharacteristics(camId);
            StreamConfigurationMap map =
                chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new Exception("no stream configuration map for camera " + camId);
            Size size = chooseJpeg(map, wantW, wantH);
            if (size == null) throw new Exception("camera " + camId + " has no JPEG output");
            System.err.println("capture size: " + size.getWidth() + "x" + size.getHeight()
                + (wantW > 0 && wantH > 0 ? " (requested " + wantW + "x" + wantH + ")" : ""));

            final ImageReader reader =
                ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            final byte[][] jpeg = new byte[1][];
            final Throwable[] capErr = new Throwable[1];
            final CountDownLatch got = new CountDownLatch(1);
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader r) {
                    Image img = null;
                    try {
                        img = r.acquireLatestImage();
                        if (img != null) {
                            ByteBuffer buf = img.getPlanes()[0].getBuffer();
                            byte[] b = new byte[buf.remaining()];
                            buf.get(b);
                            jpeg[0] = b;
                        }
                    } catch (Throwable t) {
                        capErr[0] = t;
                    } finally {
                        if (img != null) img.close();
                        got.countDown();
                    }
                }
            }, handler);

            // 打开相机
            final CameraDevice[] dev = new CameraDevice[1];
            final Throwable[] openErr = new Throwable[1];
            final CountDownLatch opened = new CountDownLatch(1);
            if (cm.getCameraIdList().length == 0) {
                throw new Exception("no cameras available");
            }
            cm.openCamera(camId, new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice camera) { dev[0] = camera; opened.countDown(); }
                public void onDisconnected(CameraDevice camera) {
                    openErr[0] = new Exception("camera disconnected");
                    opened.countDown();
                }
                public void onError(CameraDevice camera, int error) {
                    openErr[0] = new Exception("camera open error code=" + error);
                    opened.countDown();
                }
            }, handler);
            if (!opened.await(TIMEOUT_S, TimeUnit.SECONDS))
                throw new Exception("timed out opening camera");
            if (dev[0] == null) throw new Exception("cannot open camera: "
                + (openErr[0] != null ? openErr[0].getMessage() : "unknown"));

            // 建立 capture session
            final CameraCaptureSession[] sess = new CameraCaptureSession[1];
            final Throwable[] sessErr = new Throwable[1];
            final CountDownLatch sessReady = new CountDownLatch(1);
            dev[0].createCaptureSession(
                Arrays.asList(reader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    public void onConfigured(CameraCaptureSession s) { sess[0] = s; sessReady.countDown(); }
                    public void onConfigureFailed(CameraCaptureSession s) {
                        sessErr[0] = new Exception("capture session configure failed");
                        sessReady.countDown();
                    }
                }, handler);
            if (!sessReady.await(TIMEOUT_S, TimeUnit.SECONDS))
                throw new Exception("timed out configuring capture session");
            if (sess[0] == null) throw new Exception("capture session failed: "
                + (sessErr[0] != null ? sessErr[0].getMessage() : "unknown"));

            // 触发单张抓拍
            CaptureRequest.Builder rb =
                dev[0].createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            rb.addTarget(reader.getSurface());
            rb.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            rb.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation(chars));
            applyExposure(rb, chars, wantExpMs, wantIso, wantEv);
            sess[0].capture(rb.build(), null, handler);

            if (!got.await(TIMEOUT_S, TimeUnit.SECONDS))
                throw new Exception("timed out waiting for image");
            if (jpeg[0] == null || jpeg[0].length == 0)
                throw new Exception("no image data captured"
                    + (capErr[0] != null ? ": " + capErr[0].getMessage() : ""));

            // 写盘
            // 需要旋转/翻转/反转时，先解码再变换再压缩保存
            byte[] outBytes = jpeg[0];
            boolean needXf = (wantRot != 0) || !wantFlip.isEmpty() || wantInvert;
            if (needXf) {
                android.graphics.Bitmap bmp =
                    android.graphics.BitmapFactory.decodeByteArray(jpeg[0], 0, jpeg[0].length);
                if (bmp == null) throw new Exception("cannot decode captured image for transform");
                android.graphics.Bitmap tb = transform(bmp, wantRot, wantFlip, wantInvert);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                if (!tb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, bos))
                    throw new Exception("JPEG 压缩失败");
                outBytes = bos.toByteArray();
                if (tb != bmp) tb.recycle();
                bmp.recycle();
            }
            // 写盘；-o - 表示输出到标准输出(管道)
            if (out.equals("-")) {
                System.out.write(outBytes);
                System.out.flush();
                System.err.println("saved to stdout (" + outBytes.length + " bytes)");
            } else {
                File target = new File(out);
                if (target.isDirectory()) {
                    target = new File(out, "camshot_" + System.currentTimeMillis() + ".jpg");
                }
                File parent = target.getAbsoluteFile().getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new Exception("cannot create output directory: " + parent);
                }
                FileOutputStream fos = new FileOutputStream(target);
                try {
                    fos.write(outBytes);
                } finally {
                    fos.close();
                }
                System.out.println("saved: " + target.getAbsolutePath()
                    + " (" + outBytes.length + " bytes)");
            }

            try { dev[0].close(); } catch (Throwable ignore) {}
            reader.close();
            ht.quitSafely();
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("ERROR: " + t.getMessage());
            t.printStackTrace();
            System.exit(2);
        }
    }

    /* 通过 ActivityThread 拿系统 Context（root app_process 无常规 Context）
       systemMain() 内部会 new Handler，必须先 prepareMainLooper() */
    static Context getSystemContext() throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object main = at.getMethod("systemMain").invoke(null);
        return (Context) at.getMethod("getSystemContext").invoke(main);
    }

    /* 绕过 CameraManager 的旋转覆盖 settings 读取：
       getRotationOverride() -> DesktopModeFlags.ENABLE_CAMERA_COMPAT_...isTrue()
       -> isFlagTrue() -> getToggleOverride()，而 getToggleOverride() 优先返回
       sCachedToggleOverride（若非 null 则不再读 Settings.Global）。
       root 裸进程无 app 身份，读 settings 会抛 SecurityException，故预置缓存为
       OVERRIDE_OFF（ordinal 0，isFlagTrue 走直接返回 flagFunction 的分支）。
       该字段/枚举为隐藏 API，需反射；失败仅提示，不阻断（部分设备无此逻辑）。 */
    static void patchDesktopModeFlags() {
        try {
            Class<?> dmf = Class.forName("android.window.DesktopModeFlags");
            Class<?> to = Class.forName("android.window.DesktopModeFlags$ToggleOverride");
            Object off = Enum.valueOf((Class) to, "OVERRIDE_OFF");
            java.lang.reflect.Field f = dmf.getDeclaredField("sCachedToggleOverride");
            f.setAccessible(true);
            f.set(null, off);
        } catch (Throwable t) {
            // 非致命：部分 ROM 没有该字段，届时 CameraManager 自身可能报错
        }
    }

    /* 摄像头选择：指定了 ID 且存在则用；否则优先后置（背）摄像头，再回退第一个 */
    static String pickCamera(CameraManager cm, String wantCam) throws Exception {
        String[] ids = cm.getCameraIdList();
        if (ids.length == 0) throw new Exception("no cameras available");
        if (wantCam != null && wantCam.length() > 0) {
            for (String id : ids) if (id.equals(wantCam)) return id;
            throw new Exception("camera id '" + wantCam + "' not found (available: "
                + join(ids) + ")");
        }
        String first = ids[0];
        String back = null;
        for (String id : ids) {
            CameraCharacteristics c = cm.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                back = id;
                break;
            }
        }
        return (back != null) ? back : first;
    }

    /* 取 JPEG 输出尺寸：宽高>0 就近匹配（优先完全一致），否则取最大 */
    static Size chooseJpeg(StreamConfigurationMap map, int wantW, int wantH) {
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) return null;
        List<Size> list = new ArrayList<Size>(Arrays.asList(sizes));
        if (wantW > 0 && wantH > 0) {
            Size best = null;
            long bestArea = Long.MAX_VALUE;
            for (Size s : list) {
                long area = Math.abs((long) s.getWidth() - wantW)
                          + Math.abs((long) s.getHeight() - wantH);
                if (area < bestArea) { bestArea = area; best = s; }
                if (area == 0) break;   // 完全一致
            }
            return best;
        }
        // 取最大面积
        Size largest = list.get(0);
        for (Size s : list)
            if ((long) s.getWidth() * s.getHeight()
                > (long) largest.getWidth() * largest.getHeight()) largest = s;
        return largest;
    }

    /* 列出所有摄像头：ID、朝向、全部 JPEG 分辨率（按面积降序） */
    static void listCameras(CameraManager cm) throws Exception {
        String[] ids = cm.getCameraIdList();
        System.out.println("cameras: " + ids.length);
        for (String id : ids) {
            CameraCharacteristics c = cm.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            String face = "?";
            if (facing != null) {
                if (facing == CameraCharacteristics.LENS_FACING_BACK) face = "BACK";
                else if (facing == CameraCharacteristics.LENS_FACING_FRONT) face = "FRONT";
                else if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) face = "EXTERNAL";
                else face = "OTHER(" + facing + ")";
            }
            System.out.println("camera " + id + " [" + face + "]");
            StreamConfigurationMap map =
                c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] jpegs = (map == null) ? null : map.getOutputSizes(ImageFormat.JPEG);
            if (jpegs == null || jpegs.length == 0) {
                System.out.println("  (no JPEG sizes)");
                continue;
            }
            List<Size> list = new ArrayList<Size>(Arrays.asList(jpegs));
            // 按面积降序（大分辨率在前）
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    Size a = list.get(i), b = list.get(j);
                    long aa = (long) a.getWidth() * a.getHeight();
                    long bb = (long) b.getWidth() * b.getHeight();
                    if (bb > aa) { Size t = list.get(i); list.set(i, b); list.set(j, t); }
                }
            }
            for (Size s : list)
                System.out.println("  " + s.getWidth() + "x" + s.getHeight());
        }
    }

    static int parseInt(String s) {
        if (s == null || s.length() == 0) return 0;
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    static long parseLong(String s) {
        if (s == null || s.length() == 0) return 0;
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    static double parseDouble(String s) {
        if (s == null || s.length() == 0) return 0.0;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    /* 把输入流全部读成字节（用于 -show - 从 stdin 读图） */
    static byte[] readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /* 曝光控制：
       - 手动曝光/ISO（expMs/iso>0）：关自动曝光，直接设传感器参数
       - 曝光补偿 EV（ev != 0）：自动曝光基础上按步进换算提亮/压暗；与手动互斥 */
    static void applyExposure(CaptureRequest.Builder rb, CameraCharacteristics chars,
                              long expMs, int iso, double ev) {
        boolean manual = false;
        try {
            if (expMs > 0) {
                long ns = expMs * 1000000L;   // 毫秒 -> 纳秒
                android.util.Range<Long> er =
                    chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                if (er != null) {
                    if (ns < er.getLower()) ns = er.getLower();
                    if (ns > er.getUpper()) ns = er.getUpper();
                }
                rb.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ns);
                System.err.println("exposure: " + expMs + "ms (" + ns + "ns)");
                manual = true;
            }
            if (iso > 0) {
                android.util.Range<Integer> ir =
                    chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                int v = iso;
                if (ir != null) {
                    if (v < ir.getLower()) v = ir.getLower();
                    if (v > ir.getUpper()) v = ir.getUpper();
                }
                rb.set(CaptureRequest.SENSOR_SENSITIVITY, v);
                System.err.println("iso: " + v);
                manual = true;
            }
            if (ev != 0.0) {
                if (manual) {
                    System.err.println("WARN: -ev 与 -e/-iso 互斥，忽略曝光补偿");
                } else {
                    applyEv(rb, chars, ev);
                }
            }
        } catch (Throwable t) {
            System.err.println("WARN: 设置曝光失败: " + t.getMessage());
        }
        if (manual) {
            rb.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            rb.set(CaptureRequest.CONTROL_AE_LOCK, true);
        }
    }

    /* 曝光补偿 EV -> CONTROL_AE_EXPOSURE_COMPENSATION（按步进换算并钳制到范围） */
    static void applyEv(CaptureRequest.Builder rb, CameraCharacteristics chars, double ev) {
        try {
            android.util.Range<Integer> range =
                chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            android.util.Rational step =
                chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            int steps;
            if (step != null && step.getDenominator() != 0) {
                double stepVal = (double) step.getNumerator() / step.getDenominator();
                steps = (int) Math.round(ev / stepVal);   // 如 +1EV 在 1/3 步进下 = +3
            } else {
                steps = (int) Math.round(ev);
            }
            if (range != null) {
                if (steps < range.getLower()) steps = range.getLower();
                if (steps > range.getUpper()) steps = range.getUpper();
            }
            rb.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, steps);
            System.err.println("ev: " + ev + " -> " + steps + " steps");
        } catch (Throwable t) {
            System.err.println("WARN: 设置曝光补偿失败: " + t.getMessage());
        }
    }

    static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(a[i]);
        }
        return sb.toString();
    }

    /* 以竖屏(旋转=0)估算 JPEG 方向。背摄 +0，前摄翻转。 */
    /* 终端彩色显示图片：半块字符 ▀ 上下各一个像素，24 位真彩 ANSI。
       path 为 "-" 时从标准输入读取图片字节。 */
    static void showImage(String path, int cols, int rot, String flip, boolean invert)
            throws Exception {
        boolean fromStdin = path.equals("-");
        byte[] data = null;
        String dispName = path;
        long fsize = 0;
        if (fromStdin) {
            data = readAll(System.in);
            fsize = data.length;
            dispName = "(stdin)";
        } else {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) throw new Exception("file not found: " + path);
            fsize = f.length();
        }

        android.graphics.BitmapFactory.Options opt = new android.graphics.BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        if (fromStdin) android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        else            android.graphics.BitmapFactory.decodeFile(path, opt);
        int W = opt.outWidth, H = opt.outHeight;
        if (W <= 0 || H <= 0) throw new Exception("cannot decode image: " + path);
        if (cols <= 0) cols = 80;

        // 采样缩小，避免大图 OOM
        int sample = 1;
        while (W / (sample * 2) > cols * 2 || H / (sample * 2) > 3000) sample *= 2;
        opt.inJustDecodeBounds = false;
        opt.inSampleSize = sample;
        android.graphics.Bitmap bmp = fromStdin
            ? android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, opt)
            : android.graphics.BitmapFactory.decodeFile(path, opt);
        if (bmp == null) throw new Exception("cannot decode image: " + path);

        // 先应用旋转/翻转/反转（全分辨率下做，避免缩小后丢细节）
        boolean needXf = (rot != 0) || (flip != null && flip.length() > 0) || invert;
        if (needXf) {
            android.graphics.Bitmap tb = transform(bmp, rot, flip, invert);
            if (tb != bmp) bmp.recycle();
            bmp = tb;
            W = bmp.getWidth();
            H = bmp.getHeight();
        }

        int tw = cols;
        int th = Math.max(2, (int) Math.round((double) H * tw / W / 2.0) * 2);
        android.graphics.Bitmap scaled =
            android.graphics.Bitmap.createScaledBitmap(bmp, tw, th, true);

        StringBuilder sb = new StringBuilder();
        sb.append("image: ").append(dispName)
          .append("  ").append(W).append("x").append(H)
          .append("  size=").append(fsize).append(" bytes\n");
        sb.append("\033[0m");
        for (int y = 0; y < th; y += 2) {
            for (int x = 0; x < tw; x++) {
                int top = scaled.getPixel(x, y);
                int bot = (y + 1 < th) ? scaled.getPixel(x, y + 1) : top;
                sb.append("\033[38;2;").append((top >> 16) & 0xff).append(';')
                  .append((top >> 8) & 0xff).append(';').append(top & 0xff).append('m')
                  .append("\033[48;2;").append((bot >> 16) & 0xff).append(';')
                  .append((bot >> 8) & 0xff).append(';').append(bot & 0xff).append('m')
                  .append('\u2580');   // 上半块
            }
            sb.append("\033[0m\n");
        }
        sb.append("\033[0m");
        System.out.print(sb.toString());

        if (scaled != bmp) scaled.recycle();
        bmp.recycle();
    }

    static int jpegOrientation(CameraCharacteristics chars) {
        Integer sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensor == null) sensor = 0;
        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
        int deg = sensor % 360;
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            deg = (360 - deg) % 360;   // 前摄镜像
        }
        return deg;
    }

    /* 旋转(顺时针)+镜像翻转+颜色反转，返回新 Bitmap */
    static android.graphics.Bitmap transform(android.graphics.Bitmap src, int rot,
                                             String flip, boolean invert) {
        android.graphics.Bitmap b = src;
        android.graphics.Matrix m = new android.graphics.Matrix();
        if (rot != 0) m.postRotate(rot);
        if (flip != null) {
            if (flip.equals("h"))      m.postScale(-1, 1);
            else if (flip.equals("v")) m.postScale(1, -1);
            else if (flip.equals("hv")) m.postScale(-1, -1);
        }
        if (!m.isIdentity()) {
            android.graphics.Bitmap nb = android.graphics.Bitmap.createBitmap(
                src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            if (b != src) b.recycle();
            b = nb;
        }
        if (invert) {
            android.graphics.Bitmap.Config cfg = (b.getConfig() != null)
                ? b.getConfig() : android.graphics.Bitmap.Config.ARGB_8888;
            android.graphics.Bitmap nb =
                android.graphics.Bitmap.createBitmap(b.getWidth(), b.getHeight(), cfg);
            android.graphics.Canvas c = new android.graphics.Canvas(nb);
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                 0, -1, 0, 0, 255,
                 0, 0, -1, 0, 255,
                 0, 0, 0, 1, 0});
            android.graphics.Paint p = new android.graphics.Paint();
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            c.drawBitmap(b, 0, 0, p);
            if (b != src) b.recycle();
            b = nb;
        }
        return b;
    }

    /* 把 Bitmap 渲染成终端 ANSI 彩字符（半块字符，2行像素/行） */
    static String renderToTerminal(android.graphics.Bitmap bmp, int cols) {
        int W = bmp.getWidth(), H = bmp.getHeight();
        int tw = cols;
        int th = Math.max(2, (int) Math.round((double) H * tw / W / 2.0) * 2);
        android.graphics.Bitmap scaled =
            android.graphics.Bitmap.createScaledBitmap(bmp, tw, th, true);
        StringBuilder sb = new StringBuilder("\033[0m");
        for (int y = 0; y < th; y += 2) {
            for (int x = 0; x < tw; x++) {
                int top = scaled.getPixel(x, y);
                int bot = (y + 1 < th) ? scaled.getPixel(x, y + 1) : top;
                sb.append("\033[38;2;").append((top >> 16) & 0xff).append(';')
                  .append((top >> 8) & 0xff).append(';').append(top & 0xff).append('m')
                  .append("\033[48;2;").append((bot >> 16) & 0xff).append(';')
                  .append((bot >> 8) & 0xff).append(';').append(bot & 0xff).append('m')
                  .append('\u2580');
            }
            sb.append("\033[0m\n");
        }
        sb.append("\033[0m");
        if (scaled != bmp) scaled.recycle();
        return sb.toString();
    }

    /* RGB -> xterm 256 色索引 */
    static int rgb256(int r, int g, int b) {
        if (Math.abs(r - g) < 10 && Math.abs(g - b) < 10 && Math.abs(r - b) < 10) {
            return 232 + (int) Math.round((r + g + b) / 3.0 / 255.0 * 23.0);
        }
        int ri = (int) Math.round(r / 255.0 * 5.0);
        int gi = (int) Math.round(g / 255.0 * 5.0);
        int bi = (int) Math.round(b / 255.0 * 5.0);
        return 16 + 36 * ri + 6 * gi + bi;
    }

    /* 快速终端渲染（视频用）：256 色，数据量小，保证每帧能画完 */
    static String renderToTerminalFast(android.graphics.Bitmap bmp, int cols) {
        int W = bmp.getWidth(), H = bmp.getHeight();
        int tw = cols;
        int th = Math.max(2, (int) Math.round((double) H * tw / W / 2.0) * 2);
        android.graphics.Bitmap scaled =
            android.graphics.Bitmap.createScaledBitmap(bmp, tw, th, true);
        StringBuilder sb = new StringBuilder("\033[0m");
        for (int y = 0; y < th; y += 2) {
            for (int x = 0; x < tw; x++) {
                int top = scaled.getPixel(x, y);
                int bot = (y + 1 < th) ? scaled.getPixel(x, y + 1) : top;
                int ft = rgb256((top >> 16) & 0xff, (top >> 8) & 0xff, top & 0xff);
                int fb = rgb256((bot >> 16) & 0xff, (bot >> 8) & 0xff, bot & 0xff);
                if (ft == fb) {
                    // 上下同色：只用一个背景色空格，省一半数据
                    sb.append("\033[48;5;").append(ft).append("m ");
                } else {
                    sb.append("\033[38;5;").append(ft).append("m\033[48;5;")
                      .append(fb).append("m\u2580");
                }
            }
            sb.append("\033[0m\n");
        }
        sb.append("\033[0m");
        if (scaled != bmp) scaled.recycle();
        return sb.toString();
    }

    /* ==================== 视频：自定义容器 CAMV2（H.264 视频 + WAV/PCM 音频），自己拍自己播 ==================== */

    /* 录像：Camera2 → H.264 编码器；AudioRecord → WAV/PCM；写入 CAMV2 容器 */
    static void recordVideo(String out, String wantCam, int wantW, int wantH, double timeSec,
                            long expMs, int iso, double ev, boolean noaudio)
            throws Exception {
        Context ctx = getSystemContext();
        patchDesktopModeFlags();
        CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        String camId = pickCamera(cm, wantCam);
        CameraCharacteristics chars = cm.getCameraCharacteristics(camId);
        StreamConfigurationMap map =
            chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size size = chooseJpeg(map, wantW, wantH);
        if (size == null) throw new Exception("no video size for camera " + camId);
        int W = size.getWidth(), H = size.getHeight();
        int FPS = 30;
        int sr = 44100, chn = 1, bps = 2;
        long totalMs = (long) (timeSec * 1000);

        OutputStream os = out.equals("-")
            ? new FileOutputStream(FileDescriptor.out) : new FileOutputStream(out);
        final DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(os));
        dos.writeBytes("CAMV2");
        dos.writeInt(W); dos.writeInt(H); dos.writeInt(sr); dos.writeInt(chn); dos.writeInt(bps);

        // ---- 视频编码器（H.264, surface 输入）----
        MediaCodec venc = MediaCodec.createEncoderByType("video/avc");
        MediaFormat vf = MediaFormat.createVideoFormat("video/avc", W, H);
        vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        vf.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
        vf.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        venc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface vSurf = venc.createInputSurface();
        venc.start();

        // ---- 编码器 drain 线程：把 H.264 输出写进容器 ----
        final boolean[] vdone = new boolean[]{false};
        Thread vdrain = new Thread(new Runnable() {
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                try {
                    while (!vdone[0]) {
                        int oi = venc.dequeueOutputBuffer(info, 10000);
                        if (oi >= 0) {
                            if (info.size > 0) {
                                java.nio.ByteBuffer bb = venc.getOutputBuffer(oi);
                                byte[] d = new byte[info.size];
                                bb.position(info.offset);
                                bb.get(d);
                                writeChunk(dos, "H264", d, info.presentationTimeUs);
                            }
                            venc.releaseOutputBuffer(oi, false);
                        }
                    }
                } catch (Throwable ignore) {}
            }
        });
        vdrain.start();

        // ---- 打开相机，画面进编码器 surface ----
        HandlerThread ht = new HandlerThread("camvid"); ht.start();
        final Handler handler = new Handler(ht.getLooper());
        final CameraDevice[] dev = new CameraDevice[1];
        final CountDownLatch opened = new CountDownLatch(1);
        cm.openCamera(camId, new CameraDevice.StateCallback() {
            public void onOpened(CameraDevice c) { dev[0] = c; opened.countDown(); }
            public void onDisconnected(CameraDevice c) { opened.countDown(); }
            public void onError(CameraDevice c, int e) { opened.countDown(); }
        }, handler);
        opened.await(10, TimeUnit.SECONDS);
        if (dev[0] == null) throw new Exception("cannot open camera " + camId);
        final CameraCaptureSession[] sess = new CameraCaptureSession[1];
        final CountDownLatch sready = new CountDownLatch(1);
        dev[0].createCaptureSession(Arrays.asList(vSurf),
            new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) { sess[0] = s; sready.countDown(); }
                public void onConfigureFailed(CameraCaptureSession s) { sready.countDown(); }
            }, handler);
        sready.await(10, TimeUnit.SECONDS);
        if (sess[0] == null) throw new Exception("capture session failed");
        CaptureRequest.Builder rb = dev[0].createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        rb.addTarget(vSurf);
        rb.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        applyExposure(rb, chars, expMs, iso, ev);
        sess[0].setRepeatingRequest(rb.build(), null, handler);

        // ---- 音频采集：AudioRecord PCM → AUD0 块 ----
        final boolean[] astop = new boolean[]{false};
        Thread audioCap = null;
        if (!noaudio) {
            int minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            final AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.MIC, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2);
            rec.startRecording();
            audioCap = new Thread(new Runnable() {
                public void run() {
                    byte[] buf = new byte[8192];
                    try {
                        while (!astop[0]) {
                            int n = rec.read(buf, 0, buf.length);
                            if (n > 0) {
                                byte[] d = new byte[n];
                                System.arraycopy(buf, 0, d, 0, n);
                                writeChunk(dos, "AUD0", d, 0);
                            }
                        }
                    } catch (Throwable ignore) {
                    } finally {
                        try { rec.stop(); } catch (Throwable ignore) {}
                        rec.release();
                    }
                }
            });
            audioCap.start();
        }

        System.err.println("recording " + totalMs + "ms (" + W + "x" + H + " fps=" + FPS + (noaudio ? ", noaudio" : "") + ")...");
        Thread.sleep(totalMs);

        // ---- 收尾 ----
        if (audioCap != null) { astop[0] = true; audioCap.join(1000); }
        sess[0].stopRepeating();
        dev[0].close();
        vdone[0] = true;
        vdrain.join(1500);
        venc.stop(); venc.release();
        ht.quitSafely();
        dos.flush();
        dos.close();
        System.err.println("recorded -> " + out);
    }

    /* 写一块：type(4) + size(4) + [pts(8)] + data */
    static synchronized void writeChunk(DataOutputStream dos, String type, byte[] data, long pts)
            throws Exception {
        if (type.equals("H264")) {
            dos.writeBytes("H264");
            dos.writeInt(data.length + 8);
            dos.writeLong(pts);
        } else {
            dos.writeBytes("AUD0");
            dos.writeInt(data.length);
        }
        dos.write(data);
    }

    /* 播放：解析 CAMV2 → H.264 解码到终端 + WAV/PCM 到 AudioTrack。sizePct 为显示百分比 */
    static void playVideo(String path, int sizePct, int rot, String flip, boolean inv, boolean noaudio)
            throws Exception {
        InputStream fin = path.equals("-") ? System.in : new FileInputStream(path);
        DataInputStream din = new DataInputStream(new BufferedInputStream(fin));
        byte[] magic = new byte[5];
        din.readFully(magic);
        if (!new String(magic, "ISO-8859-1").equals("CAMV2"))
            throw new Exception("not a CAMV2 video (请用新版 camvid 录制)");
        int W = din.readInt(), H = din.readInt();
        int sr = din.readInt(), chn = din.readInt(), bps = din.readInt();

        // 先把所有块读进内存（视频带 pts，音频纯 PCM）
        java.util.ArrayList<byte[]> vSamples = new java.util.ArrayList<byte[]>();
        java.util.ArrayList<Long> vPts = new java.util.ArrayList<Long>();
        java.util.ArrayList<byte[]> aSamples = new java.util.ArrayList<byte[]>();
        byte[] type = new byte[4];
        byte[] csd = null;
        while (true) {
            int t = din.read();
            if (t < 0) break;
            type[0] = (byte) t;
            din.readFully(type, 1, 3);
            String s = new String(type, "ISO-8859-1");
            if (s.equals("H264")) {
                int len = din.readInt();
                long pts = din.readLong();
                byte[] d = new byte[len - 8];
                din.readFully(d);
                if (csd == null) csd = d;   // 第一块是 SPS/PPS 配置
                else { vSamples.add(d); vPts.add(pts); }
            } else if (s.equals("AUD0")) {
                int len = din.readInt();
                byte[] d = new byte[len];
                din.readFully(d);
                aSamples.add(d);
            } else break;
        }
        if (csd == null) throw new Exception("no video config");

        // 显示列数：默认 44 列 * 百分比
        int cols = Math.max(8, (int) Math.round(44.0 * Math.max(1, sizePct) / 100.0));
        int outH = Math.max(2, (int) Math.round((double) H * cols / W / 2.0) * 2);

        // ---- 视频解码器 ----
        MediaFormat fmt = MediaFormat.createVideoFormat("video/avc", W, H);
        fmt.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(csd));
        MediaCodec dec = MediaCodec.createDecoderByType("video/avc");
        dec.configure(fmt, null, null, 0);
        dec.start();

        // ---- 音频播放（WAV/PCM 直接 AudioTrack）----
        AudioTrack track = null;
        if (!noaudio && aSamples.size() > 0) {
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(sr)
                    .setChannelMask(chn >= 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(65536, sr * chn * bps * 2))
                .setTransferMode(AudioTrack.MODE_STREAM).build();
            track.play();
            final AudioTrack ft = track;
            final java.util.ArrayList<byte[]> fa = aSamples;
            Thread at = new Thread(new Runnable() {
                public void run() {
                    try {
                        for (byte[] d : fa) ft.write(d, 0, d.length);
                    } catch (Throwable ignore) {}
                }
            });
            at.setDaemon(true);
            at.start();
        }

        // ---- 喂视频输入 + 取输出渲染（按 PTS 节奏）----
        System.out.print("\033[?25l");
        System.out.flush();
        int inIdx = 0;
        boolean eos = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long firstPts = -1;
        long startReal = System.currentTimeMillis();
        int rendered = 0;
        while (true) {
            if (!eos) {
                int in = dec.dequeueInputBuffer(20000);
                if (in >= 0) {
                    if (inIdx < vSamples.size()) {
                        byte[] d = vSamples.get(inIdx);
                        long pts = vPts.get(inIdx);
                        java.nio.ByteBuffer inBuf = dec.getInputBuffer(in);
                        inBuf.clear(); inBuf.put(d);
                        dec.queueInputBuffer(in, 0, d.length, pts, 0);
                        inIdx++;
                    } else {
                        dec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eos = true;
                    }
                }
            }
            int oi = dec.dequeueOutputBuffer(info, 20000);
            if (oi >= 0) {
                if (info.size > 0) {
                    long pts = info.presentationTimeUs;
                    if (firstPts < 0) { firstPts = pts; startReal = System.currentTimeMillis(); }
                    android.graphics.Bitmap bmp = yuvToBitmap(dec.getOutputImage(oi), outH);
                    if (bmp != null) {
                        android.graphics.Bitmap tb = (rot != 0 || (flip != null && flip.length() > 0) || inv)
                            ? transform(bmp, rot, flip, inv) : bmp;
                        System.out.print(rendered == 0 ? "\033[H\033[J" : "\033[H");
                        System.out.print(renderToTerminal(tb, cols));
                        System.out.flush();
                        if (tb != bmp) tb.recycle();
                        bmp.recycle();
                        rendered++;
                        // 按 PTS 节奏：让每帧显示在正确时间点
                        long target = startReal + (pts - firstPts) / 1000;
                        long wait = target - System.currentTimeMillis();
                        if (wait > 0) Thread.sleep(wait);
                    }
                }
                dec.releaseOutputBuffer(oi, true);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (eos) {
                    // 可能已结束
                }
            }
            if (eos && inIdx >= vSamples.size()) {
                // 已喂完且没有更多输出时退出（兜底）
            }
        }
        dec.stop(); dec.release();
        if (track != null) { try { track.stop(); } catch (Throwable ignore) {} track.release(); }
        System.out.print("\033[0m\033[?25h");
        System.out.flush();
        System.err.println("played " + rendered + " frames");
    }
    static android.graphics.Bitmap yuvToBitmap(android.media.Image img, int outH) {
        try {
            android.media.Image.Plane[] pl = img.getPlanes();
            int w = img.getWidth(), h = img.getHeight();
            java.nio.ByteBuffer y = pl[0].getBuffer();
            java.nio.ByteBuffer u = pl[1].getBuffer();
            java.nio.ByteBuffer v = pl[2].getBuffer();
            int yps = pl[0].getRowStride(), ups = pl[1].getRowStride(), vps = pl[2].getRowStride();
            int yps_ = pl[0].getPixelStride(), ups_ = pl[1].getPixelStride(), vps_ = pl[2].getPixelStride();
            int tw = (int) Math.round((double) w * outH / h);
            if (tw < 1) tw = 1;
            int[] px = new int[tw * outH];
            for (int yy = 0; yy < outH; yy++) {
                int sy = (int)((double) yy / outH * (h - 1));
                for (int xx = 0; xx < tw; xx++) {
                    int sx = (int)((double) xx / tw * (w - 1));
                    int yi = sy * yps + sx * yps_;
                    int ui = (sy / 2) * ups + (sx / 2) * ups_;
                    int vi = (sy / 2) * vps + (sx / 2) * vps_;
                    int Y = (y.get(yi) & 0xff) - 16;
                    int U = (u.get(ui) & 0xff) - 128;
                    int V = (v.get(vi) & 0xff) - 128;
                    int r = (int)(1.164 * Y + 1.596 * V);
                    int g = (int)(1.164 * Y - 0.392 * U - 0.813 * V);
                    int b = (int)(1.164 * Y + 2.017 * U);
                    if (r < 0) r = 0; if (r > 255) r = 255;
                    if (g < 0) g = 0; if (g > 255) g = 255;
                    if (b < 0) b = 0; if (b > 255) b = 255;
                    px[yy * tw + xx] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
            android.graphics.Bitmap bmp =
                android.graphics.Bitmap.createBitmap(px, tw, outH, android.graphics.Bitmap.Config.ARGB_8888);
            return bmp;
        } catch (Throwable t) { return null; }
    }
}
