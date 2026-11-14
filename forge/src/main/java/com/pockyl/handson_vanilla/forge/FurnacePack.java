package com.pockyl.handson_vanilla.forge;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.client.VanillaConfig;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

/**
 * The furnace in the world is this mod's furnace: hollows behind grille doors where the station's slots stand, the fire inside.
 * Its block state and models lie under Minecraft's own name, and a resource pack sits above every mod: a pack that remodels the
 * furnace (or only names other models in its block state) would win, and the station would open on a block whose mouth is not
 * where its slots, flames and doors are. So the files come as a hidden built-in pack fixed above all others; a pack's furnace
 * textures still apply to the model's outside. The pack is there only while the furnace station is on: turned off, the furnace is
 * vanilla's, or the resource pack's, again.
 */
final class FurnacePack {
    private static final String ID = HandsOnVanilla.MOD_ID + "_furnace";
    /** The folder of the mod's files that holds the pack's {@code assets}. */
    private static final String FOLDER = "furnace_pack";

    private FurnacePack() {
    }

    static void add(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !VanillaConfig.furnace()) {
            return;
        }
        Path root = ModList.get().getModFileById(HandsOnVanilla.MOD_ID).getFile().getSecureJar().getRootPath().resolve(FOLDER);
        Pack.ResourcesSupplier resources = id -> new OnlyTheFurnace(id, root);
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        Component name = Component.literal("Hands-On Vanilla: furnace");
        Pack.Info info = new Pack.Info(name, format, format, FeatureFlagSet.of(), true);
        Pack pack = Pack.create(ID, name, true, resources, info, PackType.CLIENT_RESOURCES, Pack.Position.TOP, true, PackSource.BUILT_IN);
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /** The pack's files: client resources under Minecraft's name only. */
    private static final class OnlyTheFurnace extends PathPackResources {
        OnlyTheFurnace(String id, Path root) {
            super(id, root, true);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return type == PackType.CLIENT_RESOURCES ? super.getResource(type, location) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type == PackType.CLIENT_RESOURCES) {
                super.listResources(type, namespace, path, output);
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? Set.of(ResourceLocation.DEFAULT_NAMESPACE) : Set.of();
        }
    }
}
