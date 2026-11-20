package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

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
 * above all others); a pack's textures still apply to the models' outside. The cartography table's tray of felt comes the same way
 * ({@link RemodelledBlocks}). Each block only while its station is on: turned off, it is vanilla's, or the resource pack's, again.
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
        for (RemodelledBlocks.Entry entry : entries) {
            if (entry.cutout()) {
                // The doors and the grate have holes: the model is drawn cut out (Forge reads its render_type).
                BlockRenderLayerMap.INSTANCE.putBlock(entry.block(), RenderType.cutout());
            }
        }
        ModelLoadingPlugin.register(context -> {
            Map<ResourceLocation, BlockModel> models = new HashMap<>();
            for (RemodelledBlocks.Entry entry : entries) {
                for (String name : entry.models()) {
                    ResourceLocation id = ResourceLocation.withDefaultNamespace("block/" + name);
                    readModel(FOLDER + "models/block/" + name + ".json").ifPresent(model -> {
                        model.name = id.toString();
                        models.put(id, model);
                    });
                }
            }
            context.resolveModel().register(resolver -> models.get(resolver.id()));
            for (RemodelledBlocks.Entry entry : entries) {
                Block block = entry.block();
                Optional<BlockModelDefinition> definition = readBlockState(block, FOLDER + "blockstates/" + entry.id() + ".json");
                if (definition.isEmpty()) {
                    continue;
                }
                try {
                    context.registerBlockStateResolver(block, resolver -> {
                        Map<String, MultiVariant> variants = definition.get().getVariants();
                        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                            UnbakedModel model = variants.entrySet().stream().filter(variant -> matches(state, variant.getKey()))
                                    .<UnbakedModel>map(Map.Entry::getValue).findFirst()
                                    .orElseGet(() -> resolver.getOrLoadModel(ModelBakery.MISSING_MODEL_LOCATION));
                            resolver.setModel(state, model);
                        }
                    });
                } catch (IllegalArgumentException e) {
                    // Another mod already decides how the block looks: its choice stands.
                    HandsOnVanilla.LOGGER.warn("The {}'s models are resolved by another mod; this mod's {} is not used", entry.id(),
                            entry.id(), e);
                }
            }
        });
    }

    private static Optional<BlockModel> readModel(String file) {
        try (Reader reader = Files.newBufferedReader(find(file))) {
            return Optional.of(BlockModel.fromStream(reader));
        } catch (IOException | RuntimeException e) {
            HandsOnVanilla.LOGGER.error("Cannot read {}", file, e);
            return Optional.empty();
        }
    }

    private static Optional<BlockModelDefinition> readBlockState(Block block, String file) {
        try (Reader reader = Files.newBufferedReader(find(file))) {
            BlockModelDefinition.Context context = new BlockModelDefinition.Context();
            context.setDefinition(block.getStateDefinition());
            return Optional.of(BlockModelDefinition.fromStream(context, reader));
        } catch (IOException | RuntimeException e) {
            HandsOnVanilla.LOGGER.error("Cannot read {}", file, e);
            return Optional.empty();
        }
    }

    private static Path find(String file) throws IOException {
        return FabricLoader.getInstance().getModContainer(HandsOnVanilla.MOD_ID).flatMap(mod -> mod.findPath(file))
                .orElseThrow(() -> new IOException(file + " is missing from the mod"));
    }

    /** Whether a block state file's variant key ({@code facing=east,lit=false}) names this state. */
    private static boolean matches(BlockState state, String key) {
        if (key.isEmpty()) {
            return true;
        }
        for (String pair : key.split(",")) {
            String[] parts = pair.split("=", 2);
            Property<?> property = state.getBlock().getStateDefinition().getProperty(parts[0]);
            if (property == null || parts.length < 2) {
                return false;
            }
            Optional<?> value = property.getValue(parts[1]);
            if (value.isEmpty() || !value.get().equals(state.getValue(property))) {
                return false;
            }
        }
        return true;
    }
}
