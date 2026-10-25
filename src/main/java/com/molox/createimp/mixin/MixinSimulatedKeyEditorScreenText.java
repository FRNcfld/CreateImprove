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
        int rowX = screen.getLeftPos() + 12;
        int baseRowY = screen.getTopPos() - 15;
        int rowStep = dev.simulated_team.simulated.index.SimGUITextures.LINKED_TYPEWRITER_KEY_ENTRY.height + 3;
        graphics.enableScissor(0, screen.getTopPos() - 20,
                screen.width, screen.getTopPos() + 120);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -lerpedScroll.getValue(partialTick), 300);
        for (LinkedTypewriterEntries.KeyboardEntry entry : screen.getNewEntries().getEntries()) {
            String text = TypewriterTextGui.text(screen, entry.glfwKeyCode);
            if (text != null && !text.isBlank()) {
                TypewriterTextGui.renderScrolled(graphics, rowX + 81, baseRowY + index * rowStep + 6,
                        text, (int) -lerpedScroll.getValue(partialTick));
            }
            index++;
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }
}
