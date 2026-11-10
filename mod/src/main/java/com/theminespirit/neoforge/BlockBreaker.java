package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakTiming;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.SelfRescue;
import com.theminespirit.core.town.PlotProtection;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockBreaker {
    public static final double REACH = 4.5;
    static final int NO_FORCED_SLOT = Integer.MIN_VALUE;
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
        CHOP,
        CHOP_HARVEST,
        DEMOLISH,
        REPAIR,
        EXCAVATE
    }

    public enum RefuseReason {
        CHUNK_NOT_LOADED,
        AIR,
        UNBREAKABLE,
        OUT_OF_REACH,
        NO_SIGHT,
        NEED_TOOL
    }

    public sealed interface Begin permits Started, Refused, Protected {}

    public record Started() implements Begin {}

    public record Refused(RefuseReason reason, String message) implements Begin {}

    public record Protected(PolicyId policy, String reason) implements Begin {}

    private final SpiritEntity spirit;
    private final boolean escaping;
    private final boolean rescue;
    private BlockPos pos;
    private BlockState state;
    private Choice choice;
    private ItemStack tool = ItemStack.EMPTY;
    private int requiredTicks;
    private int elapsed;
    private int crackStage = NO_STAGE;
    private boolean active;
    private String brokenToolSuffix = "";
    private int forcedSlot = NO_FORCED_SLOT;
    private Intent intent = Intent.BREAK;
    private PlotProtection.Ground ground = PlotProtection.Ground.NONE;

    public BlockBreaker(SpiritEntity spirit) {
        this(spirit, false, false);
    }

    private BlockBreaker(SpiritEntity spirit, boolean escaping, boolean rescue) {
        this.spirit = spirit;
        this.escaping = escaping;
        this.rescue = rescue;
    }

    public static BlockBreaker escaping(SpiritEntity spirit, boolean rescue) {
        return new BlockBreaker(spirit, true, rescue);
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

    public Begin beginWithSlot(BlockPos target, int slot) {
        forcedSlot = slot;
        try {
            return begin(target, Intent.BREAK, true);
        } finally {
            forcedSlot = NO_FORCED_SLOT;
        }
    }

    public Begin beginAnyDistance(BlockPos target) {
        return begin(target, Intent.BREAK, false);
    }

    public Begin beginAnyDistance(BlockPos target, boolean tended) {
        return begin(target, tended ? Intent.HARVEST : Intent.BREAK, false);
    }

    public Begin beginChop(BlockPos target, boolean tended) {
        return begin(target, tended ? Intent.CHOP_HARVEST : Intent.CHOP, false, true);
    }

    public Begin beginRepair(BlockPos target) {
        return begin(target, Intent.REPAIR, true);
    }

    public Begin beginExcavate(BlockPos target) {
        return begin(target, Intent.EXCAVATE, true);
    }

    private static Intent intent(boolean tended, boolean demolish) {
        return demolish ? Intent.DEMOLISH : tended ? Intent.HARVEST : Intent.BREAK;
    }

    Begin begin(BlockPos target, Intent intent, boolean reachLimited) {
        return begin(target, intent, reachLimited, false);
    }

    Begin begin(BlockPos target, Intent intent, boolean reachLimited, boolean leavesPass) {
        cancel();
        this.intent = intent;
        BreakRefusal refusals = new BreakRefusal(spirit, intent);
        PlotProtection.Ground ground = BreakRefusal.groundFor(intent);
        if (escaping) {
            BlockPos feet = spirit.blockPosition();
            if (SelfRescue.mayBreakAtFeet(
                    new BlockCoord(target.getX(), target.getY(), target.getZ()),
                    new BlockCoord(feet.getX(), feet.getY(), feet.getZ()))) {
                ground = PlotProtection.Ground.SELF_RESCUE;
            }
        }
        this.ground = ground;
        Begin refusal = BreakCheck.refusal(
                new BreakCheck.Request(spirit, target, intent, reachLimited, leavesPass, escaping, rescue, refusals));
        if (refusal != null) {
            return refusal;
        }
        Level level = spirit.level();
        BlockState targetState = level.getBlockState(target);
        double hardness = targetState.getDestroySpeed(level, target);
        pos = target.immutable();
        state = targetState;
        ToolReserve reserve = MiningTables.toolReserve();
        if (targetState.requiresCorrectToolForDrops()) {
            BreakToolPick.replaceSpentHandTool(spirit, targetState, reserve);
        }
        int requestedSlot = forcedSlot;
        forcedSlot = BreakToolPick.luckSlot(spirit, forcedSlot, targetState);
        List<ToolOption> options = BreakToolPick.options(spirit, forcedSlot, targetState);
        forcedSlot = requestedSlot;
        choice = ToolChoice.best(options, reserve, MiningTables.repairTiers());
        if (ToolChoice.shouldRefuseForLackOfTool(choice, options)) {
            return refusals.refused(target, RefuseReason.NEED_TOOL, BreakNames.needToolReason(targetState));
        }
        boolean pickaxeBlock =
                targetState.requiresCorrectToolForDrops() && targetState.is(BlockTags.MINEABLE_WITH_PICKAXE);
        if (ToolChoice.refusesHandDig(choice, options, pickaxeBlock, escaping)) {
            return refusals.refused(
                    target,
                    RefuseReason.NEED_TOOL,
                    "нужна кирка для " + BreakNames.blockId(targetState)
                            + ": руками без дропа не копаем — кирки нет, дух не замурован");
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

    public Choice choice() {
        return choice;
    }

    public boolean dropsAllowedFor(BlockState targetState) {
        Objects.requireNonNull(targetState, "Состояние блока не может быть null: по нему выбирается инструмент");
        return ToolChoice.best(
                        BreakToolPick.options(spirit, forcedSlot, targetState),
                        MiningTables.toolReserve(),
                        MiningTables.repairTiers())
                .dropsAllowed();
    }

    public String brokenToolSuffix() {
        return brokenToolSuffix;
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
        BreakDestroy.Result result = BreakDestroy.after(
                spirit,
                level,
                new BreakDestroy.Break(
                        brokenPos, broken, blockEntity, usedTool, dropsAllowed, wearsTool, intent.name(), ground));
        brokenToolSuffix = result.suffix();
    }

    private void finish() {
        if (crackStage != NO_STAGE) {
            spirit.level().destroyBlockProgress(spirit.getId(), pos, NO_STAGE);
            crackStage = NO_STAGE;
        }
        active = false;
    }
}
