package com.molox.createimp.mixin;

import com.molox.createimp.client.LinkedControllerTextScreenBridge;
import com.molox.createimp.item.LinkedControllerTextFrequencies;
import com.molox.createimp.network.SaveLinkedControllerTextPacket;
import com.molox.createimp.screen.LabeledRedstoneLinkScreen;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerMenu;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerScreen;
import com.simibubi.create.foundation.utility.ControlsUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** The two original ghost slots remain the visual anchors for each button's text channel. */
@Mixin(value = LinkedControllerScreen.class, remap = false)
public abstract class MixinLinkedControllerTextScreen implements LinkedControllerTextScreenBridge {
    @Unique
    private LinkedControllerScreen createimp$self() {
        return (LinkedControllerScreen) (Object) this;
    }

    @Override
    public boolean createimp$handleTextFrequencyClick(double x, double y, int mouseButton) {
        LinkedControllerScreen screen = createimp$self();
        LinkedControllerMenu menu = screen.getMenu();
        for (int i = 0; i < 12; i++) {
            Slot slot = menu.getSlot(36 + i);
            int slotX = screen.getGuiLeft() + slot.x;
            int slotY = screen.getGuiTop() + slot.y;
            if (x < slotX || x >= slotX + 18 || y < slotY || y >= slotY + 18) continue;
            int button = i / 2;
            boolean configured = LinkedControllerTextFrequencies.has(menu.contentHolder, button);
            if (configured && mouseButton == 1) {
                LinkedControllerTextFrequencies.set(menu.contentHolder, button, "");
                PacketDistributor.sendToServer(new SaveLinkedControllerTextPacket(menu.containerId, button, "", true));
                return true;
            }
            if ((configured && mouseButton == 0) || (!configured && mouseButton == 2)) {
                String previous = LinkedControllerTextFrequencies.get(menu.contentHolder, button);
                LabeledRedstoneLinkScreen.openForController(previous, text -> {
                    if (Minecraft.getInstance().player == null
                            || Minecraft.getInstance().player.containerMenu != menu) return;
                    LinkedControllerTextFrequencies.set(menu.contentHolder, button, text);
                    for (int pair = 0; pair < 2; pair++) {
                        int ghost = button * 2 + pair;
                        menu.ghostInventory.setStackInSlot(ghost, ItemStack.EMPTY);
                        menu.getSlot(36 + ghost).setChanged();
                    }
                    PacketDistributor.sendToServer(new SaveLinkedControllerTextPacket(menu.containerId, button, text, false));
                }, () -> {}, () -> {
                    if (Minecraft.getInstance().player != null
                            && Minecraft.getInstance().player.containerMenu == menu) {
                        Minecraft.getInstance().setScreen(screen);
                    } else {
                        Minecraft.getInstance().setScreen(null);
                    }
                });
                return true;
            }
            return configured;
        }
        return false;
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void createimp$shadeTextSlots(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        LinkedControllerScreen screen = createimp$self();
        // Render the disabled-slot mask above slot contents (Z=100), below tooltips.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        for (int i = 0; i < 12; i++) {
            if (!LinkedControllerTextFrequencies.has(screen.getMenu().contentHolder, i / 2)) continue;
            Slot slot = screen.getMenu().getSlot(36 + i);
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xB3000000);
        }
        graphics.pose().popPose();
    }

    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void createimp$textSlotTooltip(List<Component> original, int ghostIndex,
                                           CallbackInfoReturnable<List<Component>> cir) {
        if (ghostIndex < 0 || ghostIndex >= 12) return;
        String text = LinkedControllerTextFrequencies.get(createimp$self().getMenu().contentHolder, ghostIndex / 2);
        if (text.isBlank()) return;
        Component binding = ControlsUtil.getControls().get(ghostIndex / 2).getTranslatedKeyMessage();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("createimp.gui.linked_controller.text_binding", binding, text)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("createimp.gui.linked_controller.edit_delete")
                .withStyle(ChatFormatting.GRAY));
        cir.setReturnValue(tooltip);
    }

    @Inject(method = "addToTooltip", at = @At("RETURN"), cancellable = true)
    private void createimp$showMiddleClickHint(List<Component> original, int ghostIndex,
                                                CallbackInfoReturnable<List<Component>> cir) {
        if (ghostIndex < 0 || ghostIndex >= 12
                || LinkedControllerTextFrequencies.has(createimp$self().getMenu().contentHolder, ghostIndex / 2)) return;
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        tooltip.add(Component.translatable("createimp.gui.linked_controller.middle_click")
                .withStyle(ChatFormatting.GRAY));
        cir.setReturnValue(tooltip);
    }
}
