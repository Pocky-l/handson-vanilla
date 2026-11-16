"""CurseForge avatar of Hands-On Vanilla: a diamond pickaxe laid by hand on the crafting table and taken, a loop from an
empty table to an empty table.

Frames: Shots `hv_avatar.txt` of Diegesis (table camera moved by a temporary resource pack to position 6,45,-9.5, target
6,16,8.2, fov 40; book shut). Frames with an item tooltip are dropped.

Usage: python avatar.py <frames dir> <out.gif> [size]
"""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

import imageio_ffmpeg
import numpy as np
from PIL import Image

GIFSICLE = os.environ.get("GIFSICLE", "gifsicle")
CROP = (290, 10, 910, 630)
FIRST, LAST = 12, 234


def has_tooltip(img):
    a = np.asarray(img).astype(int)
    tip = (a[..., 0] > 8) & (a[..., 0] < 30) & (a[..., 2] > 8) & (a[..., 2] < 30) & (a[..., 1] < 10)
    return tip.sum() > 200


if __name__ == "__main__":
    src, out = pathlib.Path(sys.argv[1]), sys.argv[2]
    size = int(sys.argv[3]) if len(sys.argv) > 3 else 256
    files = sorted(src.glob("f*.png"))[FIRST:LAST]
    frames = []
    for f in files:
        img = Image.open(f).convert("RGB").crop(CROP)
        if not has_tooltip(img):
            frames.append(img.resize((size, size), Image.LANCZOS))
    tmp = tempfile.mkdtemp()
    for k, f in enumerate(frames):
        f.save(os.path.join(tmp, "%04d.png" % k))
    raw = os.path.join(tmp, "raw.gif")
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-framerate", "25", "-i",
                    os.path.join(tmp, "%04d.png"), "-vf",
                    "split[a][b];[a]palettegen=max_colors=128:stats_mode=full[p];[b][p]paletteuse=dither=none",
                    "-loop", "0", raw], check=True)
    subprocess.run([GIFSICLE, "-O3", "--lossy=20", raw, "-o", out], check=True)
    shutil.rmtree(tmp)
    print(out, len(frames), "frames", round(os.path.getsize(out) / 1e3), "KB")
