package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The enchanting book's magic, drawn on its pages: writing that wakes letter by letter. A waking letter is not lit by a blot round
 * it but turns into light itself: it takes on the colours of the enchantment glint in turn (violet, orchid, blue, teal),
 * each letter a little further along than the one before it so the colours run through the word, with a thin deep-violet rim
 * as glow-ink writing has, and floats a little off the page. Sparks twinkle round what wakes, each in a colour of its own.
 * Drawings wake the same way, their colours turning round their middle.
 */
final class MagicInk {
    /** The glint's colours, in the order the writing passes through them: deep enough to read as light on cream paper. */
    private static final int[] PALETTE = {0x7A3CFF, 0xC03CFF, 0x3D8BFF, 0x22B8D0};
    /** The rim round a waking letter. */
    private static final int RIM = 0x3B1580;
    /** How far through the colours the light runs a second, and how far each letter is ahead of the last. */
    private static final float COLOUR_RATE = 0.7F;
    private static final float COLOUR_STEP = 0.13F;
    /** How high a waking letter floats at most, units, and how quickly it sways. */
    private static final float FLOAT = 0.7F;
    private static final float SWAY = 6.0F;
    /** A drawing wakes in tiles, each tile's colour by its angle round the middle. */
    private static final int TILES = 4;
    private static final Style RUNES = Style.EMPTY.withFont(Minecraft.ALT_FONT);

    /** What a letter is like now: {@code out[0]} its ink (0: gone), {@code out[1]} how awake (0 to 1), {@code out[2]} how white-hot. */
    @FunctionalInterface
    interface Letters {
        void at(int index, int count, float[] out);
    }

    private MagicInk() {
    }

    /** A colour of the glint's, {@code t} going round them once a unit. */
    static int colour(float t) {
        float at = (t - (float) Math.floor(t)) * PALETTE.length;
        int from = (int) at;
        return mix(PALETTE[from % PALETTE.length], PALETTE[(from + 1) % PALETTE.length], at - from);
    }

