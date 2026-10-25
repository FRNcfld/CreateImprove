package com.molox.createimp.compat.simulated;

import com.simibubi.create.Create;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import net.minecraft.server.level.ServerLevel;

/** Reconciles transmitters before Simulated replaces an entire binding map. */
public final class TypewriterBindings {
    private TypewriterBindings() { }

    public static void stopTransmitters(LinkedTypewriterBlockEntity blockEntity) {
        ((TypewriterTextAccess) blockEntity).createimp$stopAllTextKeys();
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) return;
        for (LinkedTypewriterEntries.KeyboardEntry entry : blockEntity.getTypewriterEntries().getEntries()) {
            if (entry.isAlive()) {
                // Simulated's clearAll discards its queued removals. Remove live entries first.
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, entry);
            }
        }
    }
}
