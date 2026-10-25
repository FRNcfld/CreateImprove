package com.molox.createimp.item;

import com.molox.createimp.registry.ModDataComponents;
import com.simibubi.create.AllItems;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Six independent text channels attached to a Create linked controller. An empty entry uses the original item pair. */
public final class LinkedControllerTextFrequencies {
    public static final int BUTTONS = 6;
    public static final int MAX_LENGTH = 64;

    private LinkedControllerTextFrequencies() {}

    public static String get(ItemStack stack, int button) {
        if (button < 0 || button >= BUTTONS || !AllItems.LINKED_CONTROLLER.isIn(stack)) return "";
        List<String> labels = stack.get(ModDataComponents.LINKED_CONTROLLER_TEXT_FREQUENCIES.get());
        return labels != null && button < labels.size() ? labels.get(button) : "";
    }

    public static boolean has(ItemStack stack, int button) {
        return !get(stack, button).isBlank();
    }

    public static void set(ItemStack stack, int button, String text) {
        if (button < 0 || button >= BUTTONS || !AllItems.LINKED_CONTROLLER.isIn(stack)) return;
        if (text.length() > MAX_LENGTH) return;
        List<String> labels = new ArrayList<>(BUTTONS);
        for (int i = 0; i < BUTTONS; i++) labels.add(get(stack, i));
        labels.set(button, text);
        if (labels.stream().allMatch(String::isEmpty)) {
            stack.remove(ModDataComponents.LINKED_CONTROLLER_TEXT_FREQUENCIES.get());
        } else {
            stack.set(ModDataComponents.LINKED_CONTROLLER_TEXT_FREQUENCIES.get(), List.copyOf(labels));
        }
    }
}
