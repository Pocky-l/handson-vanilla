package com.pockyl.handson_vanilla.neoforge;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.handson_vanilla.client.HandsOnVanillaClient;
import com.pockyl.handson_vanilla.client.ViewerSlide;

/** Client setup on NeoForge. */
final class NeoForgeClient {
    /** Whether this frame's drawing after the screen was moved for the viewers' slide, to be put back. */
    private static boolean slid;

    private NeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        // A recipe viewer draws its list after the screen: moved right by what is left of the slide (ViewerSlide). EMI's sidebars
        // are not drawn at a station on this line: EMI has no build above 1.21.1.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.Render.Post event) -> {
            float offset = ViewerSlide.offset(event.getScreen());
            if (offset > 0.5F) {
                event.getGuiGraphics().pose().pushMatrix();
                event.getGuiGraphics().pose().translate(offset, 0.0F);
                slid = true;
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Post event) -> {
            if (slid) {
                slid = false;
                event.getGuiGraphics().pose().popMatrix();
            }
        });
        HandsOnVanillaClient.init(FMLPaths.CONFIGDIR.get());
        // After the config: the furnace's models come only while its station is on.
        modBus.addListener(FurnacePack::add);
    }
}
