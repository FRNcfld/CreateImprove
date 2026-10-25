package com.molox.createimp.mixin;

import com.molox.createimp.compat.simulated.TypewriterEditState;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = LinkedTypewriterMenuCommon.class, remap = false)
public abstract class MixinSimulatedTypewriterMenu implements TypewriterEditState {
    @Unique private int createimp$editingKey = -1;

    @Override
    public int createimp$getEditingKey() { return createimp$editingKey; }

    @Override
    public void createimp$setEditingKey(int key) { createimp$editingKey = key; }
}
