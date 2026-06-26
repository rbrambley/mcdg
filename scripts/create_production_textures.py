from PIL import Image, ImageDraw
import os

out_dir = "src/main/resources/assets/mcdg/textures/item"
os.makedirs(out_dir, exist_ok=True)


def make_canvas(size=32):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    return img, draw


def draw_disc_bag():
    img, d = make_canvas()
    # Main bag body (standing upright duffel)
    d.rounded_rectangle([6, 10, 26, 30], radius=5, fill=(145, 95, 50), outline=(100, 65, 35), width=2)
    # Lighter highlight panel on left
    d.rounded_rectangle([7, 11, 14, 28], radius=3, fill=(170, 125, 70), outline=None)
    # Front zip pocket
    d.rounded_rectangle([10, 19, 22, 27], radius=3, fill=(155, 110, 60), outline=(115, 75, 40), width=1)
    # Zipper line
    d.line([(11, 21), (21, 21)], fill=(90, 60, 30), width=1)
    # Bag opening at top
    d.rounded_rectangle([7, 7, 25, 12], radius=4, fill=(75, 48, 25), outline=(55, 34, 18), width=1)
    # Disc rim peeking out (cyan with highlight)
    d.arc([8, 4, 24, 11], start=0, end=180, fill=(50, 140, 190), width=3)
    d.arc([10, 5, 22, 10], start=0, end=180, fill=(85, 175, 225), width=1)
    # Shoulder strap (diagonal)
    d.line([(3, 3), (13, 9)], fill=(75, 45, 25), width=4)
    d.line([(3, 3), (13, 9)], fill=(110, 70, 40), width=2)
    # Top handle
    d.arc([12, 3, 20, 8], start=0, end=180, fill=(90, 55, 30), width=2)
    # Bottom studs
    d.ellipse([9, 28, 11, 30], fill=(60, 40, 20))
    d.ellipse([21, 28, 23, 30], fill=(60, 40, 20))
    return img


def draw_disc_glove():
    img, d = make_canvas()
    # Main glove back (dark leather)
    d.rounded_rectangle([9, 6, 25, 28], radius=4, fill=(55, 55, 55), outline=(35, 35, 35), width=2)
    # Thumb (to the left)
    d.rounded_rectangle([3, 14, 10, 24], radius=3, fill=(60, 60, 60), outline=(40, 40, 40), width=1)
    # Finger knuckle lines (subtle)
    d.line([(11, 6), (11, 13)], fill=(75, 75, 75), width=1)
    d.line([(15, 6), (15, 13)], fill=(75, 75, 75), width=1)
    d.line([(19, 6), (19, 13)], fill=(75, 75, 75), width=1)
    d.line([(23, 6), (23, 13)], fill=(75, 75, 75), width=1)
    # Wrist strap
    d.rectangle([9, 24, 25, 28], fill=(35, 35, 35), outline=(25, 25, 25), width=1)
    d.rectangle([14, 25, 20, 27], fill=(90, 90, 90))  # buckle
    # Highlight on back of hand
    d.line([(12, 8), (12, 22)], fill=(90, 90, 90), width=1)
    # Stitching / seam
    d.line([(16, 16), (22, 22)], fill=(100, 100, 100), width=1)
    return img


def draw_disc_towel():
    img, d = make_canvas()
    # Main folded towel (white/light blue)
    d.rounded_rectangle([6, 7, 26, 28], radius=2, fill=(235, 245, 255), outline=(190, 205, 225), width=2)
    # Fold shadow line across middle
    d.line([(7, 18), (25, 18)], fill=(205, 215, 235), width=1)
    # Vertical fabric texture lines
    for x in range(9, 24, 3):
        d.line([(x, 8), (x, 27)], fill=(220, 230, 245), width=1)
    # Metal grommet/clip at top-left corner
    d.ellipse([5, 5, 10, 10], fill=(140, 140, 140), outline=(90, 90, 90), width=1)
    d.ellipse([6, 6, 8, 8], fill=(60, 60, 60))
    # Clip ring
    d.arc([5, 1, 9, 6], start=0, end=180, fill=(100, 100, 100), width=2)
    # Slight side shadow
    d.line([(25, 8), (25, 27)], fill=(200, 210, 230), width=1)
    # Bottom fringe hint
    d.line([(8, 28), (8, 30)], fill=(190, 205, 225), width=1)
    d.line([(12, 28), (12, 30)], fill=(190, 205, 225), width=1)
    d.line([(16, 28), (16, 30)], fill=(190, 205, 225), width=1)
    d.line([(20, 28), (20, 30)], fill=(190, 205, 225), width=1)
    d.line([(24, 28), (24, 30)], fill=(190, 205, 225), width=1)
    return img


def draw_range_finder():
    img, d = make_canvas()
    # Main body (rounded compact rectangle)
    d.rounded_rectangle([6, 8, 26, 26], radius=4, fill=(55, 55, 60), outline=(35, 35, 40), width=2)
    # Grip texture on sides
    for y in range(10, 26, 3):
        d.line([(6, y), (7, y)], fill=(40, 40, 45), width=1)
        d.line([(25, y), (26, y)], fill=(40, 40, 45), width=1)
    # Viewfinder housing on top
    d.rounded_rectangle([11, 5, 21, 9], radius=2, fill=(70, 70, 75), outline=(45, 45, 50), width=1)
    # Lens (left side)
    d.ellipse([7, 10, 15, 20], fill=(35, 35, 35), outline=(20, 20, 20), width=1)
    d.ellipse([9, 12, 13, 18], fill=(90, 220, 110), outline=(55, 180, 75), width=1)
    d.ellipse([10, 13, 12, 17], fill=(120, 255, 140))
    # LCD screen
    d.rectangle([17, 11, 24, 16], fill=(20, 30, 20), outline=(100, 100, 100), width=1)
    # Screen digits / symbol
    d.line([(18, 13), (21, 13)], fill=(100, 255, 120), width=1)
    d.line([(18, 15), (20, 15)], fill=(100, 255, 120), width=1)
    # Control buttons
    d.ellipse([17, 19, 19, 21], fill=(210, 70, 70))
    d.ellipse([21, 19, 23, 21], fill=(60, 120, 220))
    # Mode dial
    d.ellipse([19, 22, 22, 25], fill=(120, 120, 120), outline=(80, 80, 80), width=1)
    # Highlight along top edge
    d.line([(8, 9), (24, 9)], fill=(90, 90, 95), width=1)
    return img


items = {
    "disc_bag": draw_disc_bag(),
    "disc_glove": draw_disc_glove(),
    "disc_towel": draw_disc_towel(),
    "range_finder": draw_range_finder(),
}

for name, img in items.items():
    path = os.path.join(out_dir, f"{name}.png")
    img.save(path)
    print(f"Created {path} ({img.size[0]}x{img.size[1]})")
