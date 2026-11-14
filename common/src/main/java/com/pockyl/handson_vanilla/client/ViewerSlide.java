package com.pockyl.handson_vanilla.client;

import net.minecraft.client.gui.screens.Screen;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_vanilla.HandsOnVanilla;

/**
 * A recipe viewer's list (JEI, EMI) slides in from the right edge with the camera as it flies to a station of this mod, from the click
 * on, and out again as it flies back: its drawing is moved right by what is left of the flight.
 */
public final class ViewerSlide {
    private ViewerSlide() {
    }

    /** How far right to draw what viewers draw over the station's screen now, GUI pixels; 0 when in place or elsewhere. */
    public static float offset(Screen screen) {
        boolean ours = DiegesisClient.isStationScreen(screen) && DiegesisClient.focusedStation()
                .map(view -> view.type().id().getNamespace().equals(HandsOnVanilla.MOD_ID)).orElse(false);
        float arrival = DiegesisClient.arrival();
        if (!ours || arrival >= 1.0F) {
            return 0.0F;
        }
        float wide = Math.max(0.0F, screen.width - StationHover.rightEdge());
        return (1.0F - arrival) * wide;
    }
}
