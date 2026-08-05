#!/system/bin/sh
# mytools rebuild v5: added cryptsetup, gdisk, lsblk, setup, smartctl, strace, tcpdump

DST="/data/local/tmp/ai_work/mytools_builder"
BIN="$DST/binary_pool"
SRC="$DST/sources"
BUILD="/root/mytools_build"
ROOTFS="/data_mirror/data_ce/null/0/me.rerere.rikkahub/files/workspaces/48898dde-96b0-4838-8d94-8a2913c6f79b/linux"
CC="aarch64-linux-gnu-"

TOOL_LIST="adb bat chpst coreutils cryptsetup curl duf fastboot fd ffmpeg ffprobe file fzf gdisk git imgbox iperf3 jq ln lsblk lz4 magick nano openssl pv rg runit runit-init runsv runsvchdir runsvdir scp setup sftp smartctl sox soxi ssh strace sv svlogd tcpdump tree uchardet upx utmpset vim vips w3m yq zstd busybox toybox"

echo "=== 1/6 prepare ==="
/data/bin/linux "mkdir -p $BUILD && cd $BUILD && rm -f *.o mytools"

echo "=== 2/6 copy to container ==="
for f in $TOOL_LIST; do
  if [ "$f" = "ln" ]; then
    nsenter -t 1 -m -- cp "$SRC/ln_bin" "$ROOTFS/root/mytools_build/ln"
    echo "  cp ln (from sources/ln_bin)"
  elif [ -f "$BIN/$f" ]; then
    nsenter -t 1 -m -- cp "$BIN/$f" "$ROOTFS/root/mytools_build/$f"
    echo "  cp $f"
  else
    echo "  WARN: $f missing"
  fi
done
nsenter -t 1 -m -- cp "$BIN/help.txt" "$ROOTFS/root/mytools_build/help.txt"
nsenter -t 1 -m -- cp "$BIN/list.txt" "$ROOTFS/root/mytools_build/list.txt"
nsenter -t 1 -m -- cp "$SRC/start.s" "$ROOTFS/root/mytools_build/start.s"
nsenter -t 1 -m -- cp "$DST/mytools_main_v3.c" "$ROOTFS/root/mytools_build/mytools_main.c"
echo "  cp help.txt list.txt start.s mytools_main_v3.c"

echo "=== 3/6 ld -r -b binary for all ==="
/data/bin/linux "cd $BUILD && ${CC}ld -r -b binary -o help_txt.o help.txt"
echo "  help_txt.o"
/data/bin/linux "cd $BUILD && ${CC}ld -r -b binary -o list_txt.o list.txt"
echo "  list_txt.o"
for t in $TOOL_LIST; do
  echo "  $t.o"
  /data/bin/linux "cd $BUILD && ${CC}ld -r -b binary -o $t.o $t" 2>/dev/null
done

echo "=== 4/6 compile ==="
OBJS=""
for t in $TOOL_LIST; do OBJS="$OBJS $t.o"; done
OBJS="$OBJS help_txt.o list_txt.o"
/data/bin/linux "cd $BUILD && ${CC}gcc -static -nostdlib -ffreestanding -O2 -s -o mytools start.s mytools_main.c $OBJS -Wl,--gc-sections"

echo "=== 5/6 deploy ==="
nsenter -t 1 -m -- cp "$ROOTFS/root/mytools_build/mytools" "$DST/mytools_new"
cp "$DST/mytools_new" /data/local/tmp/ai_work/mytools
chmod 755 /data/local/tmp/ai_work/mytools

echo ""
echo "=== Done ==="
ls -lh "$DST/mytools_new"
echo "--- help test ---"
/data/local/tmp/ai_work/mytools --help 2>&1 | head -3
