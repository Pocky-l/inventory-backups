"""Title banner of the Inventory Backups page: an in-game frame (darkened and out of focus behind the title), the logo,
the name in Fraunces (OFL, fonts/) and a card with a piece of the real admin screen on the right.

Usage (from art/page): python banner.py <background frame> <admin screen shot> <out.png>
"""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1600, 520
LOGO = "../../common/src/main/resources/logo.png"
# A piece of the admin screen at 1920x1080: the inventory panel of the selected moment.
CARD_BOX = (470, 340, 1036, 800)


def scene(bg_path):
    src = Image.open(bg_path).convert("RGB")
    src = src.resize((W, round(src.height * W / src.width)), Image.LANCZOS)
    top = max(0, min(src.height - H, int(src.height * 0.5 - H / 2)))
    crop = src.crop((0, top, W, top + H)).filter(ImageFilter.GaussianBlur(6))
    bg = np.array(crop, np.float32)
    x = np.linspace(0, 1, W)[None, :, None]
    y = np.linspace(0, 1, H)[:, None, None]
    shade = np.clip(1.0 - 0.82 * np.clip((0.95 - x) / 0.7, 0, 1) ** 0.9, 0.16, 1.0)
    shade = shade * (1.0 - 0.35 * np.clip((y - 0.7) / 0.3, 0, 1)) * 0.78
    tint = np.array([10, 16, 30], np.float32)  # night blue of the admin screen
    bg = bg * shade + tint * (1 - shade)
    return Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def card(shot_path):
    shot = Image.open(shot_path).convert("RGB").crop(CARD_BOX)
    scale = 0.78
    shot = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    pad = 3
    framed = Image.new("RGBA", (shot.width + 2 * pad, shot.height + 2 * pad), (90, 150, 230, 255))
    framed.paste(shot, (pad, pad))
    return framed


def drop_shadow(layer, blur, alpha):
    shadow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    shadow.putalpha(layer.getchannel("A").point(lambda v: int(v * alpha)))
    return shadow.filter(ImageFilter.GaussianBlur(blur))


def compose(bg_path, shot_path):
    img = scene(bg_path)
    # The card: a real piece of the admin screen, tilted a little, with a soft shadow.
    c = card(shot_path).rotate(-3.5, resample=Image.BICUBIC, expand=True)
    big = Image.new("RGBA", (c.width + 80, c.height + 80), (0, 0, 0, 0))
    big.alpha_composite(c, (40, 40))
    cx, cy = W - big.width + 10, (H - big.height) // 2 + 6
    img.alpha_composite(drop_shadow(big, 18, 0.75), (cx + 10, cy + 16))
    img.alpha_composite(big, (cx, cy))

    logo = Image.open(LOGO).convert("RGBA").resize((210, 210), Image.LANCZOS)
    lx, ly = 70, 110
    img.alpha_composite(drop_shadow(logo, 10, 0.7), (lx + 4, ly + 8))
    img.alpha_composite(logo, (lx, ly))

    font = ImageFont.truetype("fonts/Fraunces.ttf", 96)
    font.set_variation_by_axes([144, 700, 0, 0])
    small = ImageFont.truetype("fonts/Fraunces.ttf", 27)
    small.set_variation_by_axes([24, 500, 0, 0])
    tx, ty = 300, 128
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).text((tx, ty), "Inventory", font=font, fill=255)
    ImageDraw.Draw(mask).text((tx, ty + 92), "Backups", font=font, fill=255)
    glow = Image.new("RGBA", (W, H), (80, 150, 255, 0))
    glow.putalpha(mask.filter(ImageFilter.GaussianBlur(20)).point(lambda a: int(a * 0.4)))
    layer.alpha_composite(glow)
    shadow = Image.new("RGBA", (W, H), (0, 4, 14, 0))
    shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(3)).point(lambda a: int(a * 0.9)))
    layer.alpha_composite(shadow, (4, 6))
    grad = np.zeros((H, W, 4), np.uint8)
    gy = np.clip((np.arange(H)[:, None] - ty - 10) / 200.0, 0, 1)
    top_c, bot_c = np.array([236, 244, 255]), np.array([120, 182, 255])
    grad[..., :3] = (top_c * (1 - gy[..., None]) + bot_c * gy[..., None]).astype(np.uint8)
    grad[..., 3] = np.array(mask)
    layer.alpha_composite(Image.fromarray(grad, "RGBA"))
    d = ImageDraw.Draw(layer)
    line_y = ty + 218
    d.line([(tx + 4, line_y), (tx + 560, line_y)], fill=(110, 170, 250, 220), width=2)
    d.text((tx + 4, line_y + 16), "EVERY CHANGE  ·  ONE-CLICK RESTORE  ·  ITEM SEARCH", font=small,
           fill=(214, 230, 255, 255))
    img.alpha_composite(layer)
    return img


if __name__ == "__main__":
    bg, shot, out = sys.argv[1:4]
    compose(bg, shot).convert("RGB").save(out, optimize=True)
    print(out)
