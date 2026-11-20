package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BannerPattern;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A banner layer painted on by a brush: vanilla's pattern texture copied, pixel by pixel as the brush reaches them, into a texture of
 * its own, which the loom draws on vanilla's flag over the banner's layers (the same faces, so the same depth), tinted by the dye.
 * Only the pixels the pattern colours are painted, in bands of a few rows from the top of the cloth down, each band swept across
 * and back, so the brush goes where the paint is needed.
 */
final class BannerPaint {
    /** The flag's front in the banner texture: columns 1 to 20, rows 1 to 40 (64 x 64). */
    static final int FRONT_U = 1;
    static final int FRONT_V = 1;
    static final int WIDE = 20;
    static final int HIGH = 40;
    /** Rows a sweep of the brush covers. */
    private static final int BAND = 4;
    private static final ResourceLocation TEXTURE = HandsOnVanilla.id("dynamic/loom_paint");
    private static final Map<ResourceKey<BannerPattern>, NativeImage> PATTERNS = new HashMap<>();
    private static DynamicTexture canvas;

    /** The pixels to paint, in the brush's order: column and row in the banner texture. */
    private final List<int[]> order = new ArrayList<>();
    private NativeImage pattern;
    private int painted;

    /** Readies the paint of one layer: clears the canvas and lays out the brush's way over the pattern's pixels. */
    boolean begin(ResourceKey<BannerPattern> key) {
        order.clear();
        painted = 0;
        pattern = image(key);
        NativeImage pixels = canvas();
        if (pattern == null || pixels == null) {
            return false;
        }
        pixels.fillRect(0, 0, pixels.getWidth(), pixels.getHeight(), 0);
        canvas.upload();
        for (int band = 0; band < HIGH; band += BAND) {
            // Across and back, band after band: the way a brush is drawn over cloth.
            boolean back = band / BAND % 2 == 1;
            for (int i = 0; i < WIDE; i++) {
                int column = back ? WIDE - 1 - i : i;
                for (int row = band; row < Math.min(HIGH, band + BAND); row++) {
                    int u = FRONT_U + column;
                    int v = FRONT_V + row;
                    if (u < pattern.getWidth() && v < pattern.getHeight() && (pattern.getPixelRGBA(u, v) >>> 24) > 8) {
                        order.add(new int[] {u, v});
                    }
                }
            }
        }
        return !order.isEmpty();
    }

    /** How many pixels the brush has to paint. */
    int size() {
        return order.size();
    }

    /** Paints the pixels up to {@code part} (0 to 1) of the way; the canvas is uploaded once if any changed. */
    void paintTo(float part) {
        int to = Math.min(order.size(), Math.round(order.size() * part));
        if (to <= painted || pattern == null || canvas == null) {
            return;
        }
        NativeImage pixels = canvas.getPixels();
        for (int i = painted; i < to; i++) {
            int[] at = order.get(i);
            pixels.setPixelRGBA(at[0], at[1], pattern.getPixelRGBA(at[0], at[1]));
        }
        painted = to;
        canvas.upload();
    }

    /** Where the brush is at {@code part} of the way: the column and row (in the flag's front, 0 based, as floats); null: nowhere. */
    float[] brushAt(float part) {
        if (order.isEmpty()) {
            return null;
        }
        float at = Math.max(0.0F, Math.min(order.size() - 1, part * order.size() - 0.5F));
        int i = (int) at;
        int[] a = order.get(i);
        int[] b = order.get(Math.min(order.size() - 1, i + 1));
        float t = at - i;
        return new float[] {a[0] - FRONT_U + (b[0] - a[0]) * t, a[1] - FRONT_V + (b[1] - a[1]) * t};
    }

    static ResourceLocation texture() {
        return TEXTURE;
    }

    private static NativeImage canvas() {
        if (canvas == null) {
            canvas = new DynamicTexture(64, 64, true);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, canvas);
        }
        return canvas.getPixels();
    }

    /** Vanilla's (or a pack's) pattern texture, read once. */
    private static NativeImage image(ResourceKey<BannerPattern> key) {
        return PATTERNS.computeIfAbsent(key, k -> {
            ResourceLocation sheet = BannerPattern.location(k, true);
            ResourceLocation file = sheet.withPath(path -> "textures/" + path + ".png");
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(file)) {
                return NativeImage.read(in);
            } catch (Exception e) {
                HandsOnVanilla.LOGGER.warn("Cannot read the banner pattern {}", file, e);
                return null;
            }
        });
    }
}
