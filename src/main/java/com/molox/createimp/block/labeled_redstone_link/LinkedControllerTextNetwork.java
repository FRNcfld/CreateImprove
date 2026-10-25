package com.molox.createimp.block.labeled_redstone_link;

import com.molox.createimp.item.LinkedControllerTextFrequencies;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Mirrors Create's held-button refresh/release lifetime in the separate text-frequency network. */
public final class LinkedControllerTextNetwork {
    private static final int TIMEOUT = 30;
    private static final Map<ServerLevel, Map<UUID, Map<Integer, PressedButton>>> ACTIVE = new WeakHashMap<>();

    private LinkedControllerTextNetwork() {}

    public static void input(ServerLevel level, UUID player, ItemStack controller, int button, boolean pressed) {
        if (button < 0 || button >= LinkedControllerTextFrequencies.BUTTONS) return;
        Map<UUID, Map<Integer, PressedButton>> players = ACTIVE.get(level);
        Map<Integer, PressedButton> buttons = players == null ? null : players.get(player);
        PressedButton previous = buttons == null ? null : buttons.get(button);
        String frequency = LinkedControllerTextFrequencies.get(controller, button);
        if (previous != null && (!pressed || !previous.frequency.equals(frequency))) {
            LabeledRedstoneLinkNetworkHandler.get(level).removeFromNetwork(previous);
            buttons.remove(button);
            if (buttons.isEmpty()) players.remove(player);
        }
        if (!pressed || frequency.isBlank()) return;
        if (players == null) {
            players = new HashMap<>();
            ACTIVE.put(level, players);
        }
        buttons = players.computeIfAbsent(player, key -> new HashMap<>());
        PressedButton current = buttons.get(button);
        if (current == null) {
            current = new PressedButton(controller, button, frequency);
            buttons.put(button, current);
            LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
            handler.addToNetwork(current);
            handler.updateNetworkOf(current);
        } else {
            current.lifetime = TIMEOUT;
        }
    }

    public static void tick(ServerLevel level) {
        Map<UUID, Map<Integer, PressedButton>> players = ACTIVE.get(level);
        if (players == null) return;
        LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
        Iterator<Map.Entry<UUID, Map<Integer, PressedButton>>> playerIt = players.entrySet().iterator();
        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<Integer, PressedButton>> player = playerIt.next();
            Iterator<PressedButton> buttonIt = player.getValue().values().iterator();
            while (buttonIt.hasNext()) {
                PressedButton pressed = buttonIt.next();
                if (level.getPlayerByUUID(player.getKey()) == null || --pressed.lifetime <= 0
                        || !pressed.frequency.equals(LinkedControllerTextFrequencies.get(pressed.source, pressed.button))) {
                    handler.removeFromNetwork(pressed);
                    buttonIt.remove();
                }
            }
            if (player.getValue().isEmpty()) playerIt.remove();
        }
        if (players.isEmpty()) ACTIVE.remove(level);
    }

    private static final class PressedButton implements LabeledRedstoneLinkable {
        private final ItemStack source;
        private final int button;
        private final String frequency;
        private int lifetime = TIMEOUT;

        private PressedButton(ItemStack source, int button, String frequency) {
            this.source = source;
            this.button = button;
            this.frequency = frequency;
        }

        @Override public boolean isReceiver() { return false; }
        @Override public int getTransmittedSignal() { return 15; }
        @Override public void onReceivedSignal(int power) { }
        @Override public String getFrequencyText() { return frequency; }
    }
}
