# Charcoal marks for Crafting Tweaks' grid tweaks on the crafting table: 6 marks of 16x16 in one 96x16 sheet.
import math, sys
from PIL import Image

STRONG = (46, 34, 26, 235)
SOFT = (58, 44, 34, 150)
OCHRE = (150, 52, 34, 235)

def blank():
    return [['.'] * 16 for _ in range(16)]

def rotate_cw():
    g = blank()
    cx, cy, r = 8.5, 8.0, 5.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            a = math.degrees(math.atan2(-(y - cy), x - cx)) % 360  # 0 = right, 90 = up
            if abs(d - r) < 0.55 and not (108 <= a <= 168):
                g[y][x] = '#'
    # arrowhead at the end on the left, pointing up: clockwise, the line goes on upwards there
    for x, y in [(3, 4), (2, 5), (3, 5), (4, 5), (1, 6), (2, 6), (3, 6), (4, 6), (5, 6)]:
        g[y][x] = '#'
    return g

def mirror(g):
    return [row[::-1] for row in g]

def balance():
    g = blank()
    for cy in (3, 7, 11):
        for cx in (3, 7, 11):
            for dy in (0, 1):
                for dx in (0, 1):
                    g[cy + dy][cx + dx] = '#'
    return g

def spread():
    g = blank()
    # a dotted frame round one middle dot: things laid out to the edges
    for i in range(2, 14, 2):
        g[2][i] = g[13][i] = '#'
        g[i][2] = g[i][13] = '#'
    g[2][13] = g[13][2] = '#'
    for dy in (0, 1):
        for dx in (0, 1):
            g[7 + dy][7 + dx] = '#'
    return g

def clear():
    g = blank()
    for y in range(2, 9):
        for x in (7, 8):
            g[y][x] = '#'
    for i, y in enumerate(range(8, 13)):
        for x in range(3 + i, 13 - i):
            g[y][x] = '#'
    return g

def force_clear():
    g = clear()
    # shift the arrow left a little and put an ochre mark beside it
    g = [row[1:] + ['.'] for row in g]
    for y in range(2, 9):
        g[y][13] = 'o'
    g[11][13] = 'o'
    return g

MARKS = [rotate_cw(), mirror(rotate_cw()), balance(), spread(), clear(), force_clear()]

def roughen(g, seed):
    # charcoal: a few pixels of every stroke lighter, never the ends of thin lines
    import random
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            if g[y][x] == '#' and rnd.random() < 0.1:
                g[y][x] = '+'
    return g

sheet = Image.new('RGBA', (16 * len(MARKS), 16), (0, 0, 0, 0))
for i, mark in enumerate(MARKS):
    mark = roughen([row[:] for row in mark], 7 + i)
    for y in range(16):
        for x in range(16):
            c = {'#': STRONG, '+': SOFT, 'o': OCHRE}.get(mark[y][x])
            if c:
                sheet.putpixel((16 * i + x, y), c)
sheet.save(sys.argv[1])
preview = Image.new('RGBA', sheet.size, (196, 160, 110, 255))
preview.alpha_composite(sheet)
preview.resize((sheet.width * 12, sheet.height * 12), Image.NEAREST).save(sys.argv[2])
