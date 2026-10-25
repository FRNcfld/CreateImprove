package com.molox.createimp.mixin;

import com.molox.createimp.block.labeled_redstone_link.LinkedControllerTextNetwork;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LinkedControllerServerHandler.class, remap = false)
public abstract class MixinLinkedControllerServerTextTick {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void createimp$tickTextInputs(LevelAccessor level, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) LinkedControllerTextNetwork.tick(serverLevel);
    }
}
