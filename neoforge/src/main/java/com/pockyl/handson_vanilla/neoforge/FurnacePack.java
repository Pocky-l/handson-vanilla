package com.pockyl.handson_vanilla.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.JarContentsPackResources;

import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.client.RemodelledBlocks;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The furnace and the smoker in the world are this mod's: hollows behind doors where the station's slots stand, the fire inside.
 * Their block states and models lie under Minecraft's own names, and a resource pack sits above every mod: a pack that remodels a
 * furnace (or only names other models in its block state) would win, and the station would open on a block whose hollows are not
 * where its slots, flames and doors are. So the files come as a hidden built-in pack fixed above all others; a pack's furnace and
 * smoker textures still apply to the models' outside. Each block's files are there only while its station is on
 * ({@link RemodelledBlocks#served}): turned off, it is vanilla's, or the resource pack's, again.
 */
final class FurnacePack {
    private static final String ID = HandsOnVanilla.MOD_ID + "_furnace";
    /** The folder of the mod's files that holds the pack's {@code assets}. */
    private static final String FOLDER = "furnace_pack";

    private FurnacePack() {
    }

    static void add(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !RemodelledBlocks.anyOn()) {
            return;
        }
        JarContents contents = ModList.get().getModFileById(HandsOnVanilla.MOD_ID).getFile().getContents();
        PackLocationInfo location = new PackLocationInfo(ID, Component.literal("Hands-On Vanilla: blocks"), PackSource.BUILT_IN,
                Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public OnlyTheFurnace openMetadata(PackLocationInfo info) {
                return new OnlyTheFurnace(info, contents);
            }

            @Override
            public Stream<PackResources> openResources(PackLocationInfo info, Pack.Metadata metadata) {
                return Stream.of(new OnlyTheFurnace(info, contents));
            }
        };
        // The folder has no pack.mcmeta of its own: the pack's metadata is made here.
        Pack.Metadata metadata = new Pack.Metadata(location.title(), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(),
                false);
        Pack pack = new Pack(location, resources, metadata, new PackSelectionConfig(true, Pack.Position.TOP, true));
        event.addRepositorySource(consumer -> consumer.accept(pack.hidden()));
    }

    /** The pack's files: client resources under Minecraft's name only, of the furnaces whose station is on. */
    private static final class OnlyTheFurnace extends JarContentsPackResources {
        OnlyTheFurnace(PackLocationInfo info, JarContents contents) {
            super(info, contents, FOLDER);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            return type == PackType.CLIENT_RESOURCES && RemodelledBlocks.served(location.getPath()) ? super.getResource(type, location) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type == PackType.CLIENT_RESOURCES) {
                super.listResources(type, namespace, path, (id, resource) -> {
                    if (RemodelledBlocks.served(id.getPath())) {
                        output.accept(id, resource);
                    }
                });
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? Set.of(Identifier.DEFAULT_NAMESPACE) : Set.of();
        }
    }
}
