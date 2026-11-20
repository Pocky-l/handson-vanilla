package com.pockyl.handson_vanilla.client;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.MenuBinding;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.nio.file.Path;

/**
 * The stations, registered on the client only: each one opens the block's own vanilla menu on the server and works it
 * by hand, so nothing is needed on the server.
 */
public final class HandsOnVanillaClient {
    public static final String GRID = "grid";
    public static final String RESULT = "result";
    private static final net.minecraft.resources.ResourceLocation VISUAL_WORKBENCH =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("visualworkbench", "crafting");

    private HandsOnVanillaClient() {
    }

    /** The loader's way to apply a model's GUI transform (Forge: a model may give another one for the GUI). */
    public static void setGuiModel(java.util.function.BiFunction<net.minecraft.client.resources.model.BakedModel,
            com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.resources.model.BakedModel> how) {
        RecipeBook.guiModel = how;
    }

    /**
     * The enchanting table's runes as scene particles: one style a letter of vanilla's standard galactic alphabet (its own sprites,
     * so a pack's glyphs are used), pale violet as the glyphs drifting to a vanilla table, gone as they reach the item.
     */
    static final java.util.List<net.minecraft.resources.ResourceLocation> GLYPHS = java.util.stream.IntStream.range(0, 8)
            .mapToObj(n -> HandsOnVanilla.id("glyph_" + n)).toList();
    /** The same runes crumbling off a page: slower, rising and swaying, fading as they shrink. */
    static final java.util.List<net.minecraft.resources.ResourceLocation> DRIFTING_GLYPHS = java.util.stream.IntStream.range(0, 8)
            .mapToObj(n -> HandsOnVanilla.id("drifting_glyph_" + n)).toList();
    private static final String GLYPH_LETTERS = "aeklqrvz";

