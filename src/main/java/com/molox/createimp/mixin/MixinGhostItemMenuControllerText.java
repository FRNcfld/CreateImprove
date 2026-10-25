package com.molox.createimp.mixin;

import com.molox.createimp.item.LinkedControllerTextFrequencies;
import com.molox.createimp.compat.simulated.TypewriterTextAccess;
import com.molox.createimp.compat.simulated.TypewriterEditState;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerMenu;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enforce text-mode ghost-slot locking on both menu copies, including shift insertion and drag. */
@Mixin(GhostItemMenu.class)
public abstract class MixinGhostItemMenuControllerText {
    private boolean createimp$locked(int ghostSlot) {
        if ((Object) this instanceof LinkedControllerMenu controller) {
            return ghostSlot >= 0 && ghostSlot < 12
                    && LinkedControllerTextFrequencies.has(controller.contentHolder, ghostSlot / 2);
        }
        return (Object) this instanceof LinkedTypewriterMenuCommon typewriter
                && ghostSlot >= 0 && ghostSlot < 2
                && typewriter.contentHolder != null
                && ((TypewriterTextAccess) typewriter.contentHolder).createimp$getTextBindings()
                        .containsKey(((TypewriterEditState) typewriter).createimp$getEditingKey());
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void createimp$guardClick(int slotId, int dragType, ClickType clickType,
                                      Player player, CallbackInfo ci) {
        if (createimp$locked(slotId - 36)) ci.cancel();
    }

    @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
    private void createimp$guardDrag(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        int ghostSlot = slot.getSlotIndex();
        if (ghostSlot < 0) return;
        if ((Object) this instanceof LinkedControllerMenu controller
                && ghostSlot < 12 && controller.getSlot(36 + ghostSlot) == slot
                && createimp$locked(ghostSlot)) cir.setReturnValue(false);
        if ((Object) this instanceof LinkedTypewriterMenuCommon typewriter
                && ghostSlot < 2 && typewriter.getSlot(36 + ghostSlot) == slot
                && createimp$locked(ghostSlot)) cir.setReturnValue(false);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void createimp$guardShiftFromGhost(Player player, int slotId,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (createimp$locked(slotId - 36)) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Redirect(method = "quickMoveStack", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/items/ItemStackHandler;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack createimp$skipLockedGhostOnShift(ItemStackHandler inventory, int ghostSlot) {
        return createimp$locked(ghostSlot) ? new ItemStack(Items.BARRIER) : inventory.getStackInSlot(ghostSlot);
    }
}
