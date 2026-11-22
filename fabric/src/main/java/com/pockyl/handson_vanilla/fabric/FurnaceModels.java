package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.client.RemodelledBlocks;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The furnace and the smoker in the world are this mod's: hollows behind doors where the station's slots stand, the fire inside.
 * Their block states and models lie under Minecraft's own names, where a resource pack that remodels them (or only names other
 * models in a block state) would win, and the station would open on a block whose hollows are not where its slots, flames and doors
 * are. So the block states and models are read from this mod's own copies ({@code furnace_pack}, the files Forge serves as a pack
 * above all others); a pack's textures still apply to the models' outside. Each block only while its station is on: turned off, it
 * is vanilla's, or the resource pack's, again. Since Minecraft 26.x a model's quads are drawn cut out by their texture's alpha, so
 * the doors' and the grate's holes need nothing more.
 */
final class FurnaceModels {
    private static final String FOLDER = "furnace_pack/assets/minecraft/";

    private FurnaceModels() {
    }

    static void register() {
        List<RemodelledBlocks.Entry> entries = RemodelledBlocks.all().stream().filter(entry -> entry.on().getAsBoolean()).toList();
        if (entries.isEmpty()) {
            return;
        }
        ModelLoadingPlugin.register(context -> {
            Map<Identifier, UnbakedModel> models = new HashMap<>();
            for (RemodelledBlocks.Entry entry : entries) {
                for (String name : entry.models()) {
                    readModel(FOLDER + "models/block/" + name + ".json")
                            .ifPresent(model -> models.put(Identifier.withDefaultNamespace("block/" + name), model));
                }
            }
            // The models by Minecraft's names: the item, which names the block's model, follows, as with the pack.
            context.modifyModelOnLoad().register((model, loading) -> models.getOrDefault(loading.id(), model));
            for (RemodelledBlocks.Entry entry : entries) {
                Block block = entry.block();
                // Read first: a file that cannot be read leaves the block its usual models instead of the missing one.
                Optional<Map<BlockState, BlockStateModel.UnbakedRoot>> states = readBlockState(block,
                        FOLDER + "blockstates/" + entry.id() + ".json");
                if (states.isEmpty()) {
                    continue;
                }
                try {
                    context.registerBlockStateResolver(block, resolver -> states.get().forEach(resolver::setModel));
                } catch (IllegalArgumentException e) {
                    // Another mod already decides how the block looks: its choice stands.
                    HandsOnVanilla.LOGGER.warn("The {}'s models are resolved by another mod; this mod's {} is not used", entry.id(),
                            entry.id(), e);
                }
            }
        });
    }

    private static Optional<UnbakedModel> readModel(String file) {
        try (Reader reader = Files.newBufferedReader(find(file))) {
            return Optional.of(CuboidModel.fromStream(reader));
        } catch (IOException | RuntimeException e) {
            HandsOnVanilla.LOGGER.error("Cannot read {}", file, e);
            return Optional.empty();
        }
    }

    /** The block state file, bound to the block's properties: a model for each of its states. */
    private static Optional<Map<BlockState, BlockStateModel.UnbakedRoot>> readBlockState(Block block, String file) {
        try (Reader reader = Files.newBufferedReader(find(file))) {
            BlockStateModelDispatcher definition = BlockStateModelDispatcher.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow();
            return Optional.of(definition.instantiate(block.getStateDefinition(), () -> file));
        } catch (IOException | RuntimeException e) {
            HandsOnVanilla.LOGGER.error("Cannot read {}", file, e);
            return Optional.empty();
        }
    }

    private static Path find(String file) throws IOException {
        return FabricLoader.getInstance().getModContainer(HandsOnVanilla.MOD_ID).flatMap(mod -> mod.findPath(file))
                .orElseThrow(() -> new IOException(file + " is missing from the mod"));
    }
}
