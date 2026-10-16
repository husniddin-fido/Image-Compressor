#!/usr/bin/env python3
import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = "store_assets/screenshots"
os.makedirs(OUTPUT_DIR, exist_ok=True)

FONT_HEAVY = "/Library/Fonts/SF-Pro-Display-Heavy.otf"
FONT_BOLD = "/Library/Fonts/SF-Pro-Display-Bold.otf"
FONT_SEMIBOLD = "/Library/Fonts/SF-Pro-Display-Semibold.otf"
FONT_MEDIUM = "/Library/Fonts/SF-Pro-Text-Medium.otf"

POSTER_W = 1080
POSTER_H = 2400

CONFIGS = [
    {
        "id": "1_home",
        "raw": "/tmp/real_screens/01_home.png",
        "badge": "TEZKOR VA SIFATLI",
        "badge_color": (0, 229, 255),
        "title": "SIFATNI YO'QOTMASDAN\nHAJMNI 90% SIQING",
        "subtitle": "Katta fotosuratlarni sifatini saqlagan holda ixchamlashtiring",
        "bg_top": (10, 16, 42),
        "bg_bottom": (3, 6, 18),
        "glow_color": (0, 160, 255, 90),
        "rim_color": (0, 229, 255),
    },
    {
        "id": "2_configure",
        "raw": "/tmp/real_screens/02_configure.png",
        "badge": "ANIQ NAZORAT & TANLOV",
        "badge_color": (213, 0, 249),
        "title": "ANIQ HAJM VA FORMAT\nTO'LIQ NAZORATDA",
        "subtitle": "KB, MB, Sifat % yoki O'lcham (px) bo'yicha moslang",
        "bg_top": (32, 10, 52),
        "bg_bottom": (10, 4, 22),
        "glow_color": (190, 80, 255, 95),
        "rim_color": (213, 0, 249),
    },
    {
        "id": "3_results",
        "raw": "/tmp/real_screens/03_results.png",
        "badge": "BATCH REJIM",
        "badge_color": (0, 230, 118),
        "title": "BIR VAQTDA YUZLAB\nRASMNI TEZ SIQING",
        "subtitle": "Tejalgan joyni real vaqtda ko'ring va ulashing",
        "bg_top": (6, 38, 30),
        "bg_bottom": (2, 14, 12),
        "glow_color": (0, 230, 118, 85),
        "rim_color": (0, 230, 118),
    },
    {
        "id": "4_comparison",
        "raw": "/tmp/real_screens/04_comparison.png",
        "badge": "BEFORE & AFTER SLIDER",
        "badge_color": (0, 180, 255),
        "title": "JONLI TAQQOSLASH\nSIFATNI TEKSHIRING",
        "subtitle": "Original va siqilgan holatni barmoq bilan tekshiring",
        "bg_top": (10, 26, 62),
        "bg_bottom": (4, 9, 26),
        "glow_color": (0, 180, 255, 90),
        "rim_color": (0, 180, 255),
    },
    {
        "id": "5_settings",
        "raw": "/tmp/real_screens/05_settings.png",
        "badge": "100% MAXFIY VA OFFLAYN",
        "badge_color": (255, 171, 0),
        "title": "INTERNETSIZ ISHLAYDI\nMA'LUMOTLAR XAVFSIZ",
        "subtitle": "Hech qanday serverga yuklanmaydi, to'liq qurilmada",
        "bg_top": (38, 20, 8),
        "bg_bottom": (14, 7, 3),
        "glow_color": (255, 171, 0, 85),
        "rim_color": (255, 171, 0),
    },
]

