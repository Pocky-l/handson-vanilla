package com.pockyl.handson_vanilla.forge;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import com.pockyl.handson_vanilla.client.ViewerSlide;

import com.pockyl.handson_vanilla.client.HandsOnVanillaClient;

/** Client setup on Forge. */
final class ForgeClient {
    private ForgeClient() {
    }

    /** Whether this frame's drawing after the screen was moved for the viewers' slide, to be put back. */
    private static boolean slid;

    static void init() {
        // A recipe viewer draws its list after the screen: moved right by what is left of the slide (ViewerSlide).
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.Render.Post event) -> {
            float offset = ViewerSlide.offset(event.getScreen());
            if (offset > 0.5F) {
                event.getGuiGraphics().pose().pushPose();
                event.getGuiGraphics().pose().translate(offset, 0.0F, 0.0F);
                slid = true;
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Post event) -> {
            if (slid) {
                slid = false;
                event.getGuiGraphics().pose().popPose();
            }
        });
        HandsOnVanillaClient.init(FMLPaths.CONFIGDIR.get());
        // After the config: the furnace's models come only while its station is on.
        FMLJavaModLoadingContext.get().getModEventBus().addListener(FurnacePack::add);
        // Forge's own GUI transform: a model with separate transforms gives its GUI model here (Shadow Drop's icons, GH #1 of
        // Hands-On Delight).
        HandsOnVanillaClient.setGuiModel((model, poseStack) -> model.applyTransform(
                net.minecraft.world.item.ItemDisplayContext.GUI, poseStack, false));
    }
}
