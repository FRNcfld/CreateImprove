package com.molox.createimp.mixin;

import com.molox.createimp.client.LinkedControllerTextScreenBridge;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSimiContainerScreen.class, remap = false)
public abstract class MixinAbstractSimiContainerScreenControllerText {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createimp$controllerTextClick(double x, double y, int button,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof LinkedControllerTextScreenBridge bridge
                && bridge.createimp$handleTextFrequencyClick(x, y, button)) {
            cir.setReturnValue(true);
        }
    }
}
