package com.pockyl.handson_vanilla.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.client.VanillaConfig;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
        PackLocationInfo location = new PackLocationInfo(ID, Component.literal("Hands-On Vanilla: furnace"), PackSource.BUILT_IN,
                Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public OnlyTheFurnace openPrimary(PackLocationInfo info) {
                return new OnlyTheFurnace(info, root);
            }

            @Override
            public OnlyTheFurnace openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new OnlyTheFurnace(info, root);
            }
        };
        // The folder has no pack.mcmeta of its own: the pack's metadata is made here.
        Pack.Metadata metadata = new Pack.Metadata(location.title(), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        Pack pack = new Pack(location, resources, metadata, new PackSelectionConfig(true, Pack.Position.TOP, true));
        event.addRepositorySource(consumer -> consumer.accept(pack.hidden()));
    }

    /** The pack's files: client resources under Minecraft's name only. */
    private static final class OnlyTheFurnace extends PathPackResources {
        OnlyTheFurnace(PackLocationInfo info, Path root) {
            super(info, root);
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
