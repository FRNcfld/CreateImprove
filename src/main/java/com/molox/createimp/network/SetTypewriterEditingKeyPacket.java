package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.compat.simulated.TypewriterEditState;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-side ghost-slot guard tracks the currently edited typewriter key. */
public record SetTypewriterEditingKeyPacket(int containerId, int key) implements CustomPacketPayload {
    public static final Type<SetTypewriterEditingKeyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "typewriter_editing_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetTypewriterEditingKeyPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.containerId());
                buf.writeVarInt(packet.key());
            }, buf -> new SetTypewriterEditingKeyPacket(buf.readVarInt(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetTypewriterEditingKeyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof LinkedTypewriterMenuCommon menu)
                    || menu.containerId != packet.containerId() || !menu.stillValid(player)) return;
            ((TypewriterEditState) menu).createimp$setEditingKey(
                    packet.key() >= 0 && packet.key() <= 512 ? packet.key() : -1);
        });
    }
}
