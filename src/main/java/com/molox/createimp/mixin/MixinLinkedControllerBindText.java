package com.molox.createimp.mixin;

import com.molox.createimp.item.LinkedControllerTextFrequencies;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerBindPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Create's world-link binding bypasses the ghost menu, so it must respect text-mode pairs too. */
@Mixin(value = LinkedControllerBindPacket.class, remap = false)
public abstract class MixinLinkedControllerBindText {
    @Shadow @Final private int button;

    @Inject(method = "handleItem", at = @At("HEAD"), cancellable = true)
    private void createimp$skipBoundTextButton(ServerPlayer player, ItemStack controller, CallbackInfo ci) {
        if (button < 0 || button >= LinkedControllerTextFrequencies.BUTTONS
                || LinkedControllerTextFrequencies.has(controller, button)) ci.cancel();
    }
}
