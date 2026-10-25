package com.molox.createimp.compat.cybergoggles;

import com.molox.createimp.compat.extragauges.ExtraGaugesCompat;
import io.github.forgestove.create_cyber_goggles.CCG;
import io.github.forgestove.create_cyber_goggles.core.util.CCGLang;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * 赛博护目镜大尺寸动力合成表的可选兼容入口。
 * <p>
 * 赛博护目镜 8.6.4 在 {@code misc.jei.allowLargeCrafting} 开启时，会让原版
 * 工厂仪表接受机械合成表。若额外仪表已安装，赛博护目镜本身会主动让位，
 * 因此模板仪表也应继续使用已有的额外仪表兼容路径，不能同时启用两套逻辑。
 */
public final class CyberGogglesCompat {

    public static final String MOD_ID = "create_cyber_goggles";

    private CyberGogglesCompat() {
    }

    public static boolean isLargeCraftingEnabled() {
        // 先判断额外仪表与赛博护目镜是否存在，再读取赛博配置；使其始终保持
        // 可选依赖，且两个模组共存时由额外仪表独占原有兼容路径。
        return !ExtraGaugesCompat.isLoaded()
                && ModList.get().isLoaded(MOD_ID)
                && CCG.config.misc.jei.allowLargeCrafting;
    }

    /**
     * 复用赛博护目镜自己的物品列表提示组件，以和原版工厂仪表完全相同的方式
     * 展示大尺寸配方的完整格位布局。
     */
    public static void addLargeRecipeTooltip(
            List<ItemStack> items, int columns, List<? super MutableComponent> tooltip) {
        CCGLang.itemList(items, columns).addTo(tooltip);
    }
}
