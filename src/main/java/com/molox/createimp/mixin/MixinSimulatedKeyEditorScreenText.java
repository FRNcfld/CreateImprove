package com.molox.createimp.mixin;

import com.molox.createimp.compat.simulated.client.TypewriterTextGui;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.KeyEditorScreen;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyEditorScreen.class, remap = false)
public abstract class MixinSimulatedKeyEditorScreenText {
    @Shadow private LinkedTypewriterScreen parentScreen;
    @Shadow private net.createmod.catnip.animation.LerpedFloat lerpedScroll;

    @Inject(method = "render", at = @At("TAIL"))
    private void createimp$renderTextBindings(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                               com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        LinkedTypewriterScreen screen = parentScreen;
        if (screen.modifier.modifying) return;
        int index = 0;
        for (LinkedTypewriterEntries.KeyboardEntry entry : screen.getNewEntries().getEntries()) {
            String text = TypewriterTextGui.text(screen, entry.glfwKeyCode);
            if (text != null && !text.isBlank()) {
                int rowX = screen.getLeftPos() + 12;
                int rowY = screen.getTopPos() - 15
                        + index * (dev.simulated_team.simulated.index.SimGUITextures.LINKED_TYPEWRITER_KEY_ENTRY.height + 3)
                        - (int) lerpedScroll.getValue(partialTick);
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 300);
                TypewriterTextGui.render(graphics, rowX + 81, rowY + 6, text);
                graphics.pose().popPose();
            }
            index++;
        }
    }
}
