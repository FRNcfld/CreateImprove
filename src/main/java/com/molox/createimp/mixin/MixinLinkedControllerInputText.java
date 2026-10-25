package com.molox.createimp.mixin;

import com.molox.createimp.block.labeled_redstone_link.LinkedControllerTextNetwork;
import com.molox.createimp.item.LinkedControllerTextFrequencies;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerInputPacket;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerPacketBase;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Split Create's authorized button packets between item frequencies and our text channels. */
@Mixin(value = LinkedControllerInputPacket.class, remap = false)
public abstract class MixinLinkedControllerInputText extends LinkedControllerPacketBase {
    @Shadow @Final private List<Integer> activatedButtons;
    @Shadow @Final private boolean press;

    protected MixinLinkedControllerInputText(net.minecraft.core.BlockPos lecternPos) {
        super(lecternPos);
    }

    @Inject(method = "handleItem", at = @At("HEAD"), cancellable = true)
    private void createimp$validateButtons(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        if (activatedButtons.size() > LinkedControllerTextFrequencies.BUTTONS
                || activatedButtons.stream().anyMatch(i -> i == null || i < 0
                || i >= LinkedControllerTextFrequencies.BUTTONS)) ci.cancel();
    }

    @Redirect(method = "handleItem", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/redstone/link/controller/LinkedControllerServerHandler;receivePressed(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Ljava/util/UUID;Ljava/util/List;Z)V"))
    private void createimp$onlyItemButtons(LevelAccessor level, BlockPos pos, java.util.UUID playerId,
                                           List<Couple<RedstoneLinkNetworkHandler.Frequency>> frequencies,
                                           boolean pressed, ServerPlayer player, ItemStack stack) {
        List<Couple<RedstoneLinkNetworkHandler.Frequency>> itemFrequencies = new ArrayList<>(frequencies.size());
        for (int i = 0; i < frequencies.size(); i++) {
            if (!LinkedControllerTextFrequencies.has(stack, activatedButtons.get(i)))
                itemFrequencies.add(frequencies.get(i));
        }
        LinkedControllerServerHandler.receivePressed(level, pos, playerId, itemFrequencies, pressed);
    }

    @Inject(method = "handleItem", at = @At("TAIL"))
    private void createimp$sendTextButtons(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        if (press && player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        for (int button : activatedButtons) {
            LinkedControllerTextNetwork.input(level, player.getUUID(), stack, button, press);
        }
    }
}
