package com.molox.createimp.compat.simulated;

import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkNetworkHandler;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkable;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

/** Active key presses from one loaded typewriter. Nothing here survives a save/load cycle. */
public final class TypewriterTextTransmitters {
    private final Map<Integer, Transmitter> active = new HashMap<>();

    public void press(ServerLevel level, int key, String frequency) {
        if (frequency == null || frequency.isBlank()) return;
        Transmitter existing = active.get(key);
        if (existing != null && existing.frequency.equals(frequency)) return;
        release(level, key);
        Transmitter transmitter = new Transmitter(frequency);
        active.put(key, transmitter);
        LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
        handler.addToNetwork(transmitter);
        handler.updateNetworkOf(transmitter);
    }

    public void release(ServerLevel level, int key) {
        Transmitter transmitter = active.remove(key);
        if (transmitter != null) {
            LabeledRedstoneLinkNetworkHandler.get(level).removeFromNetwork(transmitter);
        }
    }

    public void stopAll(ServerLevel level) {
        for (int key : new java.util.ArrayList<>(active.keySet())) release(level, key);
    }

    private record Transmitter(String frequency) implements LabeledRedstoneLinkable {
        @Override public boolean isReceiver() { return false; }
        @Override public int getTransmittedSignal() { return 15; }
        @Override public void onReceivedSignal(int signal) { }
        @Override public String getFrequencyText() { return frequency; }
    }
}
