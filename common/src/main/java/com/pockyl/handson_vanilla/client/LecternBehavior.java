package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * A book on a lectern, read on the lectern: the book the lectern shows is the scene's (the same model, in exactly the place and
 * pose the lectern draws it), with its text written on its two loose sheets, two pages at once. The wheel, Page Up and Down, Home
 * and End, or a click on the arrow at a page's foot turn the spread; a click on the page that is not the lectern's own page makes it
 * the lectern's (for redstone and other readers), and a link in the text works as in vanilla's screen. The lectern's page is the
 * server's: another reader's turn turns this book too. A red ribbon over the lectern's lip takes the book (not in adventure mode).
 * Every page change is the menu's button, sent as vanilla's screen sends it; the server's page sound comes with it.
 */
final class LecternBehavior implements StationBehavior {
    static final String BOOK = "book";
    private static final String NODE = "book";
    private static final String RIBBON = "ribbon";
    /** A page in units, about 30 to a pixel of the model's page; the text block as vanilla's screen has it (114 x 14 lines of 9), in its middle. */
    private static final int UNITS_WIDE = 155;
    private static final int UNITS_HIGH = 242;
    private static final float TEXT_X = 20.0F;
    private static final float TEXT_Y = 12.0F;
    private static final int TEXT_WIDE = 114;
    private static final int LINES = 14;
    /** Vanilla's 14 lines spread over the page: a line every 14 units, the letters 9 high in it. */
    private static final float LINE_PITCH = 14.0F;
    private static final float FOOT_TOP = 214.0F;
    private static final float ARROW_X = 5.0F;
    /** The lectern's page number in red, the other page's in grey; the ink of the text and of the arrows. */
    private static final int OWN_PAGE = 0xAA0000;
    private static final int OTHER_PAGE = 0x6B6B6B;
    private static final int INK = 0x000000;
    private static final int ARROW = 0xFF3A2A1A;
    private static final int ARROW_LIT = 0xFF8A5A2B;
    private static final int RIBBON_COLOR = 0x9E2424;
    private static final int RIBBON_LIT = 0xD04238;
    /** The lectern's book as {@code LecternRenderer} has it: covers' angle and the right loose page (the left one mirrors it). */
    private static final float OPEN = 1.25F * 1.2F;
    private static final float RIGHT_SHEET = OPEN - 2.0F * OPEN * 0.1F;
    /** Where the sheets lie once the book is this mod's: on the blocks of pages, as wide and high as they are. */
    private static final float SHEET_REST = OPEN * 0.97F;
    /** Ticks: the least time between two page buttons, and how long an own turn may wait for the server's page to follow it. */
    private static final int RATE = 3;
    private static final int SETTLE = 10;
    /**
     * The ribbon: half its width, how far it lies on down the page from the spine's foot (past the front edge of the lectern's
     * board), how far it hangs from there, pixels.
     */
    private static final float RIBBON_HALF = 0.5F;
    private static final float RIBBON_LIE = 2.4F;
    private static final float RIBBON_HANG = 1.6F;
    private static final float SHUT_TIME = 0.3F;
    /** How far into the camera's flight the lectern's own book has given way to this one. */
    private static final float HAND_OFF = 0.3F;
    private static final net.minecraft.resources.Identifier WHITE = HandsOnVanilla.id("block/white");

    private final OpenBook book = new OpenBook(UNITS_WIDE, UNITS_HIGH, this::paintPage);
    private StationView station;
    private ItemStack seenBook = ItemStack.EMPTY;
    private BookViewScreen.BookAccess access = BookViewScreen.EMPTY_ACCESS;
    private final List<List<FormattedCharSequence>> pageLines = new ArrayList<>();
    private int ticks;
    private int lastSend = -100;
    private int pendingTarget = -1;
    private float clock;
    /** What is under the cursor this frame: a page and a point on it, the arrow of a foot (-1 back, 1 on), a link's style. */
    private OpenBook.Hit hit;
    private int footArrow;
    private Style hoveredStyle;
    private boolean ribbonHovered;
    private boolean ribbonShown;
    private float ribbonLit;
    /** The book is being taken: its covers shut until the server closes the menu. */
    private boolean taking;
    private float shut;
    private Component tip;

    @Override
    public void onSceneReady(StationView view) {
        station = view;
        view.node(NODE).ifPresent(node -> node.setPainter(new NodePainter() {
            @Override
            public void paint(PoseStack poseStack, SceneBuffers buffers, int light) {
                drawBook(view, poseStack, buffers);
            }

            /** Read before the camera with text on it: drawn by the scene, its letters showed steps. */
            @Override
            public boolean smooth() {
                return true;
            }
        }));
        view.node(RIBBON).ifPresent(node -> node.setTooltip(Component.translatable("lectern.take_book")));
    }

