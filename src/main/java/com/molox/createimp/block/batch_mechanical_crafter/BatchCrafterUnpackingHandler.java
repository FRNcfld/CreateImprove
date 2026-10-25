package com.molox.createimp.block.batch_mechanical_crafter;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum BatchCrafterUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (orderContext != null && !orderContext.orderedCrafts().isEmpty()) {
            return simulate;
        }
        return DEFAULT.unpack(level, pos, state, side, items, null, simulate);
    }

    /**
     * 处理一个带合成表的包裹。每个包裹只携带单一配方（orderedCrafts长度恒为1），
     * 不再需要在合成器上记录任何跨包裹的订单/entry/批次进度——包裹自身携带的材料量
     * 就是全部需要处理的内容，处理多少算多少，材料不够凑一批就让包裹原地卡住。
     */
    public static void processBatchPackage(PackagerBlockEntity packager,
                                           BatchMechanicalCrafterBlockEntity crafter) {
        ItemStack box = packager.heldBox;
        if (box.isEmpty()) return;
        if (packager.animationTicks > 0) return;

        PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
        if (orderContext == null || orderContext.orderedCrafts().isEmpty()) return;

        Level level = crafter.getLevel();
        if (level == null || level.isClientSide()) return;

        BatchConnectedInputHandler.ConnectedInput input = crafter.getInput();
        List<BatchMechanicalCrafterBlockEntity.Inventory> inventories =
                input.getInventories(level, crafter.getBlockPos());
        if (inventories.isEmpty()) return;

        // 包裹只携带一个配方，直接取第一个（也是唯一一个）entry。
        PackageOrderWithCrafts.CraftingEntry entry = orderContext.orderedCrafts().get(0);
        if (entry.count() <= 0) return;
        List<BigItemStack> pattern = entry.pattern().stacks();

        // 整条连接链上，任意一个格子只要phase不是IDLE，或者phase虽然是IDLE
        // 但格子里还留有尚未开始组装的物品（两者不等价：phase变回IDLE后，
        // 格子被玩家手动放入物品、下一次tick真正触发checkCompletedRecipe之前
        // 的这一小段时间里，phase是IDLE但格子并不空——tick()里"phase==IDLE
        // 且craftingItemPresent()为真时才调用checkCompletedRecipe(false)"这
        // 一段逻辑本身就是这个状态真实存在的证据），都说明链上还没有完全
        // 空闲下来，此时绝不能开始分配这次包裹的材料——否则会出现"链条被
        // ejectWholeGrid()逐格重置为IDLE期间，只有刚重置的那一个格子空闲、
        // 其余仍在WAITING/CRAFTING"的半吊子状态：本该整条链一起收到材料，
        // 结果只有那一个格子插入成功，紧接着的checkCompletedRecipe(true)又
        // 会把其余8个"其实没轮到处理、只是槽位恰好也是空气"的格子一并强行
        // begin()，导致合成结果完全错乱；也可能出现新旧物品混入同一次组装
        // 导致合成结果错乱的情况。因此这里必须要求链上全部格子都真正处于
        // "phase为IDLE且格子本身为空"这个状态才允许处理，包裹原样留在打包机
        // 内等待，直到整条链真正全部闲下来为止。
        for (BatchMechanicalCrafterBlockEntity.Inventory inv : inventories) {
            if (inv.getBlockEntity().phase != BatchMechanicalCrafterBlockEntity.Phase.IDLE) return;
            if (!inv.getItem(0).isEmpty()) return;
        }
        // 订单只携带材料格位，不携带配方 ID、产物或宽高。按合成器实际允许的
        // 普通/机械配方识别后，使用真实格位分配材料；无法唯一判定时保留包裹。
        PatternLayout layout = findCraftingLayout(level, pattern);
        if (layout == null) return;
        List<BigItemStack> recipeSlots = layout.slots();

        // 不要求整条连接链条本身恰好等于配方尺寸——只需包含一块完整的
        // width×height矩形区域。这样既支持原来的方阵配方，也支持赛博护目镜
        // 传入的2×8、4×3等非正方形机械合成表。
        List<BatchMechanicalCrafterBlockEntity.Inventory> placement =
                findRectanglePlacement(crafter, inventories, layout.width(), layout.height());
        if (placement == null) return;
        for (BatchMechanicalCrafterBlockEntity.Inventory inv : placement) {
            // 有盖板的格子不能收货；在动包裹之前确认整个区域可用。
            if (inv.getBlockEntity().covered) return;
        }

        // 取出包裹内容
        ItemStackHandler contents = PackageItem.getContents(box);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack s = contents.getStackInSlot(i);
            if (!s.isEmpty()) items.add(s.copy());
        }

        // 配方格位中同一种物品可能多次出现；库存需要按物品类型合计。
        // 先按物品类型合并，得到每种材料"单批总需求"，用于库存量约束的计算。
        Map<ItemStack, Integer> perBatchNeedByItem = new LinkedHashMap<>();
        for (BigItemStack slot : recipeSlots) {
            if (slot.stack.isEmpty() || slot.count <= 0) continue;
            ItemStack key = null;
            for (ItemStack existingKey : perBatchNeedByItem.keySet()) {
                if (ItemStack.isSameItemSameComponents(existingKey, slot.stack)) {
                    key = existingKey;
                    break;
                }
            }
            if (key == null) {
                perBatchNeedByItem.put(slot.stack, slot.count);
            } else {
                perBatchNeedByItem.put(key, perBatchNeedByItem.get(key) + slot.count);
            }
        }

        // 计算本次批次数：受最小堆叠上限、包裹里实际材料量两重约束。
        // 堆叠上限约束必须逐格独立计算（每个格子是独立槽位，各自上限64）。
        int maxBatches = entry.count();
        for (BigItemStack slot : recipeSlots) {
            if (slot.stack.isEmpty() || slot.count <= 0) continue;
            int maxByStack = Math.min(64, slot.stack.getMaxStackSize()) / slot.count;
            maxBatches = Math.min(maxBatches, maxByStack);
        }

        // 库存量约束使用合并后的单批总需求，因为库存按物品类型统一计量，不分槽位。
        for (Map.Entry<ItemStack, Integer> e : perBatchNeedByItem.entrySet()) {
            ItemStack material = e.getKey();
            int perBatchNeed = e.getValue();
            int available = 0;
            for (ItemStack item : items) {
                if (ItemStack.isSameItemSameComponents(item, material))
                    available += item.getCount();
            }
            maxBatches = Math.min(maxBatches, available / Math.max(perBatchNeed, 1));
        }
        // 材料不够凑一批，包裹安全卡在打包机里不动，等待玩家处理
        if (maxBatches <= 0) return;

        // 分配材料到合成器槽位。
        // Inventory.insertItem 单槽一旦非空即整体拒绝任何后续插入（即使是同种物品），
        // 因此每个格子所需的数量必须先从items列表里凑齐成一份，再对该槽位调用一次insertItem。
        // placement 按配方的真实宽高选出矩形；recipeSlots 已按配方行优先排列。
        int totalInserted = 0;
        BatchMechanicalCrafterBlockEntity triggerCrafter = null;
        for (int i = 0; i < recipeSlots.size(); i++) {
            BigItemStack slot = recipeSlots.get(i);
            if (slot.stack.isEmpty()) continue;

            int needed = slot.count * maxBatches;
            int collected = 0;
            ItemStack combined = ItemStack.EMPTY;
            for (ItemStack item : items) {
                if (collected >= needed) break;
                if (item.isEmpty() || !ItemStack.isSameItemSameComponents(item, slot.stack)) continue;
                int take = Math.min(needed - collected, item.getCount());
                if (take <= 0) continue;
                if (combined.isEmpty()) {
                    combined = item.copyWithCount(take);
                } else {
                    combined.grow(take);
                }
                item.shrink(take);
                collected += take;
            }

            if (collected <= 0) continue;

            BatchMechanicalCrafterBlockEntity.Inventory targetInv = placement.get(i);
            ItemStack leftover = targetInv.insertItem(0, combined, false);
            int inserted = collected - leftover.getCount();
            totalInserted += inserted;
            if (inserted > 0 && triggerCrafter == null) {
                triggerCrafter = targetInv.getBlockEntity();
            }

            if (!leftover.isEmpty()) {
                items.add(leftover);
            }
        }

        // 如果实际没有材料被放进去（合成器phase不是IDLE），包裹留着等待，不做后续处理
        if (totalInserted == 0) return;

        // 从实际收到材料的格子启动；配方左上角也可能是有效的空格。
        // 包裹相邻的合成器也可能不在选中的矩形区域内。
        if (triggerCrafter.getSpeed() == 0f) {
            // checkCompletedRecipe(true)内部会因为getSpeed()==0直接返回、什么都不做，
            // 且不会留下任何"材料已就绪、等动力恢复后再试一次"的记录——如果这里不主动
            // 记录，之后即便动力恢复，也没有任何代码会重新尝试启动这次合成，只能靠玩家
            // 手动通一次红石信号。这里记录pendingForcedStart，交给onSpeedChanged在
            // 动力真正恢复的那一刻补一次checkCompletedRecipe(true)。
            triggerCrafter.pendingForcedStart = true;
            triggerCrafter.setChanged();
        } else {
            triggerCrafter.checkCompletedRecipe(true);
        }

        // 把剩余物品写回包裹内容
        ItemStackHandler newContents = new ItemStackHandler(9);
        for (ItemStack item : items) {
            if (!item.isEmpty())
                ItemHandlerHelper.insertItemStacked(newContents, item, false);
        }
        box.set(AllDataComponents.PACKAGE_CONTENTS,
                ItemHelper.containerContentsFromHandler(newContents));

        boolean contentsEmpty = true;
        for (int i = 0; i < newContents.getSlots(); i++) {
            if (!newContents.getStackInSlot(i).isEmpty()) {
                contentsEmpty = false;
                break;
            }
        }

        if (contentsEmpty) {
            // 包裹内容已被吃空：heldBox清空，播放一次性的"消失"动画。
            packager.previouslyUnwrapped = box;
            packager.heldBox = ItemStack.EMPTY;
            packager.animationInward = true;
            packager.animationTicks = 20;
            packager.notifyUpdate();
        } else {
            // 部分处理后同步减少订单剩余批次；否则剩余材料可能被重复合成。
            PackageOrderWithCrafts remaining = new PackageOrderWithCrafts(
                    orderContext.orderedStacks(),
                    List.of(new PackageOrderWithCrafts.CraftingEntry(entry.pattern(), entry.count() - maxBatches)));
            if (PackageItem.hasOrderData(box)) {
                PackageItem.setOrder(box, PackageItem.getOrderId(box), PackageItem.getLinkIndex(box),
                        PackageItem.isFinalLink(box), PackageItem.getIndex(box), PackageItem.isFinal(box), remaining);
            } else {
                PackageItem.addOrderContext(box, remaining);
            }
            // 包裹内容还没吃空：保留在 heldBox，等待合成器槽位清空。
            packager.heldBox = box;
            packager.notifyUpdate();
        }
    }

    private static @Nullable PatternLayout findCraftingLayout(Level level, List<BigItemStack> pattern) {
        PatternLayout found = null;
        RecipeHolder<?> matchedRecipe = null;
        boolean matchedRegular = false;

        // 普通配方与最终合成阶段使用同一套配置和自动化过滤规则。
        // 订单可能是原始矩形，也可能已被补成九宫格；裁掉外围空格后交给
        // RecipeManager 判定，包含有序、无序及其它实现 CraftingRecipe 的配方。
        if (Boolean.TRUE.equals(AllConfigs.server().recipes.allowRegularCraftingInCrafter.get())) {
            for (int width = 1; width <= 3; width++) {
                for (int height = 1; height <= 3; height++) {
                    if (width * height != pattern.size()) continue;
                    PatternLayout candidate = trimRegularPattern(pattern, width, height);
                    if (candidate == null) continue;
                    List<ItemStack> inputSlots = new ArrayList<>(candidate.slots().size());
                    for (BigItemStack slot : candidate.slots()) {
                        inputSlots.add(slot.stack.isEmpty() ? ItemStack.EMPTY : slot.stack.copyWithCount(1));
                    }
                    BatchCraftingInput input = BatchCraftingInput.of(
                            candidate.width(), candidate.height(), inputSlots);
                    var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                            .filter(holder -> BatchRecipeGridHandler.isRecipeAllowed(holder, input));
                    if (recipe.isEmpty()) continue;
                    if (found != null && (!matchedRecipe.id().equals(recipe.get().id())
                            || !samePlacement(found, candidate))) return null;
                    found = candidate;
                    matchedRecipe = recipe.get();
                    matchedRegular = true;
                }
            }
        }

        for (RecipeHolder<?> holder : level.getRecipeManager()
                .getAllRecipesFor(AllRecipeTypes.MECHANICAL_CRAFTING.getType())) {
            if (!(holder.value() instanceof MechanicalCraftingRecipe recipe)) continue;
            int width = recipe.getWidth(), height = recipe.getHeight();
            if (width <= 0 || height <= 0 || recipe.getIngredients().size() != width * height) continue;

            // 原始矩形、模板仪表左上对齐的方阵，以及 Create 的九宫格转换。
            // 每一种编码都生成“编码位置 -> 原配方位置”的映射；只有完整的空格和
            // 非空格都吻合，才可以用该配方真实的矩形格位重新摆放。
            List<List<Integer>> encodings = new ArrayList<>();
            List<Integer> raw = new ArrayList<>(width * height);
            for (int i = 0; i < width * height; i++) raw.add(i);
            encodings.add(raw);

            int side = Math.max(width, height);
            if (side > 0 && side * side == pattern.size()) {
                List<Integer> square = new ArrayList<>(side * side);
                for (int i = 0; i < side * side; i++) {
                    int row = i / side, col = i % side;
                    square.add(row < height && col < width ? row * width + col : -1);
                }
                encodings.add(square);
            }
            encodings.add(createOrderEncoding(width, height));

            for (List<Integer> encoding : encodings) {
                PatternLayout candidate = matchRecipeEncoding(recipe, pattern, encoding);
                if (candidate == null) continue;
                if (found != null && (matchedRegular || !matchedRecipe.id().equals(holder.id())
                        || !samePlacement(found, candidate))) {
                    // 包裹没有目标产物/配方 ID；不同配方或摆放结果均可解释时
                    // 无法确认用户原本选择的产物，不能凭配方遍历顺序猜测。
                    return null;
                }
                found = candidate;
                matchedRecipe = holder;
            }
        }
        return found;
    }

    /** 普通工作台配方最多 3×3；保留格位的相对位置，仅裁去外围全空的行列。 */
    private static @Nullable PatternLayout trimRegularPattern(
            List<BigItemStack> pattern, int width, int height) {
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int i = 0; i < pattern.size(); i++) {
            BigItemStack slot = pattern.get(i);
            if (slot.stack.isEmpty()) continue;
            if (slot.count <= 0) return null;
            int x = i % width, y = i / width;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        if (maxX < 0) return null;
        List<BigItemStack> slots = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                slots.add(pattern.get(y * width + x));
            }
        }
        return new PatternLayout(maxX - minX + 1, maxY - minY + 1, slots);
    }

    /** 完全重现 Create 6.0.10 FactoryPanelScreen.convertRecipeToPackageOrderContext 的格位填充。 */
    private static List<Integer> createOrderEncoding(int width, int height) {
        List<Integer> encoding = new ArrayList<>();
        if (height == 1) {
            for (int i = 0; i < 3; i++) encoding.add(-1);
        }
        if (width == 1) encoding.add(-1);
        for (int i = 0; i < width * height; i++) {
            encoding.add(i);
            if (width < 3 && (i + 1) % width == 0) {
                for (int j = 0; j < 3 - width && encoding.size() < 9; j++) {
                    encoding.add(-1);
                }
            }
        }
        while (encoding.size() < 9) encoding.add(-1);
        return encoding;
    }

    private static @Nullable PatternLayout matchRecipeEncoding(
            MechanicalCraftingRecipe recipe, List<BigItemStack> pattern, List<Integer> encoding) {
        if (pattern.size() != encoding.size()) return null;
        List<Ingredient> ingredients = recipe.getIngredients();
        List<BigItemStack> slots = new ArrayList<>(java.util.Collections.nCopies(ingredients.size(), null));
        for (int i = 0; i < encoding.size(); i++) {
            int slotIndex = encoding.get(i);
            BigItemStack supplied = pattern.get(i);
            if (slotIndex < 0) {
                if (!supplied.stack.isEmpty()) return null;
                continue;
            }
            Ingredient ingredient = ingredients.get(slotIndex);
            if (ingredient.isEmpty()) {
                if (!supplied.stack.isEmpty()) return null;
            } else if (supplied.stack.isEmpty() || supplied.count <= 0 || !ingredient.test(supplied.stack)) {
                return null;
            }
            slots.set(slotIndex, supplied);
        }
        return new PatternLayout(recipe.getWidth(), recipe.getHeight(), slots);
    }

    private static boolean samePlacement(PatternLayout first, PatternLayout other) {
        if (first.width() != other.width() || first.height() != other.height()) return false;
        for (int i = 0; i < first.slots().size(); i++) {
            BigItemStack a = first.slots().get(i), b = other.slots().get(i);
            if (a.count != b.count || !ItemStack.isSameItemSameComponents(a.stack, b.stack)) return false;
        }
        return true;
    }

    private record PatternLayout(int width, int height, List<BigItemStack> slots) {
    }

    /**
     * 在整条连接链条里寻找一块宽为width、高为height、内部完全填满（不缺
     * 格子）的矩形区域。找到时按行优先顺序（与pattern的下标顺序完全对应）
     * 返回这些合成器，找不到时返回 null。
     * <p>
     * 存在多块候选区域、或者链条本身比 n 大出不止一圈时，优先选择最靠
     * 左上角的一块——先比较区域左上角所在的行，行相同再比较列，行列的
     * 参照系是整条链条自己的左上角，不是世界坐标原点。
     * <p>
     * 行的方向取自世界坐标 Y（Y 越大越靠上，与 {@link BatchConnectedInputHandler.ConnectedInput#getInventories}
     * 排序时"从上到下"的约定一致）；列的方向取自合成器朝向对应的
     * {@code compareAxis}（facing顺时针方向的那根水平轴），同样沿用
     * {@code getInventories} 排序时"从左到右"的符号约定，保证这里认定的
     * "左上角"和玩家在配方界面里看到的左上角是同一个方向。
     */
    private static List<BatchMechanicalCrafterBlockEntity.Inventory> findRectanglePlacement(
            BatchMechanicalCrafterBlockEntity crafter,
            List<BatchMechanicalCrafterBlockEntity.Inventory> inventories, int width, int height) {
        Direction facing = Direction.SOUTH;
        BlockState blockState = crafter.getBlockState();
        if (blockState.hasProperty(BatchMechanicalCrafterBlock.HORIZONTAL_FACING)) {
            facing = blockState.getValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING);
        }
        Direction.AxisDirection axisDirection = facing.getAxisDirection();
        Direction.Axis compareAxis = facing.getClockWise().getAxis();
        int modifier = axisDirection.getStep() * (compareAxis == Direction.Axis.Z ? -1 : 1);

        int maxY = Integer.MIN_VALUE;
        int minColRaw = Integer.MAX_VALUE;
        Map<BatchMechanicalCrafterBlockEntity.Inventory, int[]> rawCoords = new LinkedHashMap<>();
        for (BatchMechanicalCrafterBlockEntity.Inventory inv : inventories) {
            BlockPos p = inv.getBlockEntity().getBlockPos();
            int y = p.getY();
            int colRaw = modifier * compareAxis.choose(p.getX(), p.getY(), p.getZ());
            rawCoords.put(inv, new int[]{y, colRaw});
            maxY = Math.max(maxY, y);
            minColRaw = Math.min(minColRaw, colRaw);
        }

        Map<Long, BatchMechanicalCrafterBlockEntity.Inventory> grid = new HashMap<>();
        int maxRow = 0, maxCol = 0;
        for (Map.Entry<BatchMechanicalCrafterBlockEntity.Inventory, int[]> e : rawCoords.entrySet()) {
            int row = maxY - e.getValue()[0];
            int col = e.getValue()[1] - minColRaw;
            grid.put(gridKey(row, col), e.getKey());
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }

        for (int r0 = 0; r0 + height - 1 <= maxRow; r0++) {
            for (int c0 = 0; c0 + width - 1 <= maxCol; c0++) {
                List<BatchMechanicalCrafterBlockEntity.Inventory> candidate = new ArrayList<>(width * height);
                boolean valid = true;
                search:
                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        BatchMechanicalCrafterBlockEntity.Inventory inv = grid.get(gridKey(r0 + row, c0 + col));
                        if (inv == null || inv.getBlockEntity().covered) {
                            valid = false;
                            break search;
                        }
                        candidate.add(inv);
                    }
                }
                if (valid) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static long gridKey(int row, int col) {
        return ((long) row << 32) ^ (col & 0xffffffffL);
    }
}
