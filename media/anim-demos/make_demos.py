"""Capture-animation demos matching BoardView's palette (WOOD #E8C07A, GRID #3E2B15).
Scenario: Black just played (4,5) capturing White's 3 stones. 30 fps GIFs.
"""
from PIL import Image, ImageDraw
import math

N = 9
SIZE = 480
PAD = 40
CELL = (SIZE - 2 * PAD) / (N - 1)
WOOD = (232, 192, 122)
GRID = (62, 43, 21)
BLACK, BLACK_HI = (17, 17, 17), (58, 58, 58)
WHITE, WHITE_RIM = (253, 248, 236), (138, 112, 64)

W = [(4, 3), (5, 3), (4, 4)]  # captured
B = [(3, 3), (6, 3), (4, 2), (5, 2), (3, 4), (5, 4), (3, 5), (5, 5)]
LAST = (4, 5)  # the capturing stone
STARS = [(2, 2), (6, 2), (2, 6), (6, 6), (4, 4)]

def pt(x, y):
    return PAD + x * CELL, PAD + y * CELL

def base():
    img = Image.new("RGB", (SIZE, SIZE), WOOD)
    d = ImageDraw.Draw(img)
    for i in range(N):
        c = PAD + i * CELL
        d.line([PAD, c, SIZE - PAD, c], fill=GRID, width=2)
        d.line([c, PAD, c, SIZE - PAD], fill=GRID, width=2)
    for s in STARS:
        x, y = pt(*s)
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=GRID)
    return img, d

def stone(d, x, y, color, scale=1.0, alpha=255, dx=0.0, dy=0.0):
    cx, cy = pt(x, y)
    cx += dx * CELL
    cy += dy * CELL
    r = CELL * 0.44 * scale
    if r <= 0.5 or alpha <= 4:
        return
    # shadow
    sh = int(70 * (alpha / 255))
    d.ellipse([cx - r + 2, cy - r + 3, cx + r + 2, cy + r + 3], fill=(0, 0, 0, 0) if False else None, outline=None)
    # PIL has no per-shape alpha on RGB; fake fade by blending toward wood
    def blend(c):
        t = alpha / 255
        return tuple(int(WOOD[i] + (c[i] - WOOD[i]) * t) for i in range(3))
    d.ellipse([cx - r + 2, cy - r + 3, cx + r + 2, cy + r + 3], fill=blend((90, 70, 40)))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=blend(color))
    if color == BLACK:
        hr = r * 0.3
        d.ellipse([cx - r * 0.35 - hr, cy - r * 0.35 - hr, cx - r * 0.35 + hr, cy - r * 0.35 + hr], fill=blend(BLACK_HI))
    else:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=blend(WHITE_RIM), width=2)

def static_board():
    img, d = base()
    for x, y in B:
        stone(d, x, y, BLACK)
    stone(d, *LAST, BLACK)
    lx, ly = pt(*LAST)
    d.ellipse([lx - CELL * 0.53, ly - CELL * 0.53, lx + CELL * 0.53, ly + CELL * 0.53], outline=(200, 60, 40), width=5)
    return img

def ease_out(t):
    return 1 - (1 - t) ** 3

def ease_in(t):
    return t ** 3

def back_out(t):
    c = 1.70158
    return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2

FPS_MS = 33
HOLD = [static_frame for static_frame in []]  # placeholder

def frames_with_hold(anim_frames, pre=8, post=8):
    # 'Before': normal game state — doomed stones on the board, the
    # capturing stone not yet played.
    pres = []
    for _ in range(pre):
        img, d = base()
        for bx, by in B:
            stone(d, bx, by, BLACK)
        for x, y in W:
            stone(d, x, y, WHITE)
        pres.append(img)
    # 'After': capturing stone (with last-move marker) in place,
    # captured stones gone.
    posts = []
    for _ in range(post):
        img, d = base()
        draw_static(d)
        posts.append(img)
    return pres + anim_frames + posts

def draw_static(d):
    for bx, by in B:
        stone(d, bx, by, BLACK)
    stone(d, *LAST, BLACK)
    lx, ly = pt(*LAST)
    d.ellipse([lx - CELL * 0.53, ly - CELL * 0.53, lx + CELL * 0.53, ly + CELL * 0.53], outline=(200, 60, 40), width=5)

def new_frame():
    img, d = base()
    draw_static(d)
    return img, d

