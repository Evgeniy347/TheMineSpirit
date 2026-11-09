package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakTiming;
import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.mining.ToolSuffix;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.SelfRescue;
import com.theminespirit.core.tactics.adapter.Visibility;
import com.theminespirit.core.town.PlotProtection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
    private static final int NO_FORCED_SLOT = Integer.MIN_VALUE;

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
    private boolean toolBroke;
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
        this.ground = groundFor(intent);
        if (escaping) {
            BlockPos feet = spirit.blockPosition();
            if (SelfRescue.mayBreakAtFeet(
                    new BlockCoord(target.getX(), target.getY(), target.getZ()),
                    new BlockCoord(feet.getX(), feet.getY(), feet.getZ()))) {
                this.ground = PlotProtection.Ground.SELF_RESCUE;
            }
        }
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
                    "блок " + BreakNames.blockId(targetState) + " в " + target.toShortString() + " неразрушим: "
                            + hardness);
        }
        if (reachLimited) {
            double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(target));
            if (distance > REACH) {
                return new Refused(
                        RefuseReason.OUT_OF_REACH,
                        "блок " + target.toShortString() + " дальше " + REACH + " от глаз (" + distance + "): подойти");
            }
        }
        boolean chop = intent == Intent.CHOP || intent == Intent.CHOP_HARVEST;
        if (!chop && BreakSight.visible(spirit, target, leavesPass) != Visibility.CLEAR) {
            return new Refused(
                    RefuseReason.NO_SIGHT,
                    "блок " + target.toShortString() + " не виден из глаз: подойти или убрать мешающий блок");
        }
        if (BreakProtection.check(spirit, intent, target, targetState, escaping, rescue)
                instanceof BlockProtection.Refused refused) {
            return new Protected(refused.policy(), refused.reason());
        }
        pos = target.immutable();
        state = targetState;
        ToolReserve reserve = MiningTables.toolReserve();
        if (targetState.requiresCorrectToolForDrops()) {
            replaceSpentHandTool(targetState, reserve);
        }
        int requestedSlot = forcedSlot;
        preferLuckPick(targetState);
        List<ToolOption> options = toolOptions(targetState);
        forcedSlot = requestedSlot;
        choice = ToolChoice.best(options, reserve, MiningTables.repairTiers());
        if (ToolChoice.shouldRefuseForLackOfTool(choice, options)) {
            return new Refused(RefuseReason.NEED_TOOL, BreakNames.needToolReason(targetState));
        }
        boolean pickaxeBlock =
                targetState.requiresCorrectToolForDrops() && targetState.is(BlockTags.MINEABLE_WITH_PICKAXE);
        if (ToolChoice.refusesHandDig(choice, options, pickaxeBlock, escaping)) {
            return new Refused(
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
        return ToolChoice.best(toolOptions(targetState), MiningTables.toolReserve(), MiningTables.repairTiers())
                .dropsAllowed();
    }

    public String brokenToolSuffix() {
        return brokenToolSuffix;
    }

    private List<ToolOption> toolOptions(BlockState targetState) {
        boolean needsTool = targetState.requiresCorrectToolForDrops();
        List<ToolOption> options = new ArrayList<>();
        options.add(new ToolOption(
                ToolChoice.HAND,
                ToolChoice.HAND_SPEED,
                !needsTool,
                ToolChoice.UNWEARING,
                ToolChoice.UNWEARING,
                false,
                ""));
        ItemStack hand = spirit.getMainHandItem();
        if (!hand.isEmpty() && allowed(ToolChoice.MAIN_HAND)) {
            options.add(BreakTools.optionFor(ToolChoice.MAIN_HAND, hand, targetState, needsTool));
        }
        Container inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && allowed(slot)) {
                options.add(BreakTools.optionFor(slot, stack, targetState, needsTool));
            }
        }
        return options;
    }

    private void preferLuckPick(BlockState targetState) {
        if (forcedSlot != NO_FORCED_SLOT || MineOres.kindOf(BlockStates.idOf(targetState)) != MineOres.Kind.LUCK) {
            return;
        }
        MineOres.Decision decision =
                MineOres.decide(BlockStates.idOf(targetState), PickOptions.of(spirit, targetState));
        if (decision instanceof MineOres.Mine mine && mine.slot().isPresent()) {
            forcedSlot = mine.slot().getAsInt();
        }
    }

    private boolean allowed(int slot) {
        return forcedSlot == NO_FORCED_SLOT || forcedSlot == slot;
    }

    private void replaceSpentHandTool(BlockState targetState, ToolReserve reserve) {
        ItemStack hand = spirit.getMainHandItem();
        if (hand.isEmpty()
                || !BreakTools.optionFor(ToolChoice.MAIN_HAND, hand, targetState, true)
                        .spent(reserve, MiningTables.repairTiers())) {
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
        List<String> repairTiers = MiningTables.repairTiers();
        int bestSlot = -1;
        double bestSpeed = ToolChoice.HAND_SPEED;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.isCorrectToolForDrops(targetState)) {
                continue;
            }
            ToolOption option = BreakTools.optionFor(slot, stack, targetState, true);
            if (option.spent(reserve, repairTiers)) {
                continue;
            }
            if (option.speed() > bestSpeed || (option.speed() == bestSpeed && bestSlot < 0)) {
                bestSpeed = option.speed();
                bestSlot = slot;
            }
        }
        return bestSlot;
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
        if (level instanceof ServerLevel server) {
            BreakNotice.broken(server, brokenPos, broken, spirit, intent.name(), ground);
        }
        level.destroyBlock(brokenPos, false, spirit);
        spirit.hunger().onBlockBroken();
        if (dropsAllowed) {
            Block.dropResources(broken, level, brokenPos, blockEntity, spirit, usedTool);
        }
        if (!usedTool.isEmpty() && wearsTool) {
            String suffix = ToolSuffix.ofItem(
                    BuiltInRegistries.ITEM.getKey(usedTool.getItem()).toString());
            int breaksBefore = spirit.toolBreaks();
            usedTool.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
            toolBroke = usedTool.isEmpty() || spirit.toolBreaks() > breaksBefore;
            brokenToolSuffix = toolBroke ? suffix : "";
        } else {
            toolBroke = false;
            brokenToolSuffix = "";
        }
    }

    private void finish() {
        if (crackStage != NO_STAGE) {
            spirit.level().destroyBlockProgress(spirit.getId(), pos, NO_STAGE);
            crackStage = NO_STAGE;
        }
        active = false;
    }

    private static PlotProtection.Ground groundFor(Intent intent) {
        return switch (intent) {
            case DEMOLISH -> PlotProtection.Ground.PLAN_DEMOLISH;
            case REPAIR -> PlotProtection.Ground.REPAIR;
            case EXCAVATE -> PlotProtection.Ground.PLAN_EXCAVATE;
            case BREAK, HARVEST, CHOP, CHOP_HARVEST -> PlotProtection.Ground.NONE;
        };
    }
}
