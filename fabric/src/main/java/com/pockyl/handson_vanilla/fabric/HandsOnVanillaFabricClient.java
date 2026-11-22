package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.client.HandsOnVanillaClient;
import com.pockyl.handson_vanilla.client.ViewerSlide;

/** Fabric entry point. The mod works on the client only; on a server it does nothing. */
public final class HandsOnVanillaFabricClient implements ClientModInitializer {
    private static final Identifier BEFORE_VIEWERS = HandsOnVanilla.id("viewer_slide_begin");
    private static final Identifier AFTER_VIEWERS = HandsOnVanilla.id("viewer_slide_end");

    /** Whether this frame's drawing after the screen was moved for the viewers' slide, to be put back. */
    private static boolean slid;

    @Override
    public void onInitializeClient() {
        HandsOnVanillaClient.init(FabricLoader.getInstance().getConfigDir());
        // After the config: the furnace's models come only while its station is on.
        FurnaceModels.register();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (DiegesisClient.isStationScreen(screen)) {
                slideViewers(screen);
            }
        });
    }

    /**
     * A recipe viewer draws its list after the screen (JEI through this event): moved right by what is left of the slide
     * ({@link ViewerSlide}), around everybody's drawing. The screen's events are made anew each time it is set up.
     */
    private static void slideViewers(Screen screen) {
        Event<ScreenEvents.AfterExtract> afterRender = ScreenEvents.afterExtract(screen);
        afterRender.addPhaseOrdering(BEFORE_VIEWERS, Event.DEFAULT_PHASE);
        afterRender.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_VIEWERS);
        afterRender.register(BEFORE_VIEWERS, (shown, graphics, mouseX, mouseY, partialTick) -> {
            float offset = ViewerSlide.offset(shown);
            if (offset > 0.5F) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(offset, 0.0F);
                slid = true;
            }
        });
        afterRender.register(AFTER_VIEWERS, (shown, graphics, mouseX, mouseY, partialTick) -> {
            if (slid) {
                slid = false;
                graphics.pose().popMatrix();
            }
        });
    }
}