# A: POP (synchronized; quick swell, ease-OUT collapse so there is no
# lingering stop at the peak — fast off the top, gentle dissolve), ~200ms
def anim_pop():
    out = []
    swell, shrink = 2, 4
    total = swell + shrink
    for f in range(total):
        img, d = new_frame()
        if f < swell:
            s = 1 + 0.15 * ease_out(f / max(swell - 1, 1))
            for x, y in W:
                stone(d, x, y, WHITE, scale=s)
        else:
            k = (f - swell + 1) / shrink
            s = max(0.0, 1.15 * (1 - ease_out(k)))
            a = int(255 * (1 - k * k))
            for x, y in W:
                stone(d, x, y, WHITE, scale=s, alpha=a)
        out.append(img)
    return out

# B: BUBBLE FLOAT (rise + wobble + fade), stagger 50ms, ~350ms
def anim_float():
    out = []
    total = 11
    for f in range(total):
        img, d = new_frame()
        t = f / (total - 1)
        for i, (x, y) in enumerate(W):
            lt = min(max((t * total - i * 1.5) / 6.0, 0.0), 1.0)
            if lt <= 0:
                stone(d, x, y, WHITE)
            else:
                e = ease_out(min(lt, 1.0))
                stone(d, x, y, WHITE, scale=1 - 0.35 * e,
                      alpha=int(255 * (1 - e * e)),
                      dx=0.12 * math.sin(e * 6 + i * 2),
                      dy=-0.7 * e)
        out.append(img)
    return out

# C: QUICK POOF (expanding ring + fast shrink), ~220ms
def anim_poof():
    out = []
    total = 7
    cx = sum(p[0] for p in W) / len(W)
    cy = sum(p[1] for p in W) / len(W)
    px, py = pt(cx, cy)
    for f in range(total):
        img, d = new_frame()
        t = f / (total - 1)
        for i, (x, y) in enumerate(W):
            lt = min(max(t * 1.4 - i * 0.12, 0.0), 1.0)
            stone(d, x, y, WHITE, scale=max(0.0, 1 - ease_in(lt)), alpha=int(255 * (1 - lt)))
        # ring
        rr = CELL * (0.3 + 1.5 * ease_out(t))
        ra = int(200 * (1 - t))
        if ra > 5:
            t2 = ra / 255
            col = tuple(int(WOOD[i] + ((235, 245, 255)[i] - WOOD[i]) * t2) for i in range(3))
            d.ellipse([px - rr, py - rr, px + rr, py + rr], outline=col, width=4)
        out.append(img)
    return out

# D: SCATTER FIZZ (drift outward + fizz bubbles), ~300ms
def anim_fizz():
    out = []
    total = 9
    dirs = [(-0.5, -0.6), (0.55, -0.4), (-0.05, -0.75)]
    for f in range(total):
        img, d = new_frame()
        t = f / (total - 1)
        for i, (x, y) in enumerate(W):
            lt = min(max(t * 1.3 - i * 0.1, 0.0), 1.0)
            e = ease_out(min(lt, 1.0))
            dx, dy = dirs[i]
            stone(d, x, y, WHITE, scale=max(0.0, 1 - 0.9 * e), alpha=int(255 * (1 - e)),
                  dx=dx * e * 0.8, dy=dy * e * 0.8)
            # fizz bubbles
            for b in range(2):
                bt = (t * 2 - b * 0.3 - i * 0.1)
                if 0 < bt < 1:
                    bx = pt(x, y)[0] + dx * 30 * bt + (b * 13 - 6)
                    by = pt(x, y)[1] - 34 * bt
                    br = 5 * (1 - bt) + 1
                    d.ellipse([bx - br, by - br, bx + br, by + br], outline=(255, 255, 255), width=2)
        out.append(img)
    return out

def save_gif(path, frames, n_pre, n_anim):
    # Holds read as pauses only if every frame is byte-distinct (PIL merges
    # identical frames); one invisible corner-pixel jitter defeats the merge.
    outs, durations = [], []
    for i, fr in enumerate(frames):
        fr = fr.copy()
        fr.putpixel((0, 0), (WOOD[0] - (i % 2), WOOD[1], WOOD[2]))
        outs.append(fr)
        durations.append(120 if i < n_pre or i >= n_pre + n_anim else FPS_MS)
    outs[0].save(path, save_all=True, append_images=outs[1:], duration=durations, loop=0)

if __name__ == "__main__":
    import os
    os.makedirs("/home/acbraith/projects/go-trainer/media/anim-demos", exist_ok=True)
    from PIL import Image as _Img
    for name, fn in [("a_pop", anim_pop), ("b_float", anim_float), ("c_poof", anim_poof), ("d_fizz", anim_fizz)]:
        anim = fn()
        frames = frames_with_hold(anim)
        n_pre = (len(frames) - len(anim)) // 2
        path = f"/home/acbraith/projects/go-trainer/media/anim-demos/{name}.gif"
        save_gif(path, frames, n_pre, len(anim))
        print(name, len(frames), "frames ->", path, "(file:", _Img.open(path).n_frames, ")")
