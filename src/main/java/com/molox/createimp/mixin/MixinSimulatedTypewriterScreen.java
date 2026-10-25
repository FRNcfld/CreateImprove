package com.molox.createimp.mixin;

import com.molox.createimp.compat.simulated.client.TypewriterTextGui;
import com.molox.createimp.network.SaveTypewriterTextBindingsPacket;
import com.molox.createimp.screen.LabeledRedstoneLinkScreen;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.EntryModifierScreen;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.KeyEditorScreen;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Extends the existing container screen without opening or closing its menu for the text editor. */
@Mixin(value = LinkedTypewriterScreen.class, remap = false)
public abstract class MixinSimulatedTypewriterScreen {
    @Shadow public EntryModifierScreen modifier;
    @Shadow private KeyEditorScreen keyEditorScreen;
    @Shadow abstract void switchStates(boolean state);
    @Unique private boolean createimp$returningFromTextEditor;

    @Unique private LinkedTypewriterScreen createimp$self() {
        return (LinkedTypewriterScreen) (Object) this;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createimp$openTextEditor(double x, double y, int button, CallbackInfoReturnable<Boolean> result) {
        LinkedTypewriterScreen screen = createimp$self();
        if ((button != 0 && button != 2) || !modifier.modifying || modifier.psuedoEntry == null) return;
        LinkedTypewriterMenuCommon menu = (LinkedTypewriterMenuCommon) screen.getMenu();
        Slot firstSlot = menu.getSlot(menu.slots.size() - 2);
        int frameX = screen.getLeftPos() + firstSlot.x - 1;
        int frameY = screen.getTopPos() + firstSlot.y - 1;
        if (x < frameX || x >= frameX + 36 || y < frameY || y >= frameY + 18) return;
        int key = modifier.psuedoEntry.glfwKeyCode;
        String existing = TypewriterTextGui.text(screen, key);
        if (existing != null && !existing.isBlank()) {
            if (button == 2) {
                TypewriterTextGui.bindings(screen).remove(key);
                modifier.psuedoEntry.first(RedstoneLinkNetworkHandler.Frequency.EMPTY)
                        .second(RedstoneLinkNetworkHandler.Frequency.EMPTY);
                menu.ghostInventory.setStackInSlot(0, ItemStack.EMPTY);
                menu.ghostInventory.setStackInSlot(1, ItemStack.EMPTY);
                result.setReturnValue(true);
                return;
            }
        } else if (button != 2) {
            return;
        }
        String initial = existing;
        createimp$returningFromTextEditor = true;
        LabeledRedstoneLinkScreen.openForController(initial == null ? "" : initial, text -> {
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu != menu
                    || !modifier.modifying || modifier.psuedoEntry == null) return;
            int currentKey = modifier.psuedoEntry.glfwKeyCode;
            if (currentKey < 0) return;
            TypewriterTextGui.bindings(screen).put(currentKey, text);
            // Only a confirmed text save removes the staged item-frequency pair.
            modifier.psuedoEntry.first(RedstoneLinkNetworkHandler.Frequency.EMPTY)
                    .second(RedstoneLinkNetworkHandler.Frequency.EMPTY);
            menu.ghostInventory.setStackInSlot(0, ItemStack.EMPTY);
            menu.ghostInventory.setStackInSlot(1, ItemStack.EMPTY);
        }, () -> {}, () -> {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu == menu) {
                Minecraft.getInstance().setScreen(screen);
            } else {
                createimp$returningFromTextEditor = false;
                Minecraft.getInstance().setScreen(null);
            }
        });
        result.setReturnValue(true);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void createimp$restorePopupAfterModal(CallbackInfo ci) {
        if (!createimp$returningFromTextEditor) return;
        createimp$returningFromTextEditor = false;
        LinkedTypewriterScreen screen = createimp$self();
        if (keyEditorScreen.active) screen.switchScreen(true);
        if (modifier.modifying) {
            screen.addWidget(modifier.cancelEntryWidget);
            screen.addWidget(modifier.confirmationWidget);
            screen.addWidget(modifier.promptWidget);
            switchStates(false);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void createimp$renderTextBindingUi(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                CallbackInfo ci) {
        LinkedTypewriterScreen screen = createimp$self();
        if (modifier.modifying && modifier.psuedoEntry != null) {
            int key = modifier.psuedoEntry.glfwKeyCode;
            String text = TypewriterTextGui.text(screen, key);
            LinkedTypewriterMenuCommon menu = (LinkedTypewriterMenuCommon) screen.getMenu();
            Slot first = menu.getSlot(menu.slots.size() - 2);
            int x = screen.getLeftPos() + first.x - 1;
            int y = screen.getTopPos() + first.y - 1;
            if (text != null && !text.isBlank()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 300);
                TypewriterTextGui.render(graphics, x, y, text);
                graphics.pose().popPose();
                if (mouseX >= x && mouseX < x + 36 && mouseY >= y && mouseY < y + 18) {
                    graphics.renderTooltip(Minecraft.getInstance().font,
                            List.<FormattedCharSequence>of(
                                    Component.literal("左键编辑").getVisualOrderText(),
                                    Component.literal("中键切换为物品频率").getVisualOrderText()),
                            mouseX, mouseY);
                }
            } else if (mouseX >= x && mouseX < x + 36 && mouseY >= y && mouseY < y + 18) {
                graphics.renderTooltip(Minecraft.getInstance().font,
                        Component.literal("中键以切换为字符频率"), mouseX, mouseY);
            }
        }
    }

    @Inject(method = "sendNewKeys", at = @At("TAIL"))
    private void createimp$saveTextAfterSimulatedEntries(boolean clearServer, CallbackInfo ci) {
        LinkedTypewriterScreen screen = createimp$self();
        Map<Integer, String> texts = TypewriterTextGui.bindings(screen);
        if (clearServer) texts.clear();
        Map<Integer, LinkedTypewriterEntries.KeyboardEntry> entries =
                new HashMap<>(screen.getNewEntries().getKeyMap());
        texts.entrySet().removeIf(binding -> {
            LinkedTypewriterEntries.KeyboardEntry entry = entries.get(binding.getKey());
            return entry == null || !entry.getFirstAsItemStack().isEmpty()
                    || !entry.getSecondAsItemStack().isEmpty();
        });
        PacketDistributor.sendToServer(new SaveTypewriterTextBindingsPacket(
                screen.getMenu().containerId, screen.clientBe.getBlockPos(), entries, new HashMap<>(texts)));
    }
}
