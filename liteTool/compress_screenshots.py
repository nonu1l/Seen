"""
轻量图片压缩脚本 — 将 PNG 转 JPG，保持文件名仅扩展名变更。
用法: python compress_screenshots.py [目录] [质量]
"""
import sys
from pathlib import Path
from PIL import Image


def compress(directory: str, quality: int = 75) -> None:
    root = Path(directory)
    if not root.is_dir():
        print(f"目录不存在: {root}")
        sys.exit(1)

    pngs = list(root.glob("*.png")) + list(root.glob("*.PNG"))
    if not pngs:
        print("未找到 PNG 文件")
        return

    for src in pngs:
        dst = src.with_suffix(".jpg")
        try:
            with Image.open(src) as im:
                # PNG 含 alpha 通道时填白底（JPG 不支持透明）
                if im.mode in ("RGBA", "LA", "P"):
                    bg = Image.new("RGB", im.size, (255, 255, 255))
                    im_rgba = im.convert("RGBA")
                    bg.paste(im_rgba, mask=im_rgba.split()[-1])
                    im = bg
                else:
                    im = im.convert("RGB")
                im.save(dst, "JPEG", quality=quality, optimize=True)
        except Exception as e:
            print(f"[FAIL] {src.name}: {e}")
            continue

        before = src.stat().st_size
        after = dst.stat().st_size
        ratio = after / before * 100
        print(f"[OK]   {src.name}  {before/1024:>7.1f} KB  ->  {after/1024:>7.1f} KB  ({ratio:4.1f}%)")


if __name__ == "__main__":
    directory = sys.argv[1] if len(sys.argv) > 1 else "."
    quality = int(sys.argv[2]) if len(sys.argv) > 2 else 75
    compress(directory, quality)
