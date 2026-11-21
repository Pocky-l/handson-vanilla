package com.pockyl.handson_vanilla.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_vanilla.client.EmiSidebar;
import com.pockyl.handson_vanilla.client.StationHover;

/**
 * EMI at a station of this mod: its sidebars beside the station ({@code EmiSidebar} draws them), its keys (recipes, uses,
 * favourite) over the item under the cursor, its "+" filling the station's menu ({@code EmiApiMixin}).
 */
@EmiEntrypoint
public final class VanillaEmiPlugin implements EmiPlugin {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void register(EmiRegistry registry) {
        // EMI works only on screens whose layout it knows, keys included: the station's middle (the table, the book, the hotbar),
        // as JEI is told, so its list sits on the right. Other stations keep EMI off, as before.
        Class<Screen> station = (Class) DiegesisClient.stationScreenClass();
        registry.addScreenBoundsProvider(station, screen -> {
            if (!StationHover.isOurs(screen) || screen.width <= 1 || screen.height <= 1) {
                return Bounds.EMPTY;
            }
            return new Bounds(0, 0, StationHover.viewerMiddle(screen.width), screen.height);
        });
        // EMI's search field under its list, where EmiSidebar puts it at a station: the list keeps clear of it.
        registry.addExclusionArea(station, (screen, areas) -> {
            int[] slot = EmiSidebar.searchSlot(screen);
            if (slot != null) {
                areas.accept(new Bounds(slot[0], slot[1], slot[2], slot[3]));
            }
        });
        registry.addGenericStackProvider((screen, mouseX, mouseY) -> {
            // EMI asks this before its own lists: over them it is theirs.
            if (!StationHover.isOurs(screen) || mouseX >= StationHover.viewerMiddle(screen.width)) {
                return EmiStackInteraction.EMPTY;
            }
            ItemStack stack = StationHover.stack();
            // For EMI's keys only: a click on the station's items is the station's (as EMI leaves a container screen's slots alone).
            return stack.isEmpty() ? EmiStackInteraction.EMPTY : new EmiStackInteraction(EmiStack.of(stack), null, false);
        });
    }
}
