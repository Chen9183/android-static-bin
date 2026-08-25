; lns_min.s — arm64 极简 symlinkat 软链接工具 (168 字节 ELF)
; 用法: ./lns_168 <target> <linkname>
; 若 argc==3 创建软链接, 否则退出码 1
;
; 机器码                   指令
; ----------------         ----------------------------------------
; f94003e9                 ldr  x9, [sp]          ; argc (栈顶, 64位)
; f1000d3f                 cmp  x9, #3
; 54000101                 b.ne error            ; argc != 3 -> error
; f9400be0                 ldr  x0, [sp, #16]     ; argv[1] = target
; 92800c61                 movn x1, #99           ; x1 = -100 = AT_FDCWD
; f9400fe2                 ldr  x2, [sp, #24]     ; argv[2] = linkname
; d2800488                 mov  x8, #36           ; symlinkat (arm64)
; d4000001                 svc  #0
;                          exit:                  ; (共用退出)
; d2800ba8                 mov  x8, #93           ; exit (arm64)
; d4000001                 svc  #0
;                          error:
; d2800020                 mov  x0, #1            ; 错误退出码
; 17fffffd                 b    exit

; --- 构建说明 ---
; 此 ELF 是手工构造的: ELF头64B + 程序头56B + 机器码48B = 168B
; 用 Python 生成二进制, 见对话中脚本; 或等价汇编:
;   aarch64-linux-gnu-gcc -nostdlib -static -Wl,-N,-s -o lns lns.s
