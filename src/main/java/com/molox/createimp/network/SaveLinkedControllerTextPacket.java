package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.LinkedControllerTextFrequencies;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Only the server's currently open controller menu and held stack may be edited. */
public record SaveLinkedControllerTextPacket(int containerId, int button, String text, boolean delete)
        implements CustomPacketPayload {
    public static final Type<SaveLinkedControllerTextPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_linked_controller_text"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveLinkedControllerTextPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.containerId());
                buf.writeVarInt(packet.button());
                buf.writeUtf(packet.text(), LinkedControllerTextFrequencies.MAX_LENGTH);
                buf.writeBoolean(packet.delete());
            }, buf -> new SaveLinkedControllerTextPacket(buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(LinkedControllerTextFrequencies.MAX_LENGTH), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveLinkedControllerTextPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof LinkedControllerMenu menu)
                    || menu.containerId != packet.containerId()
                    || !menu.stillValid(player)
                    || packet.button() < 0 || packet.button() >= LinkedControllerTextFrequencies.BUTTONS
                    || packet.text().length() > LinkedControllerTextFrequencies.MAX_LENGTH) return;
            ItemStack controller = menu.contentHolder;
            if (packet.delete()) {
                LinkedControllerTextFrequencies.set(controller, packet.button(), "");
            } else {
                if (packet.text().isBlank()) return;
                LinkedControllerTextFrequencies.set(controller, packet.button(), packet.text());
                for (int i = 0; i < 2; i++) {
                    int slot = packet.button() * 2 + i;
                    menu.ghostInventory.setStackInSlot(slot, ItemStack.EMPTY);
                    menu.getSlot(36 + slot).setChanged();
                }
            }
            menu.broadcastChanges();
            player.getInventory().setChanged();
        });
    }
}