    @Override
    public void onOpen(StationView view) {
        taking = false;
        shut = 0.0F;
        pendingTarget = -1;
        lastSend = -100;
        seenBook = ItemStack.EMPTY;
        hit = null;
    }

    @Override
    public void onMenuOpened(StationView view) {
        LecternMenu menu = lectern(view);
        if (menu != null) {
            readBook(menu);
            book.jumpTo(menu.getPage() / 2);
        }
    }

    @Override
    public void onClose(StationView view) {
        pendingTarget = -1;
        hit = null;
        hoveredStyle = null;
    }

    /** Page buttons wait their turn ({@code RATE} ticks apart, the latest one kept); the spread follows the lectern's page. */
    @Override
    public void tick(StationView view) {
        ticks++;
        LecternMenu menu = lectern(view);
        if (menu == null) {
            return;
        }
        if (pendingTarget >= 0 && ticks - lastSend >= RATE) {
            send(menu, 100 + pendingTarget);
            pendingTarget = -1;
        }
        int server = menu.getPage();
        // Another reader turned, a link moved it, the server kept it within the book, or an own turn went nowhere: the book
        // turns to the lectern's page, once nothing of this reader's is on its way.
        if (pendingTarget < 0 && ticks - lastSend >= SETTLE && server / 2 != book.spread() && server < pageCount()) {
            book.turnTo(server / 2, false);
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        station = view;
        clock += dt;
        book.frame(dt);
        BlockState state = view.level().getBlockState(view.pos());
        boolean hasBook = state.hasProperty(LecternBlock.HAS_BOOK) && state.getValue(LecternBlock.HAS_BOOK);
        view.node(NODE).ifPresent(node -> node.setVisible(hasBook));
        LecternMenu menu = view.isFocused() ? lectern(view) : null;
        if (menu != null) {
            readBook(menu);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        boolean ribbon = menu != null && hasBook && player != null && player.mayBuild() && !taking;
        view.node(RIBBON).ifPresent(node -> {
            node.setVisible(ribbon);
            ribbonHovered = ribbon && node.isHovered();
            ribbonShown = ribbon;
        });
        ribbonLit = Mth.lerp(Math.min(1.0F, dt * 12.0F), ribbonLit, ribbonHovered ? 1.0F : 0.0F);
        shut = Mth.approach(shut, taking ? 1.0F : 0.0F, dt / SHUT_TIME);
        hit = null;
        footArrow = 0;
        hoveredStyle = null;
        if (menu != null && !taking) {
            hit = book.hit(view, NODE, toNode(view), open(), rightSheet(view), -rightSheet(view)).orElse(null);
            if (hit != null) {
                footArrow = arrowAt(hit);
                hoveredStyle = footArrow == 0 ? styleAt(hit) : null;
            }
        }
        // A link's hover text, as vanilla's book shows it under the cursor.
        Component wanted = hoverText(hoveredStyle);
        if (!java.util.Objects.equals(wanted, tip)) {
            tip = wanted;
            view.node(NODE).ifPresent(node -> node.setTooltip(wanted));
        }
    }

    /** The book's text, read again when the stack on the lectern changes. */
    private void readBook(LecternMenu menu) {
        ItemStack now = menu.getBook();
        if (ItemStack.matches(now, seenBook)) {
            return;
        }
        seenBook = now.copy();
        access = BookViewScreen.BookAccess.fromItem(now);
        pageLines.clear();
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < access.getPageCount(); i++) {
            pageLines.add(font.split(access.getPage(i), TEXT_WIDE));
        }
    }

    /** At least one page: an empty book reads as one blank page. */
    private int pageCount() {
        return Math.max(1, access.getPageCount());
    }

    private int spreads() {
        return (pageCount() + 1) / 2;
    }

    /** -1 or 1 when the point is on the arrow of a page's foot that turns back or on; 0 elsewhere. */
    private int arrowAt(OpenBook.Hit at) {
        if (at.y() < FOOT_TOP) {
            return 0;
        }
        if (!at.right() && at.x() < UNITS_WIDE / 3.0F && book.spread() > 0) {
            return -1;
        }
        if (at.right() && at.x() >= UNITS_WIDE * 2.0F / 3.0F && book.spread() < spreads() - 1) {
            return 1;
        }
        return 0;
    }

    /** The style of the text under a point of a page, as vanilla's screen finds it; null over no text. */
    private Style styleAt(OpenBook.Hit at) {
        if (at.page() >= pageLines.size()) {
            return null;
        }
        int x = Mth.floor(at.x() - TEXT_X);
        int y = Mth.floor(at.y() - TEXT_Y);
        List<FormattedCharSequence> lines = pageLines.get(at.page());
        int line = (int) (y / LINE_PITCH);
        if (x < 0 || y < 0 || x > TEXT_WIDE || line >= Math.min(LINES, lines.size()) || y - line * LINE_PITCH >= 10.0F) {
            return null;
        }
        return styleAtWidth(Minecraft.getInstance().font, lines.get(line), x);
    }

    /** The style of the letter {@code x} pixels into the line, as vanilla's book screen finds what is under the cursor; null past its end. */
    private static Style styleAtWidth(Font font, FormattedCharSequence line, int x) {
        float[] left = {x};
        Style[] found = {null};
        line.accept((index, style, codepoint) -> {
            left[0] -= font.width(FormattedText.of(Character.toString(codepoint), style));
            if (left[0] < 0.0F) {
                found[0] = style;
                return false;
            }
            return true;
        });
        return found[0];
    }

    private static Component hoverText(Style style) {
        return style != null && style.getHoverEvent() instanceof HoverEvent.ShowText(Component value) ? value : null;
    }

    /** Turns to the spread holding {@code target} and tells the lectern, at once or with the next button that may go. */
    private void request(int target, boolean turn) {
        LecternMenu menu = lectern(station);
        if (menu == null) {
            return;
        }
        int page = Mth.clamp(target, 0, pageCount() - 1);
        if (turn) {
            book.turnTo(page / 2, false);
        }
        if (ticks - lastSend >= RATE) {
            send(menu, 100 + page);
            pendingTarget = -1;
        } else {
            pendingTarget = page;
        }
    }

    /** A button as vanilla's lectern screen sends it: no prediction here, the server answers with the page. */
    private void send(LecternMenu menu, int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            lastSend = ticks;
        }
    }

