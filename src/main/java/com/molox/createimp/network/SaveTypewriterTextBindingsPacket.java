package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.compat.simulated.TypewriterBindings;
import com.molox.createimp.compat.simulated.TypewriterTextAccess;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** Atomically replaces a typewriter's item bindings and its per-key text bindings. */
public record SaveTypewriterTextBindingsPacket(int containerId, BlockPos pos,
                                                Map<Integer, LinkedTypewriterEntries.KeyboardEntry> entries,
                                                Map<Integer, String> textBindings) implements CustomPacketPayload {
    private static final int MAX_KEYS = 128;
    private static final int MAX_TEXT_LENGTH = 64;
    public static final Type<SaveTypewriterTextBindingsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_typewriter_text_bindings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveTypewriterTextBindingsPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.containerId());
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.entries().size());
                packet.entries().forEach((key, entry) -> {
                    buf.writeVarInt(key);
                    LinkedTypewriterEntries.KeyboardEntry.STREAM_CODEC.encode(buf, entry);
                });
                buf.writeVarInt(packet.textBindings().size());
                packet.textBindings().forEach((key, text) -> {
                    buf.writeVarInt(key);
                    buf.writeUtf(text, MAX_TEXT_LENGTH);
                });
            }, buf -> {
                int containerId = buf.readVarInt();
                BlockPos pos = buf.readBlockPos();
                Map<Integer, LinkedTypewriterEntries.KeyboardEntry> entries = new HashMap<>();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_KEYS) throw new IllegalArgumentException("Invalid typewriter binding count");
                for (int i = 0; i < count; i++) {
                    entries.put(buf.readVarInt(), LinkedTypewriterEntries.KeyboardEntry.STREAM_CODEC.decode(buf));
                }
                Map<Integer, String> texts = new HashMap<>();
                count = buf.readVarInt();
                if (count < 0 || count > MAX_KEYS) throw new IllegalArgumentException("Invalid typewriter text count");
                for (int i = 0; i < count; i++) texts.put(buf.readVarInt(), buf.readUtf(MAX_TEXT_LENGTH));
                return new SaveTypewriterTextBindingsPacket(containerId, pos, entries, texts);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveTypewriterTextBindingsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof LinkedTypewriterMenuCommon menu)
                    || menu.containerId != packet.containerId()
                    || !menu.stillValid(player)
                    || !(menu.contentHolder instanceof LinkedTypewriterBlockEntity be)
                    || !be.getBlockPos().equals(packet.pos())
                    || player.level().getBlockEntity(packet.pos()) != be
                    || !be.checkUser(player.getUUID())
                    || packet.entries().size() > MAX_KEYS || packet.textBindings().size() > MAX_KEYS) return;
            for (var item : packet.entries().entrySet()) {
                int key = item.getKey();
                if (key < 0 || key > 512 || item.getValue() == null
                        || item.getValue().glfwKeyCode != key) return;
            }
            for (var item : packet.textBindings().entrySet()) {
                LinkedTypewriterEntries.KeyboardEntry entry = packet.entries().get(item.getKey());
                if (entry == null || item.getValue() == null || item.getValue().isBlank()
                        || item.getValue().length() > MAX_TEXT_LENGTH
                        || !entry.getFirstAsItemStack().isEmpty()
                        || !entry.getSecondAsItemStack().isEmpty()) return;
            }
            TypewriterBindings.stopTransmitters(be);
            LinkedTypewriterEntries entries = be.getTypewriterEntries();
            entries.clearAll();
            for (var item : packet.entries().entrySet()) {
                item.getValue().setLocation(packet.pos());
                entries.setKey(item.getKey(), item.getValue());
            }
            ((TypewriterTextAccess) be).createimp$replaceTextBindings(packet.textBindings());
            be.setChanged();
            be.sendData();
        });
    }
}