    @SuppressWarnings("removal")
    private static void registerGlyphs() {
        for (int n = 0; n < GLYPHS.size(); n++) {
            net.minecraft.resources.ResourceLocation sprite = net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                    "sga_" + GLYPH_LETTERS.charAt(n));
            DiegesisClient.registerParticle(GLYPHS.get(n), com.pockyl.diegesis.api.client.ParticleStyle.builder(sprite)
                    .color(0xFFEDE6FF).size(1.1F, 1.5F).life(EnchantingTableBehavior.GLYPH_FLIGHT, EnchantingTableBehavior.GLYPH_FLIGHT)
                    .drag(0.0F).emissive().grow(0.6F).build());
            DiegesisClient.registerParticle(DRIFTING_GLYPHS.get(n), com.pockyl.diegesis.api.client.ParticleStyle.builder(sprite)
                    .color(0xFFB98EFF).size(0.8F, 1.2F).life(1.0F, 1.6F).gravity(-2.0F).drag(1.4F).wobble(0.5F).emissive()
                    .grow(0.4F).fadeIn(0.1F).build());
        }
    }

    public static void init(Path configDir) {
        VanillaConfig.load(configDir);
        registerGlyphs();
        Diegesis.station(HandsOnVanilla.id("crafting_table"))
                .blocks(Blocks.CRAFTING_TABLE)
                .orientation(StationType.Orientation.PLAYER)
                // The server's own reach for a menu: a use from farther opens nothing.
                .reach(4.5)
                .schema(StationSchema.builder().slots(GRID, 9).output(RESULT).build())
                // Visual Workbench gives the table a menu of its own, vanilla's with the grid kept in the table: the same slots.
                .menu(MenuBinding.of(MenuType.CRAFTING).alsoAccepts(VISUAL_WORKBENCH).output(RESULT, 0).slots(GRID, 1, 9).build())
                .openWhen((player, pos, state) -> VanillaConfig.craftingTable())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("crafting_table"), view -> new CraftingTableBehavior());
        for (FurnaceKind kind : FurnaceKind.values()) {
            Diegesis.station(HandsOnVanilla.id(kind.id()))
                    .blocks(kind.block())
                    .orientation(StationType.Orientation.BLOCK)
                    .reach(4.5)
                    // Swapped for the scene's copy while worked at, so its doors can swing open.
                    .hidesBlock()
                    // The result is a slot like the others: it stands in the mouth and takes every vanilla click.
                    .schema(StationSchema.builder().slots(FurnaceBehavior.INPUT, 1).slots(FurnaceBehavior.FUEL, 1)
                            .slots(FurnaceBehavior.RESULT, 1).build())
                    .menu(MenuBinding.of(kind.menu()).slots(FurnaceBehavior.INPUT, 0, 1).slots(FurnaceBehavior.FUEL, 1, 1)
                            .slots(FurnaceBehavior.RESULT, 2, 1).build())
                    .openWhen((player, pos, state) -> kind.on())
                    .register();
            DiegesisClient.registerBehavior(HandsOnVanilla.id(kind.id()), view -> new FurnaceBehavior(kind));
        }
        Diegesis.station(HandsOnVanilla.id("stonecutter"))
                .blocks(Blocks.STONECUTTER)
                // From whichever side the player comes; the behaviour reads which way the saw runs.
                .orientation(StationType.Orientation.PLAYER)
                .reach(4.5)
                // The stone is a slot; what it is cut into is the menu's result, picked in the book by the menu's buttons.
                .schema(StationSchema.builder().slots(StonecutterBehavior.INPUT, 1).build())
                .menu(MenuBinding.of(MenuType.STONECUTTER).slots(StonecutterBehavior.INPUT, 0, 1).build())
                .openWhen((player, pos, state) -> VanillaConfig.stonecutter())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("stonecutter"), view -> new StonecutterBehavior());
        Diegesis.station(HandsOnVanilla.id("smithing_table"))
                .blocks(Blocks.SMITHING_TABLE)
                .orientation(StationType.Orientation.PLAYER)
                .reach(4.5)
                // Swapped for the scene's copy while worked at, so its hammer can come off the side.
                .hidesBlock()
                // Template, gear and material are slots; the result is taken by the hammer's blow (the behaviour's click on slot 3).
                .schema(StationSchema.builder().slots(SmithingTableBehavior.TEMPLATE, 1).slots(SmithingTableBehavior.BASE, 1)
                        .slots(SmithingTableBehavior.ADDITION, 1).build())
                .menu(MenuBinding.of(MenuType.SMITHING).slots(SmithingTableBehavior.TEMPLATE, 0, 1).slots(SmithingTableBehavior.BASE, 1, 1)
                        .slots(SmithingTableBehavior.ADDITION, 2, 1).build())
                .openWhen((player, pos, state) -> VanillaConfig.smithingTable())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("smithing_table"), view -> new SmithingTableBehavior());
        Diegesis.station(HandsOnVanilla.id("grindstone"))
                .blocks(Blocks.GRINDSTONE)
                // From whichever side the player comes; the behaviour turns the legs and the wheel as the block stands.
                .orientation(StationType.Orientation.PLAYER)
                .reach(4.5)
                // Swapped for the scene's legs and wheel while worked at, so the wheel can turn.
                .hidesBlock()
                // The two stacks are slots; the result is taken at the grind (the behaviour's click on slot 2).
                .schema(StationSchema.builder().slots(GrindstoneBehavior.INPUT, 2).build())
                .menu(MenuBinding.of(MenuType.GRINDSTONE).slots(GrindstoneBehavior.INPUT, 0, 2).build())
                .openWhen((player, pos, state) -> VanillaConfig.grindstone())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("grindstone"), view -> new GrindstoneBehavior());
        Diegesis.station(HandsOnVanilla.id("brewing_stand"))
                .blocks(Blocks.BREWING_STAND)
                // From whichever side the player comes; the behaviour turns the stand and its places with the scene.
                .orientation(StationType.Orientation.PLAYER)
                .reach(4.5)
                // Swapped for the stand without bottles while worked at: the bottles are the scene's slots.
                .hidesBlock()
                .schema(StationSchema.builder().slots(BrewingStandBehavior.BOTTLES, 3).slots(BrewingStandBehavior.INGREDIENT, 1)
                        .slots(BrewingStandBehavior.FUEL, 1).build())
                .menu(MenuBinding.of(MenuType.BREWING_STAND).slots(BrewingStandBehavior.BOTTLES, 0, 3)
                        .slots(BrewingStandBehavior.INGREDIENT, 3, 1).slots(BrewingStandBehavior.FUEL, 4, 1).build())
                .openWhen((player, pos, state) -> VanillaConfig.brewingStand())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("brewing_stand"), view -> new BrewingStandBehavior());
        Diegesis.station(HandsOnVanilla.id("anvil"))
                .blocks(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)
                // From whichever side the player comes: the pieces lie across the face either way.
                .orientation(StationType.Orientation.PLAYER)
                .reach(4.5)
                // The two pieces are slots; the result is taken at the hammer's blow (the behaviour's click on slot 2).
                .schema(StationSchema.builder().slots(AnvilBehavior.INPUTS, 2).build())
                .menu(MenuBinding.of(MenuType.ANVIL).slots(AnvilBehavior.INPUTS, 0, 2).build())
                .openWhen((player, pos, state) -> VanillaConfig.anvil())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("anvil"), view -> new AnvilBehavior());
        Diegesis.station(HandsOnVanilla.id("enchanting_table"))
                .blocks(Blocks.ENCHANTING_TABLE)
                // Alike from every side: the camera comes from where the player stands, not from the nearest side.
                .orientation(StationType.Orientation.PLAYER_FREE)
                .reach(4.5)
                // Its book comes to the camera and goes back: the scene stands in for the block meanwhile.
                .hidesBlock()
                // The item and the lapis are slots; an offer is taken by the menu's button, a click on its row of the book.
                .schema(StationSchema.builder().slots(EnchantingTableBehavior.ITEM, 1).slots(EnchantingTableBehavior.LAPIS, 1).build())
                .menu(MenuBinding.of(MenuType.ENCHANTMENT).slots(EnchantingTableBehavior.ITEM, 0, 1)
                        .slots(EnchantingTableBehavior.LAPIS, 1, 1).build())
                .openWhen((player, pos, state) -> VanillaConfig.enchantingTable())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("enchanting_table"), view -> new EnchantingTableBehavior());
        Diegesis.station(HandsOnVanilla.id("lectern"))
                .blocks(Blocks.LECTERN)
                // Read from the side the book faces, wherever the player stands.
                .orientation(StationType.Orientation.BLOCK)
                .reach(4.5)
                // The book's slot only lets the behaviour read the stack: no node shows it, vanilla's screen never offers it.
                .schema(StationSchema.builder().slots(LecternBehavior.BOOK, 1).build())
                .menu(MenuBinding.of(MenuType.LECTERN).slots(LecternBehavior.BOOK, 0, 1).build())
                // Without a book a lectern opens nothing: the book in the hand is laid on it.
                .openWhen((player, pos, state) -> VanillaConfig.lectern() && state.getValue(LecternBlock.HAS_BOOK))
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("lectern"), view -> new LecternBehavior());
        Diegesis.station(HandsOnVanilla.id("loom"))
                .blocks(Blocks.LOOM)
                .orientation(StationType.Orientation.PLAYER)
                .reach(4.5)
                // Banner, dye and pattern are slots; the result is the behaviour's, taken in the middle of the shuttle's pass.
                .schema(StationSchema.builder().slots(LoomBehavior.BANNER, 1).slots(LoomBehavior.DYE, 1)
                        .slots(LoomBehavior.PATTERN, 1).build())
                .menu(MenuBinding.of(MenuType.LOOM).slots(LoomBehavior.BANNER, 0, 1).slots(LoomBehavior.DYE, 1, 1)
                        .slots(LoomBehavior.PATTERN, 2, 1).build())
                .openWhen((player, pos, state) -> VanillaConfig.loom())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("loom"), view -> new LoomBehavior());
        Diegesis.station(HandsOnVanilla.id("cartography_table"))
                .blocks(Blocks.CARTOGRAPHY_TABLE)
                .orientation(StationType.Orientation.PLAYER)
                .reach(4.5)
                // The map and the piece beside it are slots; the result is the behaviour's sheet hovering over the table.
                .schema(StationSchema.builder().slots(CartographyTableBehavior.MAP, 1).slots(CartographyTableBehavior.EXTRA, 1).build())
                .menu(MenuBinding.of(MenuType.CARTOGRAPHY_TABLE).slots(CartographyTableBehavior.MAP, 0, 1)
                        .slots(CartographyTableBehavior.EXTRA, 1, 1).build())
                .openWhen((player, pos, state) -> VanillaConfig.cartographyTable())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("cartography_table"), view -> new CartographyTableBehavior());
        // A recipe viewer's page opened by its key over an item at a station keeps the station; leaving it is back at the station.
        DiegesisClient.registerChildScreen(screen -> {
            String name = screen.getClass().getName();
            return name.startsWith("mezz.jei.") || name.startsWith("dev.emi.emi.") || name.startsWith("me.shedaniel.rei.");
        });
    }
}
