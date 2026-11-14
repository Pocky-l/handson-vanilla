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
    @SuppressWarnings("removal")
    private static final net.minecraft.resources.ResourceLocation VISUAL_WORKBENCH =
            new net.minecraft.resources.ResourceLocation("visualworkbench", "crafting");

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
        Diegesis.station(HandsOnVanilla.id("furnace"))
                .blocks(Blocks.FURNACE)
                .orientation(StationType.Orientation.BLOCK)
                .reach(4.5)
                // Swapped for the scene's copy while worked at, so its doors can swing open.
                .hidesBlock()
                // The result is a slot like the others: it stands in the mouth and takes every vanilla click.
                .schema(StationSchema.builder().slots(FurnaceBehavior.INPUT, 1).slots(FurnaceBehavior.FUEL, 1)
                        .slots(FurnaceBehavior.RESULT, 1).build())
                .menu(MenuBinding.of(MenuType.FURNACE).slots(FurnaceBehavior.INPUT, 0, 1).slots(FurnaceBehavior.FUEL, 1, 1)
                        .slots(FurnaceBehavior.RESULT, 2, 1).build())
                .openWhen((player, pos, state) -> VanillaConfig.furnace())
                .register();
        DiegesisClient.registerBehavior(HandsOnVanilla.id("furnace"), view -> new FurnaceBehavior());
        // A recipe viewer's page opened by its key over an item at a station keeps the station; leaving it is back at the station.
        DiegesisClient.registerChildScreen(screen -> {
            String name = screen.getClass().getName();
            return name.startsWith("mezz.jei.") || name.startsWith("dev.emi.emi.") || name.startsWith("me.shedaniel.rei.");
        });
    }
}