def create_gradient_background(w, h, top_rgb, bottom_rgb, glow_rgb_a):
    base = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    draw = ImageDraw.Draw(base)
    for y in range(h):
        t = y / float(h - 1)
        r = int(top_rgb[0] * (1 - t) + bottom_rgb[0] * t)
        g = int(top_rgb[1] * (1 - t) + bottom_rgb[1] * t)
        b = int(top_rgb[2] * (1 - t) + bottom_rgb[2] * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Add atmospheric halo glow behind device
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    cx, cy = w // 2, int(h * 0.44)
    radius = int(w * 0.72)
    r_c, g_c, b_c, a_max = glow_rgb_a
    for rad in range(radius, 0, -18):
        factor = (1.0 - (rad / radius)) ** 1.7
        alpha = int(a_max * factor)
        glow_draw.ellipse(
            [cx - rad, cy - rad, cx + rad, cy + rad],
            fill=(r_c, g_c, b_c, alpha)
        )
    glow = glow.filter(ImageFilter.GaussianBlur(45))
    base.alpha_composite(glow)
    return base

def rounded_rectangle_mask(size, radius):
    w, h = size
    mask = Image.new("L", (w * 2, h * 2), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([0, 0, w * 2, h * 2], radius=radius * 2, fill=255)
    return mask.resize((w, h), Image.Resampling.LANCZOS)

def create_phone_frame(screen_img, rim_color, target_w=850):
    sw, sh = screen_img.size
    target_h = int(target_w * (sh / sw))

    bezel = 14
    outer_w = target_w + bezel * 2
    outer_h = target_h + bezel * 2
    outer_r = 54
    inner_r = 44

    device = Image.new("RGBA", (outer_w, outer_h), (0, 0, 0, 0))
    dev_draw = ImageDraw.Draw(device)

    # 1. Outer rim accent (metallic gradient / glowing color border)
    dev_draw.rounded_rectangle(
        [0, 0, outer_w - 1, outer_h - 1],
        radius=outer_r,
        fill=(18, 20, 26, 255),
        outline=rim_color,
        width=3
    )

    # 2. Resized screenshot
    screen_resized = screen_img.resize((target_w, target_h), Image.Resampling.LANCZOS)
    screen_mask = rounded_rectangle_mask((target_w, target_h), inner_r)
    device.paste(screen_resized, (bezel, bezel), screen_mask)

    # 3. Multi-layer drop shadow for elevation
    shadow_margin = 60
    full_w = outer_w + shadow_margin * 2
    full_h = outer_h + shadow_margin * 2
    shadow_img = Image.new("RGBA", (full_w, full_h), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow_img)

    s_draw.rounded_rectangle(
        [shadow_margin, shadow_margin + 20, shadow_margin + outer_w, shadow_margin + outer_h + 20],
        radius=outer_r,
        fill=(0, 0, 0, 180)
    )
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(36))

    glow_shadow = Image.new("RGBA", (full_w, full_h), (0, 0, 0, 0))
    g_draw = ImageDraw.Draw(glow_shadow)
    g_draw.rounded_rectangle(
        [shadow_margin - 6, shadow_margin - 6, shadow_margin + outer_w + 6, shadow_margin + outer_h + 6],
        radius=outer_r + 6,
        fill=(rim_color[0], rim_color[1], rim_color[2], 100)
    )
    glow_shadow = glow_shadow.filter(ImageFilter.GaussianBlur(44))

    final_comp = Image.new("RGBA", (full_w, full_h), (0, 0, 0, 0))
    final_comp.alpha_composite(glow_shadow)
    final_comp.alpha_composite(shadow_img)
    final_comp.paste(device, (shadow_margin, shadow_margin), device)

    return final_comp, shadow_margin

def render_poster(cfg):
    raw_path = cfg["raw"]
    if not os.path.exists(raw_path):
        print(f"File not found: {raw_path}")
        return

    raw_img = Image.open(raw_path).convert("RGBA")
    poster = create_gradient_background(
        POSTER_W, POSTER_H,
        cfg["bg_top"], cfg["bg_bottom"],
        cfg["glow_color"]
    )

    # Fonts
    font_badge = ImageFont.truetype(FONT_BOLD, 26)
    font_title = ImageFont.truetype(FONT_HEAVY, 68)
    font_subtitle = ImageFont.truetype(FONT_MEDIUM, 34)

    # 1. Badge Pill with clean alpha overlay
    badge_text = cfg["badge"]
    bbox_b = font_badge.getbbox(badge_text)
    bw = bbox_b[2] - bbox_b[0]
    bh = bbox_b[3] - bbox_b[1]
    pill_pad_x = 34
    pill_pad_y = 12
    pill_w = bw + pill_pad_x * 2
    pill_h = bh + pill_pad_y * 2
    pill_x = (POSTER_W - pill_w) // 2
    pill_y = 105

    pill_overlay = Image.new("RGBA", (POSTER_W, POSTER_H), (0, 0, 0, 0))
    p_draw = ImageDraw.Draw(pill_overlay)
    bc = cfg["badge_color"]
    p_draw.rounded_rectangle(
        [pill_x, pill_y, pill_x + pill_w, pill_y + pill_h],
        radius=pill_h // 2,
        fill=(bc[0], bc[1], bc[2], 45),
        outline=(bc[0], bc[1], bc[2], 220),
        width=2
    )
    p_draw.text(
        (pill_x + pill_pad_x, pill_y + pill_pad_y - 2),
        badge_text,
        font=font_badge,
        fill=(255, 255, 255, 255)
    )
    poster.alpha_composite(pill_overlay)

    # 2. Main Title (centered, 2 lines)
    draw = ImageDraw.Draw(poster)
    title_text = cfg["title"]
    title_y = pill_y + pill_h + 38
    for line in title_text.split("\n"):
        bbox = font_title.getbbox(line)
        lw = bbox[2] - bbox[0]
        lx = (POSTER_W - lw) // 2
        draw.text((lx + 2, title_y + 3), line, font=font_title, fill=(0, 0, 0, 180))
        draw.text((lx, title_y), line, font=font_title, fill=(255, 255, 255, 255))
        title_y += (bbox[3] - bbox[1]) + 18

    # 3. Subtitle
    sub_text = cfg["subtitle"]
    bbox_s = font_subtitle.getbbox(sub_text)
    sw = bbox_s[2] - bbox_s[0]
    sx = (POSTER_W - sw) // 2
    title_y += 12
    draw.text((sx + 1, title_y + 2), sub_text, font=font_subtitle, fill=(0, 0, 0, 160))
    draw.text((sx, title_y), sub_text, font=font_subtitle, fill=(210, 222, 240, 235))

    # 4. Device placement
    phone_comp, margin = create_phone_frame(raw_img, cfg["rim_color"], target_w=850)
    device_y = 660 - margin
    device_x = (POSTER_W - phone_comp.size[0]) // 2
    poster.alpha_composite(phone_comp, (device_x, device_y))

    # Save
    out_file = os.path.join(OUTPUT_DIR, f"phone_{cfg['id']}.png")
    poster.convert("RGB").save(out_file, "PNG", quality=98)
    print(f"Saved: {out_file}")

def render_tablet_posters():
    tab7_w, tab7_h = 1200, 1920
    tab10_w, tab10_h = 1600, 2560

    for w, h, name in [(tab7_w, tab7_h, "tablet_7inch.png"), (tab10_w, tab10_h, "tablet_10inch.png")]:
        tab_poster = create_gradient_background(
            w, h,
            (10, 18, 48), (4, 8, 22),
            (0, 180, 255, 80)
        )
        draw = ImageDraw.Draw(tab_poster)
        font_badge = ImageFont.truetype(FONT_BOLD, int(h * 0.015))
        font_t = ImageFont.truetype(FONT_HEAVY, int(h * 0.028))
        font_s = ImageFont.truetype(FONT_MEDIUM, int(h * 0.016))

        # Badge
        badge_text = "HAMMA QURILMALARDA QULAY"
        bbox_b = font_badge.getbbox(badge_text)
        bw = bbox_b[2] - bbox_b[0]
        bh = bbox_b[3] - bbox_b[1]
        pill_pad_x = 28
        pill_pad_y = 10
        pill_w = bw + pill_pad_x * 2
        pill_h = bh + pill_pad_y * 2
        pill_x = (w - pill_w) // 2
        pill_y = int(h * 0.04)

        pill_overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        p_draw = ImageDraw.Draw(pill_overlay)
        p_draw.rounded_rectangle(
            [pill_x, pill_y, pill_x + pill_w, pill_y + pill_h],
            radius=pill_h // 2,
            fill=(0, 180, 255, 45),
            outline=(0, 180, 255, 220),
            width=2
        )
        p_draw.text((pill_x + pill_pad_x, pill_y + pill_pad_y - 2), badge_text, font=font_badge, fill=(255, 255, 255))
        tab_poster.alpha_composite(pill_overlay)

        # Title
        t_lines = ["PLANSHET VA SMARTFONLARDA", "SIFATNI SAQLAB SIQING"]
        ty = pill_y + pill_h + 20
        draw = ImageDraw.Draw(tab_poster)
        for line in t_lines:
            bbox = font_t.getbbox(line)
            lw = bbox[2] - bbox[0]
            lx = (w - lw) // 2
            draw.text((lx + 2, ty + 2), line, font=font_t, fill=(0, 0, 0, 180))
            draw.text((lx, ty), line, font=font_t, fill=(255, 255, 255))
            ty += (bbox[3] - bbox[1]) + 12

        # Subtitle
        s_text = "Jonli Before & After • Batch compression • 100% offlayn"
        bbox_s = font_s.getbbox(s_text)
        sw = bbox_s[2] - bbox_s[0]
        sx = (w - sw) // 2
        ty += 8
        draw.text((sx + 1, ty + 1), s_text, font=font_s, fill=(0, 0, 0, 160))
        draw.text((sx, ty), s_text, font=font_s, fill=(185, 215, 245))

        # Display two real device frames side by side!
        raw_comp = Image.open("/tmp/real_screens/04_comparison.png").convert("RGBA")
        raw_res = Image.open("/tmp/real_screens/03_results.png").convert("RGBA")

        target_dev_w = int(w * 0.44)
        f1, m1 = create_phone_frame(raw_comp, (0, 180, 255), target_w=target_dev_w)
        f2, m2 = create_phone_frame(raw_res, (0, 230, 118), target_w=target_dev_w)

        dev_y = int(h * 0.22) - m1
        dev_x1 = int(w * 0.05) - m1
        dev_x2 = int(w * 0.51) - m2

        tab_poster.alpha_composite(f1, (dev_x1, dev_y))
        tab_poster.alpha_composite(f2, (dev_x2, dev_y))

        out_path = os.path.join(OUTPUT_DIR, name)
        tab_poster.convert("RGB").save(out_path, "PNG", quality=98)
        print(f"Saved: {out_path}")

def main():
    for cfg in CONFIGS:
        render_poster(cfg)
    render_tablet_posters()

if __name__ == "__main__":
    main()
