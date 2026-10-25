package com.molox.createimp.compat.sable;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Sable 将物理结构内方块保存在独立的逻辑坐标区域中。无线红石网络在比较距离时必须先把
 * 两端的位置投影回世界坐标；Sable 对 Create 自己的网络更新已经做了这件事，但不覆盖其它
 * 模组手写的查询逻辑。
 */
public final class SableCompat {

    private static final String MOD_ID = "sable";
    private static volatile boolean apiLookupAttempted;
    private static volatile Object sableHelper;
    private static volatile Method distanceSquaredWithSubLevels;

    private SableCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 以 Sable 投影后的世界坐标判断两个无线红石节点是否在 Create 配置的传输距离内。
     * 这里刻意沿用 Sable 自己注入到 {@code RedstoneLinkNetworkHandler#updateNetworkOf}
     * 的严格小于比较，避免边界行为与原版网络更新不一致。
     */
    public static boolean withinRedstoneLinkRange(Level level, IRedstoneLinkable from, IRedstoneLinkable to) {
        if (from == to) return true;
        try {
            if (!resolveApi()) return RedstoneLinkNetworkHandler.withinRange(from, to);
            double distanceSquared = (double) distanceSquaredWithSubLevels.invoke(sableHelper, level,
                    Vec3.atCenterOf(from.getLocation()), Vec3.atCenterOf(to.getLocation()));
            int linkRange = AllConfigs.server().logistics.linkRange.get();
            return distanceSquared < (double) linkRange * linkRange;
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            // 不因为 Sable 的可选 API 变动而令路由器失效；此时退回 Create 的普通世界逻辑。
            return RedstoneLinkNetworkHandler.withinRange(from, to);
        }
    }

    /** 只在首次需要时解析 Sable 的可选 API，之后直接复用方法句柄。 */
    private static boolean resolveApi() {
        if (apiLookupAttempted) return sableHelper != null && distanceSquaredWithSubLevels != null;
        synchronized (SableCompat.class) {
            if (apiLookupAttempted) return sableHelper != null && distanceSquaredWithSubLevels != null;
            try {
                // Sable 的 SableCompanion API 位于其独立的 companion 库中，不保证能作为我们的
                // 编译期依赖存在；按类名调用可保持本模组对 Sable 完全可选。
                Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
                Field helperField = sableClass.getField("HELPER");
                Object helper = helperField.get(null);
                Method distanceMethod = helper.getClass().getMethod("distanceSquaredWithSubLevels",
                        Level.class, Position.class, Position.class);
                sableHelper = helper;
                distanceSquaredWithSubLevels = distanceMethod;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // 保持字段为 null，调用方将继续使用 Create 的普通世界距离判断。
            } finally {
                apiLookupAttempted = true;
            }
            return sableHelper != null && distanceSquaredWithSubLevels != null;
        }
    }
}
