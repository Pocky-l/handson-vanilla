package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * are. So the block states are read from this mod's own copies ({@code furnace_pack}, the files Forge serves as a pack above all
 * others), each of their {@code minecraft:block/<name>} models taken as the mod's model the pack's file names as its parent; a
 * pack's textures still apply to the models' outside. The cartography table's tray of felt comes the same way
 * ({@link RemodelledBlocks}). Each block only while its station is on: turned off, it is vanilla's, or the resource pack's, again.
 * Since Minecraft 26.x a model's render layer follows its textures' alpha: the doors and the grate are cut out without being told.
 */
final class FurnaceModels {
    private static final String FOLDER = "furnace_pack/assets/minecraft/";
    private static final String VANILLA_MODELS = "minecraft:block/";

    private FurnaceModels() {
    }

    static void register() {
        List<RemodelledBlocks.Entry> entries = RemodelledBlocks.all().stream().filter(entry -> entry.on().getAsBoolean()).toList();
        if (entries.isEmpty()) {
            return;
        }
        Map<Identifier, String> served = new HashMap<>();
        for (RemodelledBlocks.Entry entry : entries) {
            for (String name : entry.models()) {
                served.put(Identifier.withDefaultNamespace("block/" + name), FOLDER + "models/block/" + name + ".json");
            }
        }
        ModelLoadingPlugin.register(context -> {
            // The vanilla model names themselves are the pack's files too, so the furnace held in the hand looks as the block.
            context.modifyModelOnLoad().register((model, loading) -> {
                String file = served.get(loading.id());
                if (file == null) {
                    return model;
                }
                try (Reader reader = Files.newBufferedReader(find(file))) {
                    return CuboidModel.fromStream(reader);
                } catch (IOException | RuntimeException e) {
                    HandsOnVanilla.LOGGER.error("Cannot read {}", file, e);
                    return model;
                }
            });
            for (RemodelledBlocks.Entry entry : entries) {
                Block block = entry.block();
                // Read first: a file that cannot be read leaves the block its usual models instead of the missing one.
                Optional<Map<BlockState, BlockStateModel.UnbakedRoot>> models = read(block, FOLDER + "blockstates/" + entry.id() + ".json");
                if (models.isEmpty()) {
                    continue;
                }
                try {
                    context.registerBlockStateResolver(block, resolver -> models.get().forEach(resolver::setModel));
                } catch (IllegalArgumentException e) {
                    // Another mod already decides how the block looks: its choice stands.
                    HandsOnVanilla.LOGGER.warn("The {}'s models are resolved by another mod; this mod's {} is not used", entry.id(),
                            entry.id(), e);
                }
            }
        });
    }

    /** The block state file, its vanilla model names replaced by the mod's models, bound to the block's properties. */
    private static Optional<Map<BlockState, BlockStateModel.UnbakedRoot>> read(Block block, String file) {
        try {
            JsonElement json = parse(file);
            ownModels(json);
            BlockStateModelDispatcher definition = BlockStateModelDispatcher.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            return Optional.of(definition.instantiate(block.getStateDefinition(), () -> file));
        } catch (IOException | RuntimeException e) {
            HandsOnVanilla.LOGGER.error("Cannot read {}", file, e);
            return Optional.empty();
        }
    }

    /**
     * Every {@code "model": "minecraft:block/<name>"} the pack also serves ({@code models/block/<name>.json}) becomes the model that
     * file names as its parent: the mod's own, where a resource pack cannot replace it.
     */
    private static void ownModels(JsonElement json) throws IOException {
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                ownModels(element);
            }
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if (field.getKey().equals("model") && field.getValue().isJsonPrimitive()
                    && field.getValue().getAsString().startsWith(VANILLA_MODELS)) {
                String name = field.getValue().getAsString().substring(VANILLA_MODELS.length());
                JsonElement served = parse(FOLDER + "models/block/" + name + ".json");
                if (served.isJsonObject() && served.getAsJsonObject().has("parent")) {
                    field.setValue(served.getAsJsonObject().get("parent"));
                }
            } else {
                ownModels(field.getValue());
            }
        }
    }

    private static JsonElement parse(String file) throws IOException {
        try (Reader reader = Files.newBufferedReader(find(file))) {
            return JsonParser.parseReader(reader);
        }
    }

    private static Path find(String file) throws IOException {
        return FabricLoader.getInstance().getModContainer(HandsOnVanilla.MOD_ID).flatMap(mod -> mod.findPath(file))
                .orElseThrow(() -> new IOException(file + " is missing from the mod"));
    }
}
