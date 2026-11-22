package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawings of the enchanting book's pages, each one of its own: no two figures in the book are the same. Each is drawn pixel
 * by pixel as the hand-drawn magic circle of the first spread is (1 px lines and midpoint circles, white, tinted with the rune
 * ink), at the size it takes on the page (about a pixel a unit, as the paper's texels), from its own seed. Families of three
 * shapes: square (magic circles, seals, an eye in a figure, astrolabes, orbits, crystals, a table of runes, a spiral, a
 * constellation), tall (a herb with its roots, a flask, a sword, a candle) and wide (the moon's phases, a chain of seals, a
 * constellation, a wave with its nodes). A few textures are kept and drawn anew for the figures seen now, so the game keeps a
 * fixed number of them however long the book is read.
 */
final class PageDrawings {
    /** The textures' size: a figure takes the top left part of one, as large as it is drawn. */
    static final int SIZE = 64;
    /** Pixels a page unit: the paper's texel is about a unit. */
    static final float PIXELS_A_UNIT = 1.1F;
    /** Textures kept at once: more than the figures that can show at a time (two resting pages, turning and riffling ones). */
    private static final int SLOTS = 32;
    private static final int WHITE = 0xFFFFFFFF;

    /** A figure's shape, for the families that suit it. */
    enum Shape {
        SQUARE(9), TALL(4), WIDE(4);

        final int families;

        Shape(int families) {
            this.families = families;
        }
    }

    /** A figure drawn: its texture and the part of it it takes. */
    record Drawn(Identifier texture, float u1, float v1) {
    }

    private static final Identifier[] IDS = new Identifier[SLOTS];
    private static final DynamicTexture[] TEXTURES = new DynamicTexture[SLOTS];
    private static final long[] KEYS = new long[SLOTS];
    private static final long[] USED = new long[SLOTS];
    private static long uses;

    static {
        for (int i = 0; i < SLOTS; i++) {
            IDS[i] = HandsOnVanilla.id("dynamic/enchanting_drawing_" + i);
            KEYS[i] = Long.MIN_VALUE;
        }
    }

    private PageDrawings() {
    }

    /** The pixels a figure {@code units} wide takes, at most a whole texture. */
    static int pixels(float units) {
        return Mth.clamp(Math.round(units * PIXELS_A_UNIT), 8, SIZE);
    }

    /**
     * Figure {@code key}'s drawing ({@code shape}, {@code wide} x {@code high} pixels, of the family its seed picks), drawn now if
     * it is not kept. Call it while rendering.
     */
    static Drawn of(long key, Shape shape, int wide, int high) {
        uses++;
        int oldest = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (KEYS[i] == key && TEXTURES[i] != null) {
                USED[i] = uses;
                return drawn(i, wide, high);
            }
            if (USED[i] < USED[oldest]) {
                oldest = i;
            }
        }
        NativeImage image = draw(key, shape, wide, high);
        if (TEXTURES[oldest] == null) {
            TEXTURES[oldest] = new DynamicTexture(IDS[oldest]::toString, image);
            Minecraft.getInstance().getTextureManager().register(IDS[oldest], TEXTURES[oldest]);
        } else {
            TEXTURES[oldest].setPixels(image);
            TEXTURES[oldest].upload();
        }
        KEYS[oldest] = key;
        USED[oldest] = uses;
        return drawn(oldest, wide, high);
    }

    private static Drawn drawn(int slot, int wide, int high) {
        return new Drawn(IDS[slot], (float) wide / SIZE, (float) high / SIZE);
    }

    /** Figure {@code key}'s drawing: white lines on a clear sheet, in its top left {@code wide} x {@code high} pixels. */
    static NativeImage draw(long key, Shape shape, int wide, int high) {
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        image.fillRect(0, 0, SIZE, SIZE, 0);
        RandomSource random = RandomSource.create(MagicInk.mix(key * 0x9E3779B97F4A7C15L + 0xD7A3L));
        Sheet s = new Sheet(image, wide, high);
        int family = (int) Math.floorMod(MagicInk.mix(key * 0x6A09E667L + 11L), shape.families);
        switch (shape) {
            case SQUARE -> {
                switch (family) {
                    case 0 -> circle(s, random);
                    case 1 -> seal(s, random);
                    case 2 -> eye(s, random);
                    case 3 -> astrolabe(s, random);
                    case 4 -> orbits(s, random);
                    case 5 -> crystals(s, random);
                    case 6 -> runeTable(s, random);
                    case 7 -> spiral(s, random);
                    default -> stars(s, random);
                }
            }
            case TALL -> {
                switch (family) {
                    case 0 -> herb(s, random);
                    case 1 -> flask(s, random);
                    case 2 -> sword(s, random);
                    default -> candle(s, random);
                }
            }
            default -> {
                switch (family) {
                    case 0 -> moons(s, random);
                    case 1 -> chain(s, random);
                    case 2 -> stars(s, random);
                    default -> wave(s, random);
                }
            }
        }
        return image;
    }

    // Square.

    /** A magic circle: one or two rings, a star of five to nine points in them, nodes, ticks or dots between the rings, a heart. */
    private static void circle(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int cy = s.h / 2;
        int outer = Math.min(s.w, s.h) / 2 - 2 - random.nextInt(2);
        boolean twin = random.nextFloat() < 0.75F;
        int inner = twin ? outer - Math.max(3, outer / 8) - random.nextInt(2) : outer;
        s.circle(cx, cy, outer);
        if (twin) {
            s.circle(cx, cy, inner);
            int marks = new int[] {8, 12, 16, 24}[random.nextInt(4)];
            boolean dots = random.nextBoolean();
            for (int i = 0; i < marks; i++) {
                double angle = Mth.TWO_PI * i / marks;
                double r = (outer + inner) / 2.0;
                if (dots) {
                    s.disc(cx + round(Math.cos(angle) * r), cy + round(Math.sin(angle) * r), 1);
                } else {
                    s.ray(cx, cy, angle, inner + 1, outer - 1);
                }
            }
        }
        int points = 5 + random.nextInt(5);
        int step = points >= 7 && random.nextBoolean() ? 3 : 2;
        double turn = -Math.PI / 2.0 + (random.nextBoolean() ? 0.0 : Math.PI / points);
        int[][] tips = polygon(cx, cy, points, inner, turn);
        for (int i = 0; i < points; i++) {
            s.line(tips[i][0], tips[i][1], tips[(i + step) % points][0], tips[(i + step) % points][1]);
        }
        if (random.nextBoolean()) {
            for (int[] tip : tips) {
                s.disc(tip[0], tip[1], 1);
            }
        }
        int heart = Math.max(3, round(inner * (0.2 + 0.25 * random.nextDouble())));
        s.circle(cx, cy, heart);
        if (random.nextBoolean() && heart > 6) {
            s.circle(cx, cy, heart - 3);
        }
        middle(s, cx, cy, random);
    }

    /** A seal: a ring with a square or two in it (an eight-pointed star when they cross), nodes on the ring, a heart. */
    private static void seal(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int cy = s.h / 2;
        int r = Math.min(s.w, s.h) / 2 - 2;
        s.circle(cx, cy, r);
        int inner = r - Math.max(3, r / 6);
        if (random.nextBoolean()) {
            s.circle(cx, cy, inner);
        }
        boolean both = random.nextBoolean();
        double turn = random.nextBoolean() ? 0.0 : Math.PI / 4.0;
        drawPolygon(s, polygon(cx, cy, 4, inner, turn));
        if (both) {
            drawPolygon(s, polygon(cx, cy, 4, inner, turn + Math.PI / 4.0));
        }
        int nodes = both ? 8 : 4;
        for (int[] tip : polygon(cx, cy, nodes, r, turn)) {
            s.disc(tip[0], tip[1], random.nextBoolean() ? 1 : 2);
        }
        int heart = Math.max(3, round(inner * (0.25 + 0.2 * random.nextDouble())));
        s.circle(cx, cy, heart);
        if (random.nextBoolean()) {
            for (int i = 0; i < 4; i++) {
                s.ray(cx, cy, turn + Math.PI / 4.0 + Math.PI / 2.0 * i, heart + 1, round(heart + (inner - heart) * 0.5));
            }
        }
        middle(s, cx, cy, random);
    }

    /** An eye in a figure (a triangle, a diamond or a circle), with rays round the figure. */
    private static void eye(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int r = Math.min(s.w, s.h) / 2 - 3;
        int cy = s.h / 2;
        int figure = random.nextInt(3);
        int eyeY = cy;
        if (figure == 0) {
            int top = cy - r + 1;
            int base = cy + round(r * 0.62);
            int half = round(r * 0.9);
            s.line(cx, top, cx + half, base);
            s.line(cx + half, base, cx - half, base);
            s.line(cx - half, base, cx, top);
            eyeY = base - round(r * 0.42);
            int side = 2 + random.nextInt(3);
            for (int i = 0; i < side; i++) {
                double t = 0.2 + 0.6 * i / Math.max(1, side - 1);
                int ly = top + round((base - top) * t);
                int lx = cx - round(half * t);
                int rx = cx + round(half * t);
                s.line(lx - 2, ly - 1, lx - 4, ly - 2);
                s.line(rx + 2, ly - 1, rx + 4, ly - 2);
            }
            s.line(cx, top - 3, cx, top - 2);
        } else if (figure == 1) {
            drawPolygon(s, polygon(cx, cy, 4, r, -Math.PI / 2.0));
            int rays = 4 + random.nextInt(5);
            for (int i = 0; i < rays; i++) {
                s.ray(cx, cy, Mth.TWO_PI * i / rays + Math.PI / rays, round(r * 0.78), round(r * 0.78) + 3);
            }
        } else {
            s.circle(cx, cy, r);
            if (random.nextBoolean()) {
                s.circle(cx, cy, r - 3);
            }
        }
        int wide = round(r * (0.48 + 0.1 * random.nextDouble()));
        int high = Math.max(3, round(r * (0.2 + 0.06 * random.nextDouble())));
        int[] up = null;
        int[] down = null;
        for (int i = 0; i <= 12; i++) {
            double t = i / 12.0;
            int px = cx - wide + round(2 * wide * t);
            int u = eyeY - round(Math.sin(Math.PI * t) * high);
            int d = eyeY + round(Math.sin(Math.PI * t) * high);
            if (up != null) {
                s.line(up[0], up[1], px, u);
                s.line(down[0], down[1], px, d);
            }
            up = new int[] {px, u};
            down = new int[] {px, d};
        }
        int iris = Math.max(2, high - 1);
        s.circle(cx, eyeY, iris);
        s.disc(cx, eyeY, Math.max(1, iris / 2));
    }

    /** An astrolabe: two rings, ticks round the rim, spokes, a star in the middle and a pointer. */
    private static void astrolabe(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int cy = s.h / 2;
        int outer = Math.min(s.w, s.h) / 2 - 2;
        int band = outer - Math.max(3, outer / 9);
        s.circle(cx, cy, outer);
        s.circle(cx, cy, band);
        int ticks = new int[] {16, 24, 32}[random.nextInt(3)];
        for (int i = 0; i < ticks; i++) {
            s.ray(cx, cy, Mth.TWO_PI * i / ticks, i % (ticks / 8) == 0 ? band + 1 : outer - 1, outer - 1);
        }
        int middle = round(band * (0.35 + 0.25 * random.nextDouble()));
        s.circle(cx, cy, middle);
        int spokes = new int[] {4, 6, 8, 12}[random.nextInt(4)];
        double turn = random.nextBoolean() ? 0.0 : Math.PI / spokes;
        for (int i = 0; i < spokes; i++) {
            s.ray(cx, cy, turn + Mth.TWO_PI * i / spokes, middle + 1, band - 1);
        }
        int points = random.nextBoolean() ? 4 : 8;
        int[][] star = new int[points * 2][];
        for (int i = 0; i < points * 2; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * i / points;
            int r = i % 2 == 0 ? middle - 2 : Math.max(2, middle / 4);
            star[i] = new int[] {cx + round(Math.cos(angle) * r), cy + round(Math.sin(angle) * r)};
        }
        drawPolygon(s, star);
        double pointer = random.nextDouble() * Mth.TWO_PI;
        int tx = cx + round(Math.cos(pointer) * (band - 2));
        int ty = cy + round(Math.sin(pointer) * (band - 2));
        s.line(cx, cy, tx, ty);
        s.disc(tx, ty, 1);
    }

    /** Orbits round a sun: a rayed sun, two to four orbits, a planet on each where its seed puts it, one with a moon. */
    private static void orbits(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int cy = s.h / 2;
        int most = Math.min(s.w, s.h) / 2 - 3;
        int sun = Math.max(2, most / 9 + random.nextInt(2));
        s.discFill(cx, cy, sun);
        int rays = random.nextBoolean() ? 8 : 12;
        for (int i = 0; i < rays; i++) {
            s.ray(cx, cy, Mth.TWO_PI * i / rays, sun + 2, sun + (i % 2 == 0 ? 4 : 2));
        }
        int count = 2 + random.nextInt(3);
        int first = sun + 7;
        for (int i = 0; i < count; i++) {
            int r = count == 1 ? most : first + (most - first) * i / (count - 1);
            s.circle(cx, cy, r);
            double at = random.nextDouble() * Mth.TWO_PI;
            int px = cx + round(Math.cos(at) * r);
            int py = cy + round(Math.sin(at) * r);
            int planet = 1 + random.nextInt(2);
            s.clearDisc(px, py, planet + 1);
            s.discFill(px, py, planet);
            if (i == count - 1 && random.nextBoolean()) {
                s.circle(px, py, planet + 3);
            }
        }
    }

    /** Crystals: three to five six-sided crystals growing from a base, each leaning its own way, a facet down its middle. */
    private static void crystals(Sheet s, RandomSource random) {
        int base = s.h - 4;
        int count = 3 + random.nextInt(3);
        s.line(3, base + 1, s.w - 4, base + 1);
        for (int i = 0; i < count; i++) {
            double along = (i + 0.5) / count;
            int foot = round(4 + (s.w - 8) * along + (random.nextDouble() - 0.5) * 4);
            double tall = (s.h - 8) * (0.45 + 0.5 * random.nextDouble()) * (i == count / 2 ? 1.0 : 0.8);
            double half = Math.max(3.0, s.w / (count * 2.6) * (0.8 + 0.4 * random.nextDouble()));
            double lean = (along - 0.5) * 0.9 + (random.nextDouble() - 0.5) * 0.3;
            double[][] shape = {{-half, 0}, {-half, -tall * 0.78}, {0, -tall}, {half, -tall * 0.78}, {half, 0}};
            int[][] points = new int[shape.length][];
            for (int k = 0; k < shape.length; k++) {
                double x = shape[k][0];
                double y = shape[k][1];
                points[k] = new int[] {foot + round(x * Math.cos(lean) - y * Math.sin(lean)),
                        base + round(x * Math.sin(lean) + y * Math.cos(lean))};
            }
            for (int k = 0; k + 1 < points.length; k++) {
                s.line(points[k][0], points[k][1], points[k + 1][0], points[k + 1][1]);
            }
            s.line(points[2][0], points[2][1], foot, base);
            s.line(points[1][0], points[1][1], points[3][0], points[3][1]);
        }
        int sparks = 2 + random.nextInt(3);
        for (int i = 0; i < sparks; i++) {
            int x = 4 + random.nextInt(Math.max(1, s.w - 8));
            int y = 3 + random.nextInt(Math.max(1, s.h / 3));
            s.line(x - 1, y, x + 1, y);
            s.line(x, y - 1, x, y + 1);
        }
    }

    /** A table of runes: a grid of three or four cells a side, a rune of its own in each, a few cells left empty or marked. */
    private static void runeTable(Sheet s, RandomSource random) {
        int cells = 3 + random.nextInt(2);
        int side = Math.min(s.w, s.h) - 4;
        int cell = side / cells;
        int x0 = (s.w - cell * cells) / 2;
        int y0 = (s.h - cell * cells) / 2;
        for (int i = 0; i <= cells; i++) {
            s.line(x0, y0 + i * cell, x0 + cells * cell, y0 + i * cell);
            s.line(x0 + i * cell, y0, x0 + i * cell, y0 + cells * cell);
        }
        for (int row = 0; row < cells; row++) {
            for (int column = 0; column < cells; column++) {
                int cx = x0 + column * cell;
                int cy = y0 + row * cell;
                int kind = random.nextInt(10);
                if (kind == 0) {
                    continue;
                }
                if (kind == 1) {
                    s.circle(cx + cell / 2, cy + cell / 2, Math.max(1, cell / 4));
                    continue;
                }
                // A rune: two to four strokes between points of a three by three lattice inside the cell.
                int m = Math.max(2, cell / 5);
                int span = cell - 2 * m;
                int[] last = {random.nextInt(3), random.nextInt(3)};
                int strokes = 2 + random.nextInt(3);
                for (int k = 0; k < strokes; k++) {
                    int[] next = {random.nextInt(3), random.nextInt(3)};
                    if (next[0] == last[0] && next[1] == last[1]) {
                        next[1] = (next[1] + 1) % 3;
                    }
                    s.line(cx + m + last[0] * span / 2, cy + m + last[1] * span / 2, cx + m + next[0] * span / 2,
                            cy + m + next[1] * span / 2);
                    last = random.nextBoolean() ? next : new int[] {random.nextInt(3), random.nextInt(3)};
                }
            }
        }
    }

    /** A spiral winding out from a small ring, beads along it, a few short rays at its end. */
    private static void spiral(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int cy = s.h / 2;
        double most = Math.min(s.w, s.h) / 2.0 - 3.0;
        double turns = 2.5 + random.nextDouble() * 1.5;
        double start = 3.0;
        boolean clockwise = random.nextBoolean();
        int steps = (int) (turns * 40);
        int[] last = null;
        int beads = 4 + random.nextInt(5);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double angle = (clockwise ? 1 : -1) * t * turns * Mth.TWO_PI;
            double r = start + (most - start) * t;
            int px = cx + round(Math.cos(angle) * r);
            int py = cy + round(Math.sin(angle) * r);
            if (last != null) {
                s.line(last[0], last[1], px, py);
            }
            if (i > 0 && i % (steps / beads) == 0) {
                s.disc(px, py, 1);
            }
            last = new int[] {px, py};
        }
        s.circle(cx, cy, 2);
    }

    /** A constellation: a chain of stars of its own, one bright star with rays, a moon in its own phase, scattered dots. */
    private static void stars(Sheet s, RandomSource random) {
        int count = 5 + random.nextInt(4) + (s.w > s.h * 2 ? 2 : 0);
        List<int[]> chain = new ArrayList<>();
        boolean wide = s.w > s.h * 2;
        int top = wide ? 3 : s.h * 3 / 8;
        int x = 4 + random.nextInt(Math.max(1, s.w / 8));
        int y = top + random.nextInt(Math.max(1, s.h - top - 4));
        int stride = Math.max(4, (s.w - 8) / count);
        for (int i = 0; i < count; i++) {
            chain.add(new int[] {x, y});
            x = Mth.clamp(x + stride / 2 + random.nextInt(stride), 3, s.w - 4);
            y = Mth.clamp(y + random.nextInt(Math.max(1, s.h / 3)) - s.h / 6, top, s.h - 4);
        }
        for (int i = 1; i < chain.size(); i++) {
            s.line(chain.get(i - 1)[0], chain.get(i - 1)[1], chain.get(i)[0], chain.get(i)[1]);
        }
        if (chain.size() > 4 && random.nextBoolean()) {
            int[] last = chain.get(chain.size() - 1);
            int[] back = chain.get(chain.size() - 3);
            s.line(last[0], last[1], back[0], back[1]);
        }
        for (int[] star : chain) {
            s.disc(star[0], star[1], random.nextInt(4) == 0 ? 2 : 1);
        }
        if (!wide) {
            boolean moonLeft = random.nextBoolean();
            int reach = Math.max(4, s.w / 9);
            int bx = moonLeft ? s.w - reach - 4 : reach + 4;
            int by = reach + 2;
            int rays = random.nextBoolean() ? 4 : 8;
            for (int i = 0; i < rays; i++) {
                s.ray(bx, by, Mth.TWO_PI * i / rays, 2, i % 2 == 0 ? reach : reach / 2);
            }
            s.disc(bx, by, 1);
            int moon = Math.max(3, s.w / 11);
            int mx = moonLeft ? moon + 4 : s.w - moon - 5;
            int my = moon + 3;
            moon(s, mx, my, moon, random.nextDouble() * Mth.TWO_PI);
        }
        int dots = 6 + random.nextInt(8);
        for (int i = 0; i < dots; i++) {
            s.dot(2 + random.nextInt(Math.max(1, s.w - 4)), 2 + random.nextInt(Math.max(1, s.h - 4)));
        }
    }

    // Tall.

    /** A herb as in a herbal: a swaying stem, leaves in pairs or by turns, a flower or berries at its top, its roots. */
    private static void herb(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int ground = s.h - round(s.h * 0.22);
        int top = 6;
        double sway = (random.nextDouble() - 0.5) * s.w * 0.3;
        int[][] stem = new int[7][];
        for (int i = 0; i < stem.length; i++) {
            double t = i / (stem.length - 1.0);
            stem[i] = new int[] {cx + round(Math.sin(t * Math.PI) * sway), ground - round((ground - top) * t)};
        }
        for (int i = 1; i < stem.length; i++) {
            s.line(stem[i - 1][0], stem[i - 1][1], stem[i][0], stem[i][1]);
        }
        s.line(3, ground + 1, s.w - 4, ground + 1);
        boolean pairs = random.nextBoolean();
        int leaves = 3 + random.nextInt(3);
        double size = s.w * (0.28 + 0.12 * random.nextDouble());
        for (int i = 0; i < leaves; i++) {
            int[] at = stem[1 + i * (stem.length - 3) / Math.max(1, leaves - 1)];
            for (int side = -1; side <= 1; side += 2) {
                if (!pairs && (i % 2 == 0) == (side < 0)) {
                    continue;
                }
                leaf(s, at[0], at[1], side, size * (1.0 - 0.15 * i / leaves));
            }
        }
        int[] crown = stem[stem.length - 1];
        if (random.nextBoolean()) {
            int petals = 5 + random.nextInt(3);
            int r = Math.max(2, s.w / 10);
            for (int i = 0; i < petals; i++) {
                double angle = Mth.TWO_PI * i / petals;
                s.circle(crown[0] + round(Math.cos(angle) * r), crown[1] + round(Math.sin(angle) * r), Math.max(1, r - 1));
            }
            s.disc(crown[0], crown[1], 1);
        } else {
            int berries = 3 + random.nextInt(3);
            for (int i = 0; i < berries; i++) {
                double angle = -Math.PI / 2.0 + (i - (berries - 1) / 2.0) * 0.6;
                int bx = crown[0] + round(Math.cos(angle) * 5);
                int by = crown[1] + round(Math.sin(angle) * 5);
                s.line(crown[0], crown[1], bx, by);
                s.discFill(bx, by, 1);
            }
        }
        int roots = 3 + random.nextInt(3);
        for (int i = 0; i < roots; i++) {
            double angle = Math.PI / 2.0 + (i - (roots - 1) / 2.0) * 0.45;
            int length = 4 + random.nextInt(Math.max(1, s.h - ground - 4));
            int ex = cx + round(Math.cos(angle) * length);
            int ey = ground + 1 + round(Math.sin(angle) * length);
            s.line(cx, ground + 1, ex, Math.min(s.h - 1, ey));
        }
    }

    /** A leaf from the stem at {@code x, y} to the {@code side}, {@code size} pixels long, with its middle vein. */
    private static void leaf(Sheet s, int x, int y, int side, double size) {
        int tipX = x + side * round(size);
        int tipY = y - round(size * 0.45);
        int midX = x + side * round(size * 0.5);
        int upY = y - round(size * 0.55);
        int downY = y - round(size * 0.05);
        s.line(x, y, midX, upY);
        s.line(midX, upY, tipX, tipY);
        s.line(x, y, midX, downY);
        s.line(midX, downY, tipX, tipY);
        s.line(x, y, tipX, tipY);
    }

    /** A flask: a round flask, a wide-bottomed one or a vial, corked, with its liquid's level and bubbles rising in it. */
    private static void flask(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int bottom = s.h - 3;
        int kind = random.nextInt(3);
        int neck = Math.max(2, s.w / 9);
        int neckTop = 6;
        int surface;
        if (kind == 0) {
            int r = Math.min(s.w / 2 - 3, (s.h - 14) / 2);
            int cy = bottom - r;
            s.circle(cx, cy, r);
            s.clearRect(cx - neck + 1, cy - r - 1, 2 * neck - 1, 3);
            s.line(cx - neck, neckTop, cx - neck, cy - r + 1);
            s.line(cx + neck, neckTop, cx + neck, cy - r + 1);
            surface = cy - r / 4 + random.nextInt(Math.max(1, r / 2));
            int half = (int) Math.sqrt(Math.max(0, r * r - (surface - cy) * (surface - cy))) - 1;
            s.line(cx - half, surface, cx + half, surface);
        } else if (kind == 1) {
            int shoulder = neckTop + (s.h - neckTop) / 3;
            int half = s.w / 2 - 3;
            s.line(cx - neck, neckTop, cx - neck, shoulder);
            s.line(cx + neck, neckTop, cx + neck, shoulder);
            s.line(cx - neck, shoulder, cx - half, bottom);
            s.line(cx + neck, shoulder, cx + half, bottom);
            s.line(cx - half, bottom, cx + half, bottom);
            surface = shoulder + (bottom - shoulder) / 3 + random.nextInt(Math.max(1, (bottom - shoulder) / 3));
            double t = (double) (surface - shoulder) / (bottom - shoulder);
            int reach = neck + round((half - neck) * t) - 1;
            s.line(cx - reach, surface, cx + reach, surface);
        } else {
            int half = Math.max(3, s.w / 5);
            s.line(cx - half, neckTop, cx - half, bottom - half);
            s.line(cx + half, neckTop, cx + half, bottom - half);
            for (int i = 0; i <= 8; i++) {
                double angle = Math.PI * i / 8.0;
                int px = cx + round(Math.cos(angle) * half);
                int py = bottom - half + round(Math.sin(angle) * half);
                s.dot(px, py);
            }
            surface = neckTop + (bottom - neckTop) / 3 + random.nextInt(Math.max(1, (bottom - neckTop) / 3));
            s.line(cx - half + 1, surface, cx + half - 1, surface);
        }
        s.fillRect(cx - neck - 1, neckTop - 4, 2 * neck + 3, 4);
        int bubbles = 2 + random.nextInt(3);
        for (int i = 0; i < bubbles; i++) {
            int bx = cx + random.nextInt(Math.max(1, neck * 2 + 1)) - neck;
            int by = surface + 3 + random.nextInt(Math.max(1, (bottom - surface) / 2));
            s.circle(bx, Math.min(bottom - 3, by), 1);
        }
        for (int i = 0; i < 2; i++) {
            int x = random.nextBoolean() ? 2 + random.nextInt(4) : s.w - 3 - random.nextInt(4);
            int y = 4 + random.nextInt(Math.max(1, s.h / 3));
            s.line(x - 1, y, x + 1, y);
            s.line(x, y - 1, x, y + 1);
        }
    }

    /** A sword pointing up: its blade with a fuller, a crossguard, the grip and pommel, runes marked along the blade. */
    private static void sword(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int tip = 2;
        int guard = s.h - round(s.h * (0.26 + 0.06 * random.nextDouble()));
        int blade = Math.max(2, s.w / 7);
        s.line(cx, tip, cx - blade, tip + blade + 2);
        s.line(cx, tip, cx + blade, tip + blade + 2);
        s.line(cx - blade, tip + blade + 2, cx - blade, guard);
        s.line(cx + blade, tip + blade + 2, cx + blade, guard);
        s.line(cx, tip + blade + 3, cx, guard - 3);
        int reach = Math.max(blade + 3, round(s.w * (0.32 + 0.12 * random.nextDouble())));
        s.rect(cx - reach, guard, 2 * reach + 1, 3);
        if (random.nextBoolean()) {
            s.disc(cx - reach, guard + 1, 1);
            s.disc(cx + reach, guard + 1, 1);
        }
        int pommel = s.h - 4;
        s.line(cx - 1, guard + 3, cx - 1, pommel - 2);
        s.line(cx + 1, guard + 3, cx + 1, pommel - 2);
        s.circle(cx, pommel, 2);
        int marks = 2 + random.nextInt(4);
        for (int i = 0; i < marks; i++) {
            int y = tip + blade + 6 + i * Math.max(3, (guard - tip - blade - 10) / Math.max(1, marks));
            if (y < guard - 3) {
                s.line(cx - 1, y, cx + 1, y);
            }
        }
    }

    /** A candle on its dish: the wax with drips, the wick and its flame, short rays of light round the flame. */
    private static void candle(Sheet s, RandomSource random) {
        int cx = s.w / 2;
        int half = Math.max(3, round(s.w * (0.16 + 0.06 * random.nextDouble())));
        int top = round(s.h * (0.32 + 0.12 * random.nextDouble()));
        int foot = s.h - 6;
        s.line(cx - half, top, cx - half, foot);
        s.line(cx + half, top, cx + half, foot);
        s.line(cx - half, top, cx + half, top);
        int drips = 1 + random.nextInt(3);
        for (int i = 0; i < drips; i++) {
            int x = cx - half + 1 + random.nextInt(Math.max(1, 2 * half - 1));
            s.line(x, top + 1, x, top + 2 + random.nextInt(Math.max(1, (foot - top) / 3)));
        }
        int dish = Math.min(s.w / 2 - 2, half + 5);
        s.line(cx - dish, foot + 1, cx + dish, foot + 1);
        s.line(cx - dish + 2, foot + 3, cx + dish - 2, foot + 3);
        s.dot(cx - dish + 1, foot + 2);
        s.dot(cx + dish - 1, foot + 2);
        s.line(cx, top - 1, cx, top - 3);
        int flame = Math.max(3, top / 4);
        int fy = top - 4 - flame;
        s.line(cx, fy - flame, cx - flame / 2 - 1, fy);
        s.line(cx, fy - flame, cx + flame / 2 + 1, fy);
        s.line(cx - flame / 2 - 1, fy, cx, fy + flame / 2 + 1);
        s.line(cx + flame / 2 + 1, fy, cx, fy + flame / 2 + 1);
        int rays = 4 + random.nextInt(3);
        for (int i = 0; i < rays; i++) {
            double angle = -Math.PI + Math.PI * (i + 0.5) / rays;
            s.ray(cx, fy - 1, angle, flame + 3, flame + 5);
        }
    }

    // Wide.

    /** The moon's phases in a row: new to full and back, the full one in the middle, dots between them. */
    private static void moons(Sheet s, RandomSource random) {
        int count = 5 + random.nextInt(3);
        int r = Math.max(2, Math.min(s.h / 2 - 2, (s.w / count) / 2 - 2));
        int cy = s.h / 2;
        for (int i = 0; i < count; i++) {
            int cx = round((i + 0.5) * s.w / count);
            double k = count > 1 ? (double) i / (count - 1) : 0.5;
            double lit = 1.0 - 2.0 * Math.abs(k - 0.5);
            s.circle(cx, cy, r);
            if (lit > 0.05) {
                s.discFill(cx, cy, r);
                if (lit < 0.95) {
                    // The shadow slides off as the moon fills: from over it (new) to clear of it (full).
                    int shift = (k < 0.5 ? -1 : 1) * round(lit * (2 * r + 2));
                    s.clearDisc(cx + shift, cy, r);
                    s.circle(cx, cy, r);
                }
            }
            if (i + 1 < count) {
                s.dot(round((i + 1.0) * s.w / count), cy);
            }
        }
    }

    /** A chain of small seals joined by lines, each with a mark of its own in it. */
    private static void chain(Sheet s, RandomSource random) {
        int count = 3 + random.nextInt(3);
        int r = Math.max(3, Math.min(s.h / 2 - 2, s.w / (count * 3)));
        int cy = s.h / 2;
        int[] xs = new int[count];
        for (int i = 0; i < count; i++) {
            xs[i] = round((i + 0.5) * s.w / count);
            s.circle(xs[i], cy, r);
            switch (random.nextInt(4)) {
                case 0 -> s.disc(xs[i], cy, 1);
                case 1 -> {
                    s.line(xs[i] - r / 2, cy, xs[i] + r / 2, cy);
                    s.line(xs[i], cy - r / 2, xs[i], cy + r / 2);
                }
                case 2 -> drawPolygon(s, polygon(xs[i], cy, 3, Math.max(2, r - 2), -Math.PI / 2.0));
                default -> s.circle(xs[i], cy, Math.max(1, r / 2));
            }
        }
        for (int i = 1; i < count; i++) {
            s.line(xs[i - 1] + r + 1, cy, xs[i] - r - 1, cy);
            if (random.nextBoolean()) {
                s.dot((xs[i - 1] + xs[i]) / 2, cy - 2);
                s.dot((xs[i - 1] + xs[i]) / 2, cy + 2);
            }
        }
    }

    /** A wave with beads at its crests and an arrow at its end. */
    private static void wave(Sheet s, RandomSource random) {
        double periods = 1.5 + random.nextDouble() * 1.5;
        int amplitude = Math.max(2, s.h / 2 - 3);
        int cy = s.h / 2;
        int[] last = null;
        int end = s.w - 6;
        for (int x = 2; x <= end; x++) {
            double angle = (x - 2.0) / (end - 2.0) * periods * Mth.TWO_PI;
            int y = cy - round(Math.sin(angle) * amplitude);
            if (last != null) {
                s.line(last[0], last[1], x, y);
            }
            last = new int[] {x, y};
            double crest = Math.abs(Math.sin(angle));
            if (crest > 0.999) {
                s.disc(x, y, 1);
            }
        }
        if (last != null) {
            s.line(last[0], last[1], s.w - 2, last[1]);
            s.line(s.w - 2, last[1], s.w - 4, last[1] - 2);
            s.line(s.w - 2, last[1], s.w - 4, last[1] + 2);
        }
        s.line(2, cy, end, cy);
    }

    // Helpers.

    private static void moon(Sheet s, int cx, int cy, int r, double phase) {
        s.discFill(cx, cy, r);
        s.clearDisc(cx + round(Math.cos(phase) * (r - 2)), cy + round(Math.sin(phase) * (r - 2)), r - 1);
    }

    /** The middle of a circle: a dot, a cross, a small diamond or a small ring. */
    private static void middle(Sheet s, int cx, int cy, RandomSource random) {
        switch (random.nextInt(4)) {
            case 0 -> s.disc(cx, cy, 1 + random.nextInt(2));
            case 1 -> {
                s.line(cx - 3, cy, cx + 3, cy);
                s.line(cx, cy - 3, cx, cy + 3);
            }
            case 2 -> drawPolygon(s, polygon(cx, cy, 4, 4, 0.0));
            default -> {
                s.circle(cx, cy, 2);
                s.dot(cx, cy);
            }
        }
    }

    private static int[][] polygon(int cx, int cy, int points, int radius, double turn) {
        int[][] tips = new int[points][];
        for (int i = 0; i < points; i++) {
            double angle = turn + Mth.TWO_PI * i / points;
            tips[i] = new int[] {cx + round(Math.cos(angle) * radius), cy + round(Math.sin(angle) * radius)};
        }
        return tips;
    }

    private static void drawPolygon(Sheet s, int[][] points) {
        for (int i = 0; i < points.length; i++) {
            int[] a = points[i];
            int[] b = points[(i + 1) % points.length];
            s.line(a[0], a[1], b[0], b[1]);
        }
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    /** Pixel drawing on a {@code w} x {@code h} part of the sheet: 1 px lines and circles, as drawn by hand in Aseprite. */
    private record Sheet(NativeImage image, int w, int h) {
        void dot(int x, int y) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                image.setPixel(x, y, WHITE);
            }
        }

        void clear(int x, int y) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                image.setPixel(x, y, 0);
            }
        }

        /** Bresenham's line. */
        void line(int x0, int y0, int x1, int y1) {
            int dx = Math.abs(x1 - x0);
            int dy = -Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int error = dx + dy;
            while (true) {
                dot(x0, y0);
                if (x0 == x1 && y0 == y1) {
                    return;
                }
                int twice = 2 * error;
                if (twice >= dy) {
                    error += dy;
                    x0 += sx;
                }
                if (twice <= dx) {
                    error += dx;
                    y0 += sy;
                }
            }
        }

        /** A line along {@code angle} from the middle at {@code cx, cy}, from radius {@code from} to {@code to}. */
        void ray(int cx, int cy, double angle, int from, int to) {
            line(cx + round(Math.cos(angle) * from), cy + round(Math.sin(angle) * from), cx + round(Math.cos(angle) * to),
                    cy + round(Math.sin(angle) * to));
        }

        /** The midpoint circle. */
        void circle(int cx, int cy, int r) {
            int x = r;
            int y = 0;
            int error = 1 - r;
            while (x >= y) {
                dot(cx + x, cy + y);
                dot(cx + y, cy + x);
                dot(cx - y, cy + x);
                dot(cx - x, cy + y);
                dot(cx - x, cy - y);
                dot(cx - y, cy - x);
                dot(cx + y, cy - x);
                dot(cx + x, cy - y);
                y++;
                if (error < 0) {
                    error += 2 * y + 1;
                } else {
                    x--;
                    error += 2 * (y - x) + 1;
                }
            }
        }

        /** A small round node: a plus of radius 1, a filled disc above that. */
        void disc(int cx, int cy, int r) {
            if (r <= 1) {
                dot(cx, cy);
                dot(cx + 1, cy);
                dot(cx - 1, cy);
                dot(cx, cy + 1);
                dot(cx, cy - 1);
                return;
            }
            discFill(cx, cy, r);
        }

        void discFill(int cx, int cy, int r) {
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    if (x * x + y * y <= r * r + r) {
                        dot(cx + x, cy + y);
                    }
                }
            }
        }

        void clearDisc(int cx, int cy, int r) {
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    if (x * x + y * y <= r * r + r) {
                        clear(cx + x, cy + y);
                    }
                }
            }
        }

        void rect(int x, int y, int wide, int high) {
            line(x, y, x + wide - 1, y);
            line(x, y + high - 1, x + wide - 1, y + high - 1);
            line(x, y, x, y + high - 1);
            line(x + wide - 1, y, x + wide - 1, y + high - 1);
        }

        void fillRect(int x, int y, int wide, int high) {
            for (int j = 0; j < high; j++) {
                for (int i = 0; i < wide; i++) {
                    dot(x + i, y + j);
                }
            }
        }

        void clearRect(int x, int y, int wide, int high) {
            for (int j = 0; j < high; j++) {
                for (int i = 0; i < wide; i++) {
                    clear(x + i, y + j);
                }
            }
        }
    }
}