    private void turnBy(int spreads) {
        int to = Mth.clamp(book.spread() + spreads, 0, spreads() - 1);
        if (to != book.spread()) {
            request(2 * to, true);
        }
    }

    private void click(StationView view) {
        if (taking || hit == null || lectern(view) == null) {
            return;
        }
        if (footArrow != 0) {
            turnBy(footArrow);
            return;
        }
        if (hoveredStyle != null && hoveredStyle.getClickEvent() != null) {
            link(view, hoveredStyle);
            return;
        }
        // A click on the page that is not the lectern's makes it the lectern's page.
        LecternMenu menu = lectern(view);
        if (hit.page() < pageCount() && hit.page() != menu.getPage()) {
            request(hit.page(), false);
        }
    }

    /** A link of the text, as vanilla's book screen follows it: another page, or what the game does with a chat link. */
    private void link(StationView view, Style style) {
        ClickEvent event = style.getClickEvent();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        switch (event) {
            case ClickEvent.ChangePage(int page) -> request(page - 1, true);
            case ClickEvent.OpenUrl(URI uri) -> {
                // The confirm screen ends the station; it closes to the world, not back to a station already left.
                if (!minecraft.options.chatLinks().get()) {
                    return;
                }
                String scheme = uri.getScheme();
                if (scheme == null || !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                    return;
                }
                if (minecraft.options.chatLinksPrompt().get()) {
                    minecraft.setScreen(new ConfirmLinkScreen(open -> {
                        if (open) {
                            net.minecraft.util.Util.getPlatform().openUri(uri);
                        }
                        minecraft.setScreen(null);
                    }, uri.toString(), false));
                } else {
                    net.minecraft.util.Util.getPlatform().openUri(uri);
                }
            }
            case ClickEvent.RunCommand(String command) -> {
                if (player == null) {
                    return;
                }
                // Vanilla's book closes before it runs a command: the camera leaves.
                if (DiegesisClient.isStationScreen(minecraft.screen)) {
                    minecraft.screen.onClose();
                }
                player.connection.sendUnattendedCommand(net.minecraft.commands.Commands.trimOptionalPrefix(command), null);
            }
            case ClickEvent.CopyToClipboard(String text) -> minecraft.keyboardHandler.setClipboard(text);
            case ClickEvent.ShowDialog dialog -> {
                if (player != null) {
                    player.connection.showDialog(dialog.dialog(), null);
                }
            }
            case ClickEvent.Custom custom -> {
                if (player != null) {
                    player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket(custom.id(),
                            custom.payload()));
                }
            }
            default -> {
                // A command to suggest or a file to open: nothing to do without a chat line, as in vanilla's book.
            }
        }
    }

    /** The ribbon takes the book: the menu's button 3, as vanilla's Take Book; the server closes the menu after it. */
    private void take(StationView view) {
        LecternMenu menu = lectern(view);
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu == null || taking || player == null || !player.mayBuild()) {
            return;
        }
        taking = true;
        send(menu, 3);
        StationHands.play(view, SoundEvents.BOOK_PUT, RIBBON, 0.35F, 1.2F);
        StationHands.swing();
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.button() == 0) {
            if (RIBBON.equals(click.node())) {
                take(view);
            } else if (NODE.equals(click.node())) {
                click(view);
            }
        }
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        if (NODE.equals(node) && amount != 0.0 && lectern(view) != null && !taking) {
            turnBy(amount < 0.0 ? 1 : -1);
            return true;
        }
        return false;
    }

    /** Page Down and Up turn a spread, Home and End go to the first and the last; the rest are the station's keys. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (lectern(view) == null || taking) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> turnBy(1);
            case GLFW.GLFW_KEY_PAGE_UP -> turnBy(-1);
            case GLFW.GLFW_KEY_HOME -> turnBy(-spreads());
            case GLFW.GLFW_KEY_END -> turnBy(spreads());
            default -> {
                return false;
            }
        }
        return true;
    }

    // Drawing.

    private float open() {
        return OPEN * (1.0F - shut);
    }

    /** The lectern's own book until the reader comes; then, early in the camera's flight, this one fades in over it. */
    private static float own(StationView view) {
        float arrival = view.isFocused() ? DiegesisClient.arrival() : 0.0F;
        float handOff = Mth.clamp(arrival / HAND_OFF, 0.0F, 1.0F);
        return handOff * handOff * (3.0F - 2.0F * handOff);
    }

    /** The right sheet's angle (the left one's is the same, mirrored): vanilla's loose page settling onto its block of pages. */
    private float rightSheet(StationView view) {
        return Mth.lerp(own(view), RIGHT_SHEET, SHEET_REST) * (1.0F - shut);
    }

    /** The lectern's book in the scene's pixels: exactly as {@code LecternRenderer} draws it. */
    private static Matrix4f bookToScene(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        float facing = state.hasProperty(LecternBlock.FACING) ? state.getValue(LecternBlock.FACING).getClockWise().toYRot() : 0.0F;
        Matrix4f block = new Matrix4f().translation(8.0F, 17.0F, 8.0F).rotateY(-facing * Mth.DEG_TO_RAD)
                .rotateZ(67.5F * Mth.DEG_TO_RAD).translate(0.0F, -2.0F, 0.0F);
        return OpenBook.sceneFromBlock(view).mul(block);
    }

    private static Vector3fc nodePos(StationView view) {
        return view.node(NODE).map(n -> n.definition().transform().pos()).orElse(new Vector3f());
    }

    private static Matrix4f toNode(StationView view) {
        Matrix4f scene = bookToScene(view);
        Vector3f at = scene.getTranslation(new Vector3f()).sub(nodePos(view));
        float size = WorldBooks.SCALE;
        return new Matrix4f().translation(at).rotate(scene.getNormalizedRotation(new Quaternionf())).scale(size);
    }

    private void drawBook(StationView view, PoseStack poseStack, SceneBuffers buffers) {
        Matrix4f scene = bookToScene(view);
        Vector3f at = scene.getTranslation(new Vector3f()).sub(nodePos(view));
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(scene.getNormalizedRotation(new Quaternionf()));
        // The block entity's own light, as the lectern's renderer draws the book in it.
        int light = LevelRenderer.getLightColor(view.level(), view.pos());
        float arrival = view.isFocused() ? DiegesisClient.arrival() : 0.0F;
        // The lectern's book in the world is this one too, at its size: only its sheets settle onto the pages as the reader comes.
        float size = WorldBooks.SCALE;
        poseStack.pushPose();
        poseStack.scale(size, size, size);
        book.draw(poseStack, buffers, light, open(), rightSheet(view), -rightSheet(view), arrival, 1.0F);
        poseStack.popPose();
        if (ribbonShown) {
            drawRibbon(poseStack, buffers, light, scene, size);
        }
        poseStack.popPose();
    }

    /** The pages of the spread: the text as vanilla's screen breaks it, the page's number at its foot, the arrows. */
    private void paintPage(OpenBook.Canvas canvas, int page, boolean resting) {
        StationView view = station;
        LecternMenu menu = view == null ? null : lectern(view);
        if (menu == null || page < 0 || page >= pageCount()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        if (page < pageLines.size()) {
            List<FormattedCharSequence> lines = pageLines.get(page);
            for (int i = 0; i < Math.min(LINES, lines.size()); i++) {
                canvas.text(lines.get(i), TEXT_X, TEXT_Y + i * LINE_PITCH, INK, 1.0F);
            }
        }
        String number = (page + 1) + "/" + pageCount();
        int rgb = page == menu.getPage() ? OWN_PAGE : OTHER_PAGE;
        float footY = FOOT_TOP + (UNITS_HIGH - FOOT_TOP - font.lineHeight) / 2.0F + 1.0F;
        canvas.text(Component.literal(number), (UNITS_WIDE - font.width(number)) / 2.0F, footY, rgb, 1.0F);
        boolean right = page % 2 == 1;
        float arrowY = FOOT_TOP + (UNITS_HIGH - FOOT_TOP - OpenBook.ARROW_HIGH) / 2.0F;
        if (!right && page / 2 > 0) {
            canvas.arrow(ARROW_X, arrowY, false, resting && footArrow < 0 ? ARROW_LIT : ARROW);
        }
        if (right && page / 2 < spreads() - 1) {
            canvas.arrow(UNITS_WIDE - ARROW_X - OpenBook.ARROW_WIDE, arrowY, true, resting && footArrow > 0 ? ARROW_LIT : ARROW);
        }
    }

    /**
     * The ribbon: a red bookmark from the foot of the spine lying on down the page and hanging over the lectern's lip; under the
     * cursor it lights and sways. Drawn with the book, in its model's pixels (so the book, drawn over everything, never covers it).
     */
    private void drawRibbon(PoseStack poseStack, SceneBuffers buffers, int light, Matrix4f scene, float size) {
        // Out of the spine between the pages, at the foot of the gutter of the book at its size; it bends over the board's edge where
        // it does at full size, so the end hangs in its node's collider whatever the book's size.
        Vector3f full = OpenBook.spineFoot(open()).add(0.1F, 0.3F, 0.0F);
        Vector3f foot = new Vector3f(full).mul(size);
        Vector3f bend = new Vector3f(foot.x, full.y - RIBBON_LIE, foot.z);
        // Straight down in the scene, in the book's own axes; swaying across the page under the cursor.
        Vector3f down = new Matrix4f(scene).invert().transformDirection(new Vector3f(0.0F, -1.0F, 0.0F)).normalize();
        float sway = Mth.sin(clock * 6.0F) * 0.5F * ribbonLit;
        Vector3f end = new Vector3f(down).mul(RIBBON_HANG).add(bend).add(0.0F, 0.0F, sway);
        int rgb = lerpColor(RIBBON_COLOR, RIBBON_LIT, ribbonLit);
        TextureAtlasSprite white = com.pockyl.diegesis.impl.client.render.SceneDraw.blockSprite(WHITE);
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        Vector3f half = new Vector3f(0.0F, 0.0F, RIBBON_HALF);
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), 10), LightTexture.sky(light));
        strip(poseStack, consumer, foot, bend, half, rgb, white, lit);
        strip(poseStack, consumer, bend, end, half, rgb, white, lit);
    }

    private static void strip(PoseStack poseStack, VertexConsumer consumer, Vector3f from, Vector3f to, Vector3f half, int rgb,
            TextureAtlasSprite white, int light) {
        Matrix4f pose = poseStack.last().pose();
        org.joml.Matrix3f normal = poseStack.last().normal();
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        // Out of the ribbon's face towards the reader (the book's +x), so it is lit as the pages are.
        Vector3f face = new Vector3f(half).cross(new Vector3f(to).sub(from)).normalize();
        Vector3f lit = normal.transform(new Vector3f(face)).normalize();
        Vector3f[] corners = {new Vector3f(from).sub(half), new Vector3f(from).add(half), new Vector3f(to).add(half),
                new Vector3f(to).sub(half)};
        for (Vector3f corner : corners) {
            // The sprite's middle: its corner sits on the edge of the next sprite of the atlas and samples it at a distance.
            consumer.addVertex(pose, corner.x, corner.y, corner.z).setColor(r, g, b, 255).setUv(white.getU(0.5F), white.getV(0.5F))
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(lit.x, lit.y, lit.z);
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    private static LecternMenu lectern(StationView view) {
        MenuAccess menu = view == null ? null : view.menu();
        return menu != null && menu.handle() instanceof LecternMenu lectern ? lectern : null;
    }
}
