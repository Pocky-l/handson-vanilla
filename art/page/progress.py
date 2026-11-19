"""Progress bar of the Hands-On Vanilla page: a plain green loading bar with the percentage in it.

The mod aims to replace TARGET (80%) of the game's player interfaces, so
    progress = released interfaces / (TARGET * all interfaces in INTERFACES).
Mark an interface released when a published file has it; then rerun and commit the PNG, and update the alt text in
CURSEFORGE.md and README.md.

Usage: python progress.py [out.png]      default: ../../docs/showcase/progress.png
"""
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = pathlib.Path(__file__).resolve().parent

TARGET = 0.8
# Every interface a player meets in survival (admin screens such as command and structure blocks are left out).
INTERFACES = {
    "crafting table": True, "furnace": True, "smoker": True, "blast furnace": True, "stonecutter": True,
    "smithing table": True, "grindstone": True, "brewing stand": True, "anvil": True,
    "enchanting table": False, "lectern": False, "loom": False, "cartography table": False, "beacon": False,
    "chest": False, "barrel": False, "ender chest": False, "shulker box": False, "hopper": False,
    "dispenser and dropper": False, "villager trading": False, "horse and llama": False, "player inventory": False,
    "creative inventory": False, "sign": False, "book and quill": False,
}

W, H = 1668, 34             # shown at 834 x 17 on the page: 2 image px per CSS px
TRACK = (38, 42, 40)
GREEN_TOP, GREEN_BOT = np.array([138, 222, 110]), np.array([70, 168, 72])
FONT = "C:/Windows/Fonts/seguisb.ttf"    # Segoe UI Semibold


def progress():
    return min(1.0, sum(INTERFACES.values()) / (TARGET * len(INTERFACES)))


def draw():
    share = progress()
    r = H // 2
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, W - 1, H - 1), r, fill=TRACK)

    fw = max(H, round(W * share))
    yy = np.linspace(0, 1, H)[:, None, None]
    col = GREEN_TOP * (1 - yy) + GREEN_BOT * yy
    fill = Image.fromarray(np.dstack([np.broadcast_to(col, (H, fw, 3)), np.full((H, fw, 1), 255)])
                           .clip(0, 255).astype(np.uint8), "RGBA")
    mask = Image.new("L", (fw, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, fw - 1, H - 1), r, fill=255)
    fill.putalpha(mask)
    img.alpha_composite(fill)

    text = f"{round(share * 100)}%"
    font = ImageFont.truetype(FONT, 22)
    d.text((W // 2 + 1, H // 2 + 2), text, font=font, fill=(0, 0, 0, 110), anchor="mm")
    d.text((W // 2, H // 2), text, font=font, fill=(255, 255, 255), anchor="mm")
    return img


if __name__ == "__main__":
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parents[1] / "docs" / "showcase" / "progress.png"
    draw().save(out, optimize=True)
    print("saved", out, "%d%%" % round(progress() * 100))