    static int mix(int a, int b, float k) {
        float c = Mth.clamp(k, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(c, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(c, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(c, a & 0xFF, b & 0xFF));
        return r << 16 | g << 8 | bl;
    }

    /** A colour of the glint's picked by {@code random} (0 to 1), for a spark or a glyph flying off. */
    static int any(float random) {
        return PALETTE[Math.min(PALETTE.length - 1, (int) (random * PALETTE.length))];
    }

    /**
     * Writes {@code letters} (runes of the {@code alt} font) from {@code x}, each letter as {@code look} says: plain ink where it
     * sleeps, light where it wakes. Returns the width written, units.
     */
    static float write(OpenBook.Canvas canvas, String letters, float x, float y, float scale, int ink, float alpha, float time,
            Letters look) {
        Font font = Minecraft.getInstance().font;
        float[] state = new float[3];
        float at = x;
        int count = letters.length();
        for (int i = 0; i < count; i++) {
            char c = letters.charAt(i);
            FormattedCharSequence letter = FormattedCharSequence.forward(String.valueOf(c), RUNES);
            float wide = font.width(letter) * scale;
            if (c != ' ') {
                state[0] = 1.0F;
                state[1] = 0.0F;
                state[2] = 0.0F;
                look.at(i, count, state);
                float kept = Mth.clamp(state[0], 0.0F, 1.0F);
                float awake = Mth.clamp(state[1], 0.0F, 1.0F);
                if (kept > 0.0F && awake < 1.0F) {
                    canvas.text(letter, at, y, scale, ink, alpha * kept * (1.0F - awake));
                }
                if (kept > 0.0F && awake > 0.0F) {
                    int light = mix(colour(time * COLOUR_RATE + i * COLOUR_STEP), 0xFFFFFF, state[2]);
                    float rise = awake * FLOAT * (0.6F + 0.4F * Mth.sin(time * SWAY + i * 0.9F));
                    canvas.brightText(letter, at, y, scale, light, RIM, alpha * kept * awake, rise);
                }
            }
            at += wide;
        }
        return at - x;
    }

    /** The width of {@code letters} at {@code scale}, units, and where letter {@code index}'s middle is from the start. */
    static float middleOf(String letters, int index, float scale) {
        Font font = Minecraft.getInstance().font;
        float at = 0.0F;
        for (int i = 0; i < index && i < letters.length(); i++) {
            at += font.width(FormattedCharSequence.forward(String.valueOf(letters.charAt(i)), RUNES)) * scale;
        }
        float own = index < letters.length() ? font.width(FormattedCharSequence.forward(String.valueOf(letters.charAt(index)), RUNES))
                : 0.0F;
        return at + own * scale / 2.0F;
    }

    /**
     * Sparks twinkling in the box {@code x0, y0} to {@code x1, y1} (units) at {@code strength}: {@code count} of them come and go,
     * each a little four-pointed star in a colour of the glint's with a white heart, where {@code seed} and the time put it.
     */
    static void sparks(OpenBook.Canvas canvas, long seed, float time, float x0, float y0, float x1, float y1, float strength,
            int count) {
        if (strength <= 0.02F) {
            return;
        }
        for (int k = 0; k < count; k++) {
            float period = 0.5F + 0.12F * (k % 3);
            float shifted = time / period + fraction(mix(seed * 31L + k)) * 7.0F;
            long turn = (long) Math.floor(shifted);
            float phase = shifted - turn;
            long hash = mix(seed * 0x9E3779B97F4A7C15L + k * 0x632BE59BD9B4E019L + turn);
            if (Math.floorMod(hash, 4) == 0) {
                continue;
            }
            float px = x0 + fraction(hash >>> 8) * (x1 - x0);
            float py = y0 + fraction(hash >>> 24) * (y1 - y0);
            float life = Mth.sin(phase * Mth.PI);
            spark(canvas, px, py, 1.0F + 2.0F * life, any(fraction(hash >>> 40)), strength * life);
        }
    }

    /** A four-pointed spark at {@code x, y}: rays {@code reach} units long in {@code rgb}, a white heart. */
    static void spark(OpenBook.Canvas canvas, float x, float y, float reach, int rgb, float alpha) {
        float ray = Math.max(0.5F, reach);
        canvas.brightRect(x - ray - 0.5F, y - 0.5F, 2.0F * ray + 1.0F, 1.0F, rgb, alpha * 0.8F);
        canvas.brightRect(x - 0.5F, y - ray - 0.5F, 1.0F, 2.0F * ray + 1.0F, rgb, alpha * 0.8F);
        canvas.brightRect(x - 0.5F, y - 0.5F, 1.0F, 1.0F, 0xFFFFFF, alpha);
    }

    /**
     * A drawing (the part {@code u1} x {@code v1} of {@code texture}, white lines) {@code wide} x {@code high} units from
     * {@code x, y}: in ink while it sleeps; awake ({@code awake} 0 to 1) its lines turn to light, the glint's colours going round
     * its middle, sparks over it.
     */
    static void drawing(OpenBook.Canvas canvas, ResourceLocation texture, float x, float y, float wide, float high, float u1, float v1,
            int ink, float alpha, float awake, float time, long seed) {
        canvas.sprite(texture, x, y, wide, high, u1, v1, ink, alpha * (1.0F - 0.8F * awake), false);
        if (awake <= 0.02F) {
            return;
        }
        float tileW = wide / TILES;
        float tileH = high / TILES;
        for (int row = 0; row < TILES; row++) {
            for (int column = 0; column < TILES; column++) {
                float angle = (float) Math.atan2((row + 0.5F - TILES / 2.0F) * high, (column + 0.5F - TILES / 2.0F) * wide);
                int rgb = colour(time * 0.45F + angle / Mth.TWO_PI);
                canvas.brightSprite(texture, x + column * tileW, y + row * tileH, tileW, tileH, u1 * column / TILES, v1 * row / TILES,
                        u1 * (column + 1) / TILES, v1 * (row + 1) / TILES, rgb, alpha * awake);
            }
        }
        sparks(canvas, seed, time, x + wide * 0.1F, y + high * 0.1F, x + wide * 0.9F, y + high * 0.9F, alpha * awake,
                Math.max(3, Math.round((wide + high) / 18.0F)));
    }

    private static float fraction(long bits) {
        return (bits & 0xFFFFL) / 65536.0F;
    }

    static long mix(long z) {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }
}
