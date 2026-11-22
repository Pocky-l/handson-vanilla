package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The enchanting book's own writing: every page of it is covered in the enchanting table's runes (vanilla's {@code alt} font, which a
 * resource pack restyles with the enchanting screen), the same page the same each time it is seen. Pages differ: most are
 * paragraphs, some open with a large red initial, some carry a drawing (magic circles, constellations, eyes, astrolabes, orbits;
 * no two alike) under a heading, and some are on older, yellowed paper. The writing is alive ({@link MagicInk}): now and then a word
 * wakes letter by letter in the glint's colours with sparks round it, breaks off letter by letter into glyphs that drift away,
 * and is written again letter by letter as by an unseen pen; drawings wake too. Written on the book over the table in the world,
 * at the station under the offers, and on the sheets that riffle over.
 */
final class RunePages {
    /** The page's writing in units (as the enchanting table lays its pages out): where it starts, how far it runs, its lines. */
    static final int UNITS_WIDE = 100;
    static final int UNITS_HIGH = 156;
    private static final float LEFT = 4.0F;
    private static final float TOP = 5.0F;
    private static final float WIDE = 92.0F;
    private static final float BOTTOM = 136.0F;
    private static final float SCALE = 0.75F;
    private static final float LINE = 8.5F;
    private static final float INDENT = 8.0F;
    /** The enchanting screen's rune ink, and the red of an initial. */
    static final int INK = 0x685E4A;
    private static final int RUBRIC = 0x8E2A1C;
    /** An initial: how large, how much room it keeps beside it. */
    private static final float INITIAL_SCALE = 2.2F;
    private static final int INITIAL_LINES = 2;
    /** Captions and labels by the figures: their size. */
    private static final float CAPTION_SCALE = 0.6F;
    /** The lowest a figure reaches on a page, units (the foot's line stays clear), and the least run of writing beside one. */
    private static final float FIGURES_BOTTOM = 146.0F;
    private static final float LEAST_RUN = 24.0F;
    /** How far writing keeps from a figure, units. */
    private static final float MARGIN = 3.0F;
    /**
     * The hand-drawn drawings and the one page each is on (the magic circle on the first spread, seen over the table in the
     * world); every other drawing page has one of its own ({@link PageDrawings}), so no drawing comes twice.
     */
    private static final List<Identifier> DRAWINGS = List.of(drawing("sigil"), drawing("stars"), drawing("eye"),
            drawing("astrolabe"), drawing("binding"));
    private static final int[] DRAWN_PAGES = {1, 5, 8, 13, 18};
    /** The papers: plain, and an older one, yellowed and spotted. */
    static final Identifier PAPER = OpenBook.PAPER;
    private static final Identifier AGED = HandsOnVanilla.id("textures/entity/enchanting_book_paper_aged.png");
    private static final Style RUNES = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.withDefaultNamespace("alt")));
    private static final int KEPT = 96;
    private static final Map<Integer, Page> PAGES = new HashMap<>();

    /**
     * A living word: two streams a page take turns ({@code PERIOD} and {@code PERIOD + STREAM_STEP} long), each waking a word
     * now and then. Times within a turn: its letters wake one after another until {@code WOKEN}, stay awake, break off one after
     * another from {@code BREAK} to {@code BROKEN}, are gone, and are written again one after another from {@code PEN} to
     * {@code WRITTEN}, each cooling from light to ink over {@code COOL}.
     */
    private static final float PERIOD = 2.9F;
    private static final float STREAM_STEP = 1.1F;
    private static final float WOKEN = 0.5F;
    private static final float BREAK = 1.1F;
    private static final float BROKEN = 1.5F;
    private static final float PEN = 1.7F;
    private static final float WRITTEN = 2.3F;
    private static final float COOL = 0.35F;
    /** Out of ten turns, how many wake a word. */
    private static final int LIVELY = 8;
    /** The least width of a word that wakes, units: a word of two runes is too small to be seen. */
    private static final float LIVELY_WIDE = 12.0F;
    /** A drawing wakes on its own, slowly: how long a turn. */
    private static final float DRAWING_PERIOD = 4.3F;

    /** A word of a page: its runes, where it stands (units), how large, how wide, and in which ink. */
    record Word(String letters, FormattedCharSequence text, float x, float y, float scale, float wide, int rgb) {
        float middleY() {
            // Text at a scale is drawn about the middle of its line (OpenBook.Canvas.text).
            return y + Minecraft.getInstance().font.lineHeight / 2.0F;
        }
    }

    /** A point of a page, units. */
    record Spot(float x, float y) {
    }

    /**
     * A figure on a page: hand-drawn ({@code hand}, an index into {@code DRAWINGS}) or drawn from its own seed ({@code hand} -1,
     * {@code key}, of {@code shape}), at {@code x, y}, {@code wide} x {@code high} units.
     */
    private record Figure(int hand, PageDrawings.Shape shape, long key, float x, float y, float wide, float high) {
    }

    /** A rule drawn in ink: a callout's leader or its dot, units. */
    private record Rule(float x, float y, float wide, float high) {
    }

    private record Page(List<Word> words, List<Figure> figures, List<Rule> rules, boolean aged) {
    }

    /** How a page is laid out. */
    private enum Layout {
        TEXT, CENTRE, FLOAT, BOTTOM, STRIP, PAIR, CALLOUT
    }

    private RunePages() {
    }

    private static Identifier drawing(String name) {
        return HandsOnVanilla.id("textures/entity/enchanting_" + name + ".png");
    }

    /** The clock the writing lives by, seconds: the same for every book, so a book read at the table goes on as it was. */
    static float now() {
        return (net.minecraft.util.Util.getMillis() % 3_600_000L) / 1000.0F;
    }

    /** The paper page {@code page} is written on. */
    static Identifier paper(int page) {
        return page(page).aged() ? AGED : PAPER;
    }

    /** Writes page {@code page}'s runes on {@code canvas} at {@code alpha} (also faded by the canvas' own ink), alive at {@code time}. */
    static void paint(OpenBook.Canvas canvas, int page, float alpha, float time) {
        if (alpha <= 0.02F) {
            return;
        }
        Page written = page(page);
        for (int f = 0; f < written.figures().size(); f++) {
            Figure figure = written.figures().get(f);
            Identifier texture;
            float u1 = 1.0F;
            float v1 = 1.0F;
            if (figure.hand() >= 0) {
                texture = DRAWINGS.get(figure.hand());
            } else {
                PageDrawings.Drawn drawn = PageDrawings.of(figure.key(), figure.shape(), PageDrawings.pixels(figure.wide()),
                        PageDrawings.pixels(figure.high()));
                texture = drawn.texture();
                u1 = drawn.u1();
                v1 = drawn.v1();
            }
            MagicInk.drawing(canvas, texture, figure.x(), figure.y(), figure.wide(), figure.high(), u1, v1, INK, alpha * 0.85F,
                    drawingAwake(page * 4 + f, time), time, figure.key());
        }
        int rule = Math.round(255.0F * Mth.clamp(alpha * 0.8F, 0.0F, 1.0F)) << 24 | INK;
        for (Rule line : written.rules()) {
            canvas.rect(line.x(), line.y(), line.wide(), line.high(), OpenBook.MARK_LIFT, rule);
        }
        List<Word> words = written.words();
        int lit0 = livingWord(page, 0, time, words);
        int lit1 = livingWord(page, 1, time, words);
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < words.size(); i++) {
            Word word = words.get(i);
            if (i != lit0 && i != lit1) {
                canvas.text(word.text(), word.x(), word.y(), word.scale(), word.rgb(), alpha);
                continue;
            }
            float phase = phase(page, i == lit0 ? 0 : 1, time);
            MagicInk.write(canvas, word.letters(), word.x(), word.y(), word.scale(), word.rgb(), alpha, time,
                    (index, count, out) -> letter(phase, index, count, out));
            if (phase < BROKEN) {
                // Sparks round it while it is awake.
                float strength = Math.min(1.0F, phase / WOKEN) * alpha;
                float high = font.lineHeight * word.scale();
                MagicInk.sparks(canvas, page * 1000L + i, time, word.x() - 2.0F, word.middleY() - high, word.x() + word.wide() + 2.0F,
                        word.middleY() + high, strength, 3);
            } else if (phase >= PEN && phase < WRITTEN) {
                // A spark on the unseen pen's nib, at the letter being written.
                int at = Math.min(word.letters().length() - 1, (int) ((phase - PEN) / (WRITTEN - PEN) * word.letters().length()));
                MagicInk.spark(canvas, word.x() + MagicInk.middleOf(word.letters(), at, word.scale()), word.middleY(), 2.0F,
                        MagicInk.colour(time), alpha);
            }
        }
    }

    /** A letter of a living word at {@code phase} of its turn: awake, breaking off, gone, written again. */
    private static void letter(float phase, int index, int count, float[] out) {
        float along = count > 1 ? (float) index / (count - 1) : 0.0F;
        if (phase < WOKEN) {
            float wakes = along * WOKEN * 0.7F;
            float since = phase - wakes;
            out[1] = smooth(since / 0.15F);
            out[2] = since > 0.0F ? 0.6F * Math.max(0.0F, 1.0F - since / 0.3F) : 0.0F;
        } else if (phase < BREAK) {
            out[1] = 1.0F;
        } else if (phase < BROKEN) {
            float breaks = breaksAt(along);
            if (phase < breaks) {
                out[1] = 1.0F;
                out[2] = smooth((phase - (breaks - 0.15F)) / 0.15F);
            } else {
                out[0] = 0.0F;
            }
        } else if (phase < PEN) {
            out[0] = 0.0F;
        } else {
            float written = PEN + along * (WRITTEN - PEN);
            if (phase < written) {
                out[0] = 0.0F;
            } else {
                out[1] = Math.max(0.0F, 1.0F - (phase - written) / COOL);
                out[2] = Math.max(0.0F, 1.0F - (phase - written) / 0.12F);
            }
        }
    }

    /** When a letter so far {@code along} its word breaks off, seconds into the turn. */
    private static float breaksAt(float along) {
        return BREAK + along * (BROKEN - BREAK) * 0.85F;
    }

    /** The letters of page {@code page} that break off into glyphs after {@code from} and by {@code to} (seconds of {@link #now}). */
    static void crumbling(int page, float from, float to, Consumer<Spot> out) {
        if (to <= from) {
            return;
        }
        Page written = page(page);
        for (int stream = 0; stream < 2; stream++) {
            float period = PERIOD + STREAM_STEP * stream;
            float offset = offset(page, stream) * period;
            long last = (long) Math.floor((to + offset - BREAK) / period);
            for (long turn = last; turn >= last - 1; turn--) {
                int index = word(page, stream, turn, written.words());
                if (index < 0) {
                    continue;
                }
                Word word = written.words().get(index);
                int count = word.letters().length();
                for (int i = 0; i < count; i++) {
                    if (word.letters().charAt(i) == ' ') {
                        continue;
                    }
                    float at = turn * period - offset + breaksAt(count > 1 ? (float) i / (count - 1) : 0.0F);
                    if (at > from && at <= to) {
                        out.accept(new Spot(word.x() + MagicInk.middleOf(word.letters(), i, word.scale()), word.middleY()));
                    }
                }
            }
        }
    }

    /** The word stream {@code stream} of the page wakes at {@code time}, or -1. */
    private static int livingWord(int page, int stream, float time, List<Word> words) {
        float period = PERIOD + STREAM_STEP * stream;
        long turn = (long) Math.floor((time + offset(page, stream) * period) / period);
        return word(page, stream, turn, words);
    }

    private static float phase(int page, int stream, float time) {
        float period = PERIOD + STREAM_STEP * stream;
        float shifted = time + offset(page, stream) * period;
        return shifted - (float) Math.floor(shifted / period) * period;
    }

    /** The word a turn wakes (the first wide enough from a place picked by the turn), or -1 when it wakes none. */
    private static int word(int page, int stream, long turn, List<Word> words) {
        if (words.isEmpty()) {
            return -1;
        }
        long hash = MagicInk.mix(page * 0x9E3779B97F4A7C15L + stream * 0xC2B2AE3D27D4EB4FL + turn);
        if (Math.floorMod(hash, 10) >= LIVELY) {
            return -1;
        }
        int from = (int) Math.floorMod(hash >>> 8, words.size());
        for (int i = 0; i < words.size(); i++) {
            int at = (from + i) % words.size();
            if (words.get(at).wide() >= LIVELY_WIDE) {
                return at;
            }
        }
        return from;
    }

    /** How far into its turn a page's stream starts, 0 to 1, so the pages do not wake in step. */
    private static float offset(int page, int stream) {
        return Math.floorMod(MagicInk.mix(page * 31L + stream * 7L + 0x51L), 1000) / 1000.0F;
    }

    /** How awake a page's drawing is: rising and falling once in two turns of three. */
    private static float drawingAwake(int page, float time) {
        float shifted = time + offset(page, 3) * DRAWING_PERIOD;
        long turn = (long) Math.floor(shifted / DRAWING_PERIOD);
        if (Math.floorMod(MagicInk.mix(page * 131L + turn), 3) == 0) {
            return 0.0F;
        }
        float wave = Mth.sin((shifted - turn * DRAWING_PERIOD) / DRAWING_PERIOD * Mth.PI);
        return wave * wave;
    }

    private static float smooth(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private static Page page(int page) {
        Page written = PAGES.get(page);
        if (written == null) {
            if (PAGES.size() >= KEPT) {
                PAGES.clear();
            }
            written = write(page);
            PAGES.put(page, written);
        }
        return written;
    }

    /**
     * A page: paragraphs of words of two to seven runes, as vanilla's screen writes an offer's runes, seeded by the page so it reads
     * the same every time, laid out as a book's pages are. Most pages are writing (some opening with a red initial); others carry
     * figures: a large one under a heading with a caption, one set to a side with the writing running round it, one at the foot,
     * a wide strip between paragraphs, two small ones side by side with their captions, or one with labelled leaders as a
     * diagram. The hand-drawn drawings are each on a page of their own; every other figure is drawn for its page alone.
     */
    private static Page write(int page) {
        RandomSource random = RandomSource.create(0x5EEDL * 31L + page);
        boolean aged = Math.floorMod(MagicInk.mix(page * 977L + 0x77L), 4) == 0;
        int hand = -1;
        for (int i = 0; i < DRAWN_PAGES.length; i++) {
            if (DRAWN_PAGES[i] == page) {
                hand = i;
            }
        }
        Layout layout = hand >= 0 ? Layout.CENTRE : layout(page);
        List<Word> words = new ArrayList<>();
        List<Figure> figures = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        List<float[]> blocked = new ArrayList<>();
        long key = page * 8L;
        switch (layout) {
            case CENTRE -> {
                float size = hand >= 0 ? 58.0F : 50.0F + random.nextInt(9);
                float y = TOP + 11.0F;
                centred(random, words, UNITS_WIDE / 2.0F, TOP, WIDE * 0.6F, SCALE);
                figures.add(new Figure(hand, PageDrawings.Shape.SQUARE, key, (UNITS_WIDE - size) / 2.0F, y, size, size));
                float caption = y + size + 2.0F;
                centred(random, words, UNITS_WIDE / 2.0F, caption, WIDE * 0.45F, CAPTION_SCALE);
                blocked.add(new float[] {0.0F, 0.0F, UNITS_WIDE, caption + 8.0F});
            }
            case FLOAT -> {
                boolean square = random.nextFloat() < 0.55F;
                float wide = square ? 34.0F + random.nextInt(9) : 24.0F + random.nextInt(7);
                float high = square ? wide : 46.0F + random.nextInt(13);
                boolean right = random.nextBoolean();
                float x = right ? LEFT + WIDE - wide : LEFT;
                float y = random.nextFloat() < 0.4F ? TOP + 1.0F : 22.0F + random.nextInt(Math.max(1, (int) (BOTTOM - high - 34.0F)));
                figures.add(new Figure(-1, square ? PageDrawings.Shape.SQUARE : PageDrawings.Shape.TALL, key, x, y, wide, high));
                float under = y + high + 2.0F;
                if (random.nextFloat() < 0.45F) {
                    centred(random, words, x + wide / 2.0F, y + high + 1.0F, wide, CAPTION_SCALE);
                    under += 6.0F;
                }
                blocked.add(new float[] {right ? x - MARGIN : 0.0F, y - 2.0F, right ? UNITS_WIDE : x + wide + MARGIN, under});
            }
            case BOTTOM -> {
                boolean square = random.nextBoolean();
                float wide = square ? 44.0F + random.nextInt(11) : 84.0F + random.nextInt(9);
                float high = square ? wide : 18.0F + random.nextInt(9);
                boolean caption = random.nextBoolean();
                float y = FIGURES_BOTTOM - high - (caption ? 7.0F : 0.0F);
                figures.add(new Figure(-1, square ? PageDrawings.Shape.SQUARE : PageDrawings.Shape.WIDE, key, (UNITS_WIDE - wide) / 2.0F,
                        y, wide, high));
                if (caption) {
                    centred(random, words, UNITS_WIDE / 2.0F, y + high + 1.0F, WIDE * 0.45F, CAPTION_SCALE);
                }
                blocked.add(new float[] {0.0F, y - MARGIN, UNITS_WIDE, UNITS_HIGH});
            }
            case STRIP -> {
                float wide = 86.0F + random.nextInt(7);
                float high = 16.0F + random.nextInt(9);
                float y = 38.0F + random.nextInt(50);
                figures.add(new Figure(-1, PageDrawings.Shape.WIDE, key, (UNITS_WIDE - wide) / 2.0F, y, wide, high));
                blocked.add(new float[] {0.0F, y - MARGIN, UNITS_WIDE, y + high + MARGIN});
            }
            case PAIR -> {
                boolean square = random.nextBoolean();
                float wide = square ? 30.0F + random.nextInt(7) : 22.0F + random.nextInt(5);
                float high = square ? wide : 40.0F + random.nextInt(9);
                boolean top = random.nextBoolean();
                float y = top ? TOP + 1.0F : FIGURES_BOTTOM - high - 7.0F;
                for (int i = 0; i < 2; i++) {
                    float x = i == 0 ? LEFT + 6.0F : LEFT + WIDE - 6.0F - wide;
                    figures.add(new Figure(-1, square ? PageDrawings.Shape.SQUARE : PageDrawings.Shape.TALL, key + i, x, y, wide, high));
                    centred(random, words, x + wide / 2.0F, y + high + 1.0F, wide + 6.0F, CAPTION_SCALE);
                }
                blocked.add(top ? new float[] {0.0F, 0.0F, UNITS_WIDE, y + high + 9.0F} : new float[] {0.0F, y - MARGIN, UNITS_WIDE, UNITS_HIGH});
            }
            case CALLOUT -> {
                float size = 40.0F + random.nextInt(7);
                float x = (UNITS_WIDE - size) / 2.0F;
                float y = 26.0F + random.nextInt(40);
                figures.add(new Figure(-1, PageDrawings.Shape.SQUARE, key, x, y, size, size));
                int labels = 2 + random.nextInt(3);
                Font font = Minecraft.getInstance().font;
                for (int i = 0; i < labels; i++) {
                    boolean left = i % 2 == 0;
                    int onSide = left ? (labels + 1) / 2 : labels / 2;
                    int index = i / 2;
                    float ly = y + size * (onSide == 1 ? 0.45F : 0.15F + 0.7F * index / (onSide - 1));
                    List<Word> label = new ArrayList<>();
                    float width = line(random, label, 0.0F, ly, 16.0F / CAPTION_SCALE, CAPTION_SCALE);
                    float lx = left ? LEFT : x + size + 6.0F;
                    for (Word word : label) {
                        words.add(new Word(word.letters(), word.text(), word.x() + lx, ly, word.scale(), word.wide(), word.rgb()));
                    }
                    float middle = ly + font.lineHeight / 2.0F - 0.3F;
                    float from = left ? lx + width + 1.5F : x + size * 0.78F;
                    float to = left ? x + size * 0.22F : lx - 1.5F;
                    rules.add(new Rule(from, middle, to - from, 0.6F));
                    float dot = left ? to - 0.7F : from - 0.7F;
                    rules.add(new Rule(dot, middle - 0.7F, 2.0F, 2.0F));
                }
                blocked.add(new float[] {0.0F, y - MARGIN, UNITS_WIDE, y + size + MARGIN});
            }
            default -> {
            }
        }
        flow(random, words, blocked, layout == Layout.TEXT && random.nextFloat() < 0.4F);
        return new Page(List.copyOf(words), List.copyOf(figures), List.copyOf(rules), aged);
    }

    /** How page {@code page} is laid out, by its own seed: writing on about a third of the pages, figures on the rest. */
    private static Layout layout(int page) {
        int roll = (int) Math.floorMod(MagicInk.mix(page * 0x2545F4914F6CDD1DL + 0x1234L), 100);
        if (roll < 34) {
            return Layout.TEXT;
        }
        if (roll < 44) {
            return Layout.CENTRE;
        }
        if (roll < 66) {
            return Layout.FLOAT;
        }
        if (roll < 75) {
            return Layout.BOTTOM;
        }
        if (roll < 84) {
            return Layout.STRIP;
        }
        if (roll < 92) {
            return Layout.PAIR;
        }
        return Layout.CALLOUT;
    }

    /**
     * Fills the page with paragraphs from the top, each line in the widest stretch of the page the figures leave free on it (a
     * line with too little room is left empty and starts a new paragraph after it); {@code initial} opens with a red initial.
     */
    private static void flow(RandomSource random, List<Word> words, List<float[]> blocked, boolean initial) {
        Font font = Minecraft.getInstance().font;
        float y = TOP;
        if (initial) {
            String letter = String.valueOf((char) ('a' + random.nextInt(26)));
            FormattedCharSequence big = Component.literal(letter).withStyle(RUNES).getVisualOrderText();
            float wide = font.width(big) * INITIAL_SCALE;
            // Its top on the first line's top: scaled text is drawn about the middle of its line.
            words.add(new Word(letter, big, LEFT, y + font.lineHeight * (INITIAL_SCALE - 1.0F) * 0.5F, INITIAL_SCALE, wide, RUBRIC));
            blocked.add(new float[] {0.0F, y - 1.0F, LEFT + wide + 2.0F, y + INITIAL_LINES * LINE - 1.0F});
        }
        int left = 0;
        boolean start = !initial;
        boolean first = true;
        while (y + 7.0F <= BOTTOM) {
            float[] free = widestFree(y - 1.0F, y + 8.0F, blocked);
            if (free[1] - free[0] < LEAST_RUN) {
                y += LINE;
                left = 0;
                continue;
            }
            if (left <= 0) {
                left = 3 + random.nextInt(4);
                start = !(first && initial);
            }
            float indent = start ? INDENT : 0.0F;
            float room = (free[1] - free[0] - indent) / SCALE;
            if (left == 1) {
                // The paragraph's last line stops short.
                room *= 0.35F + 0.45F * random.nextFloat();
            }
            line(random, words, free[0] + indent, y, room, SCALE);
            left--;
            start = false;
            first = false;
            y += LINE;
        }
    }

    /** The widest stretch of the writing's width that no blocked box covers between {@code y0} and {@code y1}: {from, to}. */
    private static float[] widestFree(float y0, float y1, List<float[]> blocked) {
        List<float[]> free = new ArrayList<>();
        free.add(new float[] {LEFT, LEFT + WIDE});
        for (float[] box : blocked) {
            if (box[1] >= y1 || box[3] <= y0) {
                continue;
            }
            List<float[]> next = new ArrayList<>();
            for (float[] run : free) {
                if (box[2] <= run[0] || box[0] >= run[1]) {
                    next.add(run);
                    continue;
                }
                if (box[0] > run[0]) {
                    next.add(new float[] {run[0], box[0]});
                }
                if (box[2] < run[1]) {
                    next.add(new float[] {box[2], run[1]});
                }
            }
            free = next;
        }
        float[] best = {LEFT, LEFT};
        for (float[] run : free) {
            if (run[1] - run[0] > best[1] - best[0]) {
                best = run;
            }
        }
        return best;
    }

    /** A line of words centred on {@code middle} at {@code y}, as wide as {@code room} units at most. */
    private static void centred(RandomSource random, List<Word> out, float middle, float y, float room, float scale) {
        List<Word> line = new ArrayList<>();
        float width = line(random, line, 0.0F, y, room / scale, scale);
        float shift = middle - width / 2.0F;
        for (Word word : line) {
            out.add(new Word(word.letters(), word.text(), word.x() + shift, word.y(), word.scale(), word.wide(), word.rgb()));
        }
    }

    /** A line of words from {@code x}, as many as fit in {@code room} font pixels; returns its width in units. */
    private static float line(RandomSource random, List<Word> out, float x, float y, float room, float scale) {
        Font font = Minecraft.getInstance().font;
        float space = font.width(Component.literal(" ").withStyle(RUNES)) * scale;
        float at = 0.0F;
        while (true) {
            String letters = word(random);
            FormattedCharSequence text = Component.literal(letters).withStyle(RUNES).getVisualOrderText();
            float wide = font.width(text) * scale;
            float next = at == 0.0F ? wide : at + space + wide;
            if (next > room * scale) {
                return at;
            }
            out.add(new Word(letters, text, x + (at == 0.0F ? 0.0F : at + space), y, scale, wide, INK));
            at = next;
        }
    }

    private static String word(RandomSource random) {
        int length = 2 + random.nextInt(6);
        char[] letters = new char[length];
        for (int i = 0; i < length; i++) {
            letters[i] = (char) ('a' + random.nextInt(26));
        }
        return new String(letters);
    }
}
