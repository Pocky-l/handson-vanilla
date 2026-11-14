package com.pockyl.handson_vanilla.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_vanilla.client.StationHover;

/** EMI at a station: its keys (recipes, uses, favourite) work over the item under the cursor. */
@EmiEntrypoint
public final class VanillaEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericStackProvider((screen, mouseX, mouseY) -> {
            if (!DiegesisClient.isStationScreen(screen)) {
                return EmiStackInteraction.EMPTY;
            }
            ItemStack stack = StationHover.stack();
            return stack.isEmpty() ? EmiStackInteraction.EMPTY : new EmiStackInteraction(EmiStack.of(stack));
        });
    }
}
