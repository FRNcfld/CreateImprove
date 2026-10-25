package com.molox.createimp.compat.simulated.client;

import com.molox.createimp.compat.simulated.TypewriterTextAccess;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-only staged text bindings; Simulated keeps item bindings separately until the menu is saved. */
public final class TypewriterTextGui {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createimp", "textures/gui/linked_typewriter/aero_tyw_compat.png");
    private static final Map<LinkedTypewriterScreen, Map<Integer, String>> STAGED = new WeakHashMap<>();
    private static final int WIDTH = 33;
    private static final int HEIGHT = 15;
    private static final int GAP = 8;
    private static final int TEXT_COLOR = 0xF8F8F8;
    private static final int TEXT_SHADOW_COLOR = 0x747474;
    private static final float TEXT_SHADOW_OFFSET = 0.5f;

    private TypewriterTextGui() {}

    public static Map<Integer, String> bindings(LinkedTypewriterScreen screen) {
        return STAGED.computeIfAbsent(screen, ignored ->
                new HashMap<>(((TypewriterTextAccess) screen.clientBe).createimp$getTextBindings()));
    }

    public static String text(LinkedTypewriterScreen screen, int key) {
        return bindings(screen).get(key);
    }

    public static void render(GuiGraphics graphics, int x, int y, String text) {
        render(graphics, x, y, text, 0);
    }

    /** Renders a scrolled entry while keeping its inner text scissor in screen coordinates. */
    public static void renderScrolled(GuiGraphics graphics, int x, int y, String text, int screenOffsetY) {
        render(graphics, x, y, text, screenOffsetY);
    }

    private static void render(GuiGraphics graphics, int x, int y, String text, int screenOffsetY) {
        if (text == null || text.isEmpty()) return;
        graphics.blit(TEXTURE, x, y, 0, 0, 36, 18, 36, 18);
        Font font = Minecraft.getInstance().font;
        int areaX = x + 3;
        int areaY = y + 2;
        graphics.enableScissor(areaX, areaY + screenOffsetY, areaX + WIDTH, areaY + HEIGHT + screenOffsetY);
        int textWidth = font.width(text);
        int textY = areaY + (HEIGHT - font.lineHeight) / 2;
        if (textWidth <= WIDTH) {
            createimp$drawText(graphics, text, areaX + (WIDTH - textWidth) / 2f, textY);
        } else {
            int cycle = textWidth + GAP;
            int offset = Math.floorMod((int) (System.currentTimeMillis() / 60L), cycle);
            createimp$drawText(graphics, text, areaX - offset, textY);
            createimp$drawText(graphics, text, areaX - offset + cycle, textY);
        }
        graphics.disableScissor();
    }

    private static void createimp$drawText(GuiGraphics graphics, String text, float x, float y) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, text, x + TEXT_SHADOW_OFFSET, y + TEXT_SHADOW_OFFSET,
                TEXT_SHADOW_COLOR, false);
        graphics.drawString(font, text, x, y, TEXT_COLOR, false);
    }
}
