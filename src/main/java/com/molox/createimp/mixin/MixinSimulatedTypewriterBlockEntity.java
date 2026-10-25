package com.molox.createimp.mixin;

import com.molox.createimp.compat.simulated.TypewriterTextAccess;
import com.molox.createimp.compat.simulated.TypewriterTextTransmitters;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/** Stores text-only bindings beside Simulated's unchanged item-frequency codec. */
@Mixin(value = LinkedTypewriterBlockEntity.class, remap = false)
public abstract class MixinSimulatedTypewriterBlockEntity implements TypewriterTextAccess {
    @Unique private static final String createimp$TAG = "CreateImpTextKeys";
    @Unique private final Map<Integer, String> createimp$textKeys = new HashMap<>();
    @Unique private final TypewriterTextTransmitters createimp$transmitters = new TypewriterTextTransmitters();
    @Shadow private LinkedTypewriterEntries entryMap;

    @Unique private LinkedTypewriterBlockEntity createimp$self() {
        return (LinkedTypewriterBlockEntity) (Object) this;
    }

    @Override
    public Map<Integer, String> createimp$getTextBindings() {
        return Map.copyOf(createimp$textKeys);
    }

    @Override
    public void createimp$replaceTextBindings(Map<Integer, String> bindings) {
        createimp$stopAllTextKeys();
        createimp$textKeys.clear();
        if (bindings == null) return;
        bindings.forEach((key, text) -> {
            if (key != null && key >= 0 && key <= 512 && text != null && !text.isBlank()
                    && text.length() <= 64 && entryMap.getEntry(key) != null) {
                createimp$textKeys.put(key, text);
            }
        });
    }

    @Override
    public void createimp$pressTextKey(int key) {
        if (createimp$self().getLevel() instanceof ServerLevel serverLevel) {
            createimp$transmitters.press(serverLevel, key, createimp$textKeys.get(key));
        }
    }

    @Override
    public void createimp$releaseTextKey(int key) {
        if (createimp$self().getLevel() instanceof ServerLevel serverLevel) {
            createimp$transmitters.release(serverLevel, key);
        }
    }

    @Override
    public void createimp$stopAllTextKeys() {
        if (createimp$self().getLevel() instanceof ServerLevel serverLevel) {
            createimp$transmitters.stopAll(serverLevel);
        }
    }

    @Unique private ListTag createimp$serialize() {
        ListTag list = new ListTag();
        createimp$textKeys.forEach((key, text) -> {
            CompoundTag binding = new CompoundTag();
            binding.putInt("Key", key);
            binding.putString("Text", text);
            list.add(binding);
        });
        return list;
    }

    @Unique private void createimp$deserialize(CompoundTag tag) {
        Map<Integer, String> bindings = new HashMap<>();
        for (Tag element : tag.getList(createimp$TAG, Tag.TAG_COMPOUND)) {
            CompoundTag binding = (CompoundTag) element;
            bindings.put(binding.getInt("Key"), binding.getString("Text"));
        }
        createimp$replaceTextBindings(bindings);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createimp$writeTextKeys(CompoundTag tag, HolderLookup.Provider registries,
                                         boolean clientPacket, CallbackInfo ci) {
        tag.put(createimp$TAG, createimp$serialize());
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createimp$readTextKeys(CompoundTag tag, HolderLookup.Provider registries,
                                        boolean clientPacket, CallbackInfo ci) {
        createimp$deserialize(tag);
    }

    @Redirect(method = "pressKey", at = @At(value = "INVOKE", target =
            "Ldev/simulated_team/simulated/content/blocks/redstone/linked_typewriter/LinkedTypewriterEntries;activateKey(ILdev/simulated_team/simulated/content/blocks/redstone/linked_typewriter/LinkedTypewriterBlockEntity;)V"))
    private void createimp$pressTextInsteadOfItem(LinkedTypewriterEntries entries, int key,
                                                   LinkedTypewriterBlockEntity blockEntity) {
        if (createimp$textKeys.containsKey(key)) createimp$pressTextKey(key);
        else entries.activateKey(key, blockEntity);
    }

    @Redirect(method = "releaseKey", at = @At(value = "INVOKE", target =
            "Ldev/simulated_team/simulated/content/blocks/redstone/linked_typewriter/LinkedTypewriterEntries;deactivateKey(I)V"))
    private void createimp$releaseTextInsteadOfItem(LinkedTypewriterEntries entries, int key) {
        if (createimp$textKeys.containsKey(key)) createimp$releaseTextKey(key);
        else entries.deactivateKey(key);
    }

    @Inject(method = "disconnectUser", at = @At("HEAD"))
    private void createimp$disconnectTextKeys(CallbackInfo ci) { createimp$stopAllTextKeys(); }

    @Inject(method = {"invalidate", "destroy"}, at = @At("HEAD"))
    private void createimp$unloadTextKeys(CallbackInfo ci) { createimp$stopAllTextKeys(); }

    @Inject(method = "writeToClipboard", at = @At("TAIL"))
    private void createimp$copyTextKeys(HolderLookup.Provider registries, CompoundTag tag,
                                        Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) tag.put(createimp$TAG, createimp$serialize());
    }

    @Inject(method = "readFromClipboard", at = @At("TAIL"))
    private void createimp$pasteTextKeys(HolderLookup.Provider registries, CompoundTag tag,
                                         Player player, Direction side, boolean simulate,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!simulate && cir.getReturnValue()) {
            createimp$deserialize(tag);
            createimp$self().setChanged();
            createimp$self().sendData();
        }
    }
}
