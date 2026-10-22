package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakTiming;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PolicyId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BlockBreaker {
    public static final double REACH = 4.5;
    private static final int NO_STAGE = -1;
    private static final int MAX_STAGE = 9;
    private static final int STAGES = 10;

    public enum Progress {
        BREAKING,
        BROKEN,
        GONE
    }

    public enum Intent {
        BREAK,
        HARVEST,
        DEMOLISH
    }

    public enum RefuseReason {
        CHUNK_NOT_LOADED,
        AIR,
        UNBREAKABLE,
        OUT_OF_REACH,
        NEED_TOOL
    }

    public sealed interface Begin permits Started, Refused, Protected {}

    public record Started() implements Begin {}

    public record Refused(RefuseReason reason, String message) implements Begin {}

    public record Protected(PolicyId policy, String reason) implements Begin {}

    private final SpiritEntity spirit;
    private BlockPos pos;
    private BlockState state;
    private Choice choice;
    private ItemStack tool = ItemStack.EMPTY;
    private int requiredTicks;
    private int elapsed;
    private int crackStage = NO_STAGE;
    private boolean active;
    private boolean toolBroke;
    private int lastHitTick = -1;
    private int hitsThisTick;
    private int maxHitsPerTick;

    public BlockBreaker(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public Begin begin(BlockPos target) {
        return begin(target, Intent.BREAK, true);
    }

    public Begin begin(BlockPos target, boolean tended) {
        return begin(target, tended ? Intent.HARVEST : Intent.BREAK, true);
    }

    public Begin begin(BlockPos target, boolean tended, boolean demolish) {
        return begin(target, intent(tended, demolish), true);
    }

    public Begin beginAnyDistance(BlockPos target) {
        return begin(target, Intent.BREAK, false);
    }

    public Begin beginAnyDistance(BlockPos target, boolean tended) {
        return begin(target, tended ? Intent.HARVEST : Intent.BREAK, false);
    }

    private static Intent intent(boolean tended, boolean demolish) {
        return demolish ? Intent.DEMOLISH : tended ? Intent.HARVEST : Intent.BREAK;
    }

    private Begin begin(BlockPos target, Intent intent, boolean reachLimited) {
        cancel();
        Level level = spirit.level();
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return new Refused(
                    RefuseReason.CHUNK_NOT_LOADED,
                    "чанк с блоком " + target.toShortString() + " не загружен: дух не грузит чанки ради ломки");
        }
        BlockState targetState = level.getBlockState(target);
        if (targetState.isAir()) {
            return new Refused(RefuseReason.AIR, "в " + target.toShortString() + " воздух: ломать нечего");
        }
        double hardness = targetState.getDestroySpeed(level, target);
        if (hardness < 0) {
            return new Refused(
                    RefuseReason.UNBREAKABLE,
                    "блок " + blockId(targetState) + " в " + target.toShortString() + " неразрушим: твёрдость "
                            + hardness);
        }
        if (reachLimited) {
            double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(target));
            if (distance > REACH) {
                return new Refused(
                        RefuseReason.OUT_OF_REACH,
                        "блок в " + target.toShortString() + " дальше " + REACH + " от глаз духа (" + distance
                                + "): сначала подойти");
            }
        }
        BlockProtection.Decision decision = switch (intent) {
            case HARVEST -> spirit.protectionGate().checkHarvest(target, targetState);
            case DEMOLISH -> spirit.protectionGate().checkDemolish(target, targetState);
            case BREAK -> spirit.protectionGate().checkBreak(target, targetState);
        };
        if (decision instanceof BlockProtection.Refused refused) {
            return new Protected(refused.policy(), refused.reason());
        }
        pos = target.immutable();
        state = targetState;
        ToolReserve reserve = MiningTables.toolReserve();
        if (targetState.requiresCorrectToolForDrops()) {
            replaceSpentHandTool(targetState, reserve);
        }
        List<ToolOption> options = toolOptions(targetState);
        choice = ToolChoice.best(options, reserve);
        if (ToolChoice.shouldRefuseForLackOfTool(choice, options)) {
            return new Refused(RefuseReason.NEED_TOOL, needToolReason(targetState));
        }
        tool = choice.slot() == ToolChoice.HAND
                ? ItemStack.EMPTY
                : choice.slot() == ToolChoice.MAIN_HAND
                        ? spirit.getMainHandItem()
                        : spirit.getInventory().getItem(choice.slot());
        spirit.showInHand(tool);
        requiredTicks = BreakTiming.ticks(hardness, choice.speed(), choice.dropsAllowed());
        elapsed = 0;
        active = true;
        return new Started();
    }

    public Progress tick() {
        if (!active) {
            throw new IllegalStateException("Ломка не начата: сначала begin(...), иначе нечего ломать");
        }
        recordHit();
        Level level = spirit.level();
        boolean unloaded = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null;
        if (unloaded || !level.getBlockState(pos).is(state.getBlock())) {
            finish();
            return Progress.GONE;
        }
        elapsed++;
        SpiritGesture.keepWorking(spirit, pos);
        if (elapsed >= requiredTicks) {
            complete(level);
            return Progress.BROKEN;
        }
        showCrack(level, elapsed * STAGES / requiredTicks);
        return Progress.BREAKING;
    }

    public void cancel() {
        if (active) {
            finish();
        }
    }

    public ItemStack tool() {
        return tool.copy();
    }

    public int toolSlot() {
        return choice == null || choice.slot() < 0 ? -1 : choice.slot();
    }

    public boolean dropsAllowed() {
        return choice != null && choice.dropsAllowed();
    }

    public int crackStage() {
        return crackStage;
    }

    public int requiredTicks() {
        return requiredTicks;
    }

    public Choice choice() {
        return choice;
    }

    public boolean dropsAllowedFor(BlockState targetState) {
        Objects.requireNonNull(targetState, "Состояние блока не может быть null: по нему выбирается инструмент");
        return ToolChoice.best(toolOptions(targetState), MiningTables.toolReserve())
                .dropsAllowed();
    }

    public boolean toolBroke() {
        return toolBroke;
    }

    public int maxHitsPerTick() {
        return maxHitsPerTick;
    }

    private void recordHit() {
        int now = spirit.tickCount;
        if (now == lastHitTick) {
            hitsThisTick++;
        } else {
            lastHitTick = now;
            hitsThisTick = 1;
        }
        maxHitsPerTick = Math.max(maxHitsPerTick, hitsThisTick);
    }

    private List<ToolOption> toolOptions(BlockState targetState) {
        boolean needsTool = targetState.requiresCorrectToolForDrops();
        List<ToolOption> options = new ArrayList<>();
        options.add(new ToolOption(
                ToolChoice.HAND, ToolChoice.HAND_SPEED, !needsTool, ToolChoice.UNWEARING, ToolChoice.UNWEARING, false));
        ItemStack hand = spirit.getMainHandItem();
        if (!hand.isEmpty()) {
            options.add(optionFor(ToolChoice.MAIN_HAND, hand, targetState, needsTool));
        }
        Container inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                options.add(optionFor(slot, stack, targetState, needsTool));
            }
        }
        return options;
    }

    private static ToolOption optionFor(int slot, ItemStack stack, BlockState targetState, boolean needsTool) {
        int max = stack.isDamageableItem() ? stack.getMaxDamage() : ToolChoice.UNWEARING;
        int remaining = max > 0 ? max - stack.getDamageValue() : ToolChoice.UNWEARING;
        return new ToolOption(
                slot,
                stack.getDestroySpeed(targetState),
                !needsTool || stack.isCorrectToolForDrops(targetState),
                remaining,
                max,
                stack.isEnchanted());
    }

    private void replaceSpentHandTool(BlockState targetState, ToolReserve reserve) {
        ItemStack hand = spirit.getMainHandItem();
        if (hand.isEmpty()
                || !optionFor(ToolChoice.MAIN_HAND, hand, targetState, true).spent(reserve)) {
            return;
        }
        int source = bestSpareSlot(targetState, reserve);
        if (source < 0) {
            return;
        }
        ItemStack spare = spirit.getInventory().getItem(source).copy();
        spirit.getInventory().setItem(source, spirit.mainHandOwned() ? hand.copy() : ItemStack.EMPTY);
        spirit.holdInMainHand(spare);
    }

    private int bestSpareSlot(BlockState targetState, ToolReserve reserve) {
        Container inventory = spirit.getInventory();
        int bestSlot = -1;
        double bestSpeed = ToolChoice.HAND_SPEED;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.isCorrectToolForDrops(targetState)) {
                continue;
            }
            ToolOption option = optionFor(slot, stack, targetState, true);
            if (option.spent(reserve)) {
                continue;
            }
            if (option.speed() > bestSpeed || (option.speed() == bestSpeed && bestSlot < 0)) {
                bestSpeed = option.speed();
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static String needToolReason(BlockState targetState) {
        String kind = RequiredTier.kindOf(targetState);
        String what = kind.isEmpty() ? "нужен инструмент" : "нужен инструмент (" + kind + ")";
        return what + " для " + blockId(targetState) + ": подходящий есть, но изношен, запасного в инвентаре нет";
    }

    private void showCrack(Level level, int stage) {
        int clamped = Math.min(MAX_STAGE, stage);
        if (clamped != crackStage) {
            crackStage = clamped;
            level.destroyBlockProgress(spirit.getId(), pos, clamped);
        }
    }

    private void complete(Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState broken = state;
        BlockPos brokenPos = pos;
        ItemStack usedTool = tool;
        boolean dropsAllowed = choice.dropsAllowed();
        boolean wearsTool = broken.getDestroySpeed(level, brokenPos) != 0.0F;
        finish();
        level.destroyBlock(brokenPos, false, spirit);
        spirit.hunger().onBlockBroken();
        if (dropsAllowed) {
            Block.dropResources(broken, level, brokenPos, blockEntity, spirit, usedTool);
        }
        if (!usedTool.isEmpty() && wearsTool) {
            int breaksBefore = spirit.toolBreaks();
            usedTool.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
            toolBroke = usedTool.isEmpty() || spirit.toolBreaks() > breaksBefore;
        } else {
            toolBroke = false;
        }
    }

    private void finish() {
        if (crackStage != NO_STAGE) {
            spirit.level().destroyBlockProgress(spirit.getId(), pos, NO_STAGE);
            crackStage = NO_STAGE;
        }
        active = false;
    }

    private static String blockId(BlockState blockState) {
        return BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
    }
}
