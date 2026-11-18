package com.pockyl.handson_vanilla.client;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;

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

    public static void init(Path configDir) {
        VanillaConfig.load(configDir);
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
        // A recipe viewer's page opened by its key over an item at a station keeps the station; leaving it is back at the station.
        DiegesisClient.registerChildScreen(screen -> {
            String name = screen.getClass().getName();
            return name.startsWith("mezz.jei.") || name.startsWith("dev.emi.emi.") || name.startsWith("me.shedaniel.rei.");
        });
    }
}
