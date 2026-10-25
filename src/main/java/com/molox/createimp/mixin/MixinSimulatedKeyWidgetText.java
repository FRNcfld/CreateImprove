package com.molox.createimp.mixin;

import com.molox.createimp.compat.simulated.client.TypewriterTextGui;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.widgets.KeyWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyWidget.class, remap = false)
public abstract class MixinSimulatedKeyWidgetText {
    @Shadow public int keyNum;
    @Shadow private LinkedTypewriterScreen screen;
    @Shadow abstract Component keyName();

    @Inject(method = "renderHover", at = @At("HEAD"), cancellable = true)
    private void createimp$renderTextHover(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                            CallbackInfo ci) {
        String text = TypewriterTextGui.text(screen, keyNum);
        if (text == null || text.isBlank()) return;
        if (!createimp$isActive(mouseX, mouseY)) return;
        KeyWidget widget = (KeyWidget) (Object) this;
        Component keyName = keyName();
        int keyNameWidth = Minecraft.getInstance().font.width(keyName);
        int bgHeight = 9 + 12 + 20;
        var arrow = dev.simulated_team.simulated.index.SimGUITextures.LINKED_TYPEWRITER_TOOLTIP_ARROW;
        int minWidth = arrow.width + 4 + 20;
        int bgWidth = Math.max(minWidth, keyNameWidth + 8);
        int center = widget.getWidth() / 2;
        int bgX = widget.getX() + center - bgWidth / 2;
        int bgY = widget.getY() - bgHeight - 8;
        // Keep Simulated's original tooltip background/arrow; replace only its two item boxes.
        var bg = dev.simulated_team.simulated.index.SimGUITextures.LINKED_TYPEWRITER_TOOLTIP_BACKGROUND;
        graphics.blitSprite(bg.location, bgX, bgY, 0, bgWidth, bgHeight);
        arrow.render(graphics, widget.getX() + center - arrow.width / 2, widget.getY() - 10);
        graphics.drawString(Minecraft.getInstance().font, keyName.getString(),
                widget.getX() + center - keyNameWidth / 2 - 1, widget.getY() - 8 - 9 - 2, 0x1E1E1E, false);
        TypewriterTextGui.render(graphics, widget.getX() + center - 18, bgY + 4, text);
        ci.cancel();
    }

    @Unique private boolean createimp$isActive(int mouseX, int mouseY) {
        return ((KeyWidget) (Object) this).isMouseOver(mouseX, mouseY);
    }
}

