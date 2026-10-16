package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakTiming;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
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

    public enum RefuseReason {
        CHUNK_NOT_LOADED,
        AIR,
        UNBREAKABLE,
        OUT_OF_REACH
    }

    public sealed interface Begin permits Started, Refused, Protected {
    }

    public record Started() implements Begin {
    }

    public record Refused(RefuseReason reason, String message) implements Begin {
    }

    public record Protected(PolicyId policy, String reason) implements Begin {
    }

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
        return begin(target, false);
    }

    public Begin begin(BlockPos target, boolean tended) {
        cancel();
        Level level = spirit.level();
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return new Refused(RefuseReason.CHUNK_NOT_LOADED, "чанк с блоком " + target.toShortString() + " не загружен: дух не грузит чанки ради ломки");
        }
        BlockState targetState = level.getBlockState(target);
        if (targetState.isAir()) {
            return new Refused(RefuseReason.AIR, "в " + target.toShortString() + " воздух: ломать нечего");
        }
        double hardness = targetState.getDestroySpeed(level, target);
        if (hardness < 0) {
            return new Refused(RefuseReason.UNBREAKABLE, "блок " + blockId(targetState) + " в " + target.toShortString()
                    + " неразрушим: твёрдость " + hardness);
        }
        double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(target));
        if (distance > REACH) {
            return new Refused(RefuseReason.OUT_OF_REACH, "блок в " + target.toShortString() + " дальше " + REACH + " от глаз духа ("
                    + distance + "): сначала подойти");
        }
        BlockProtection.Decision decision = tended ? spirit.protectionGate().checkHarvest(target, targetState)
                : spirit.protectionGate().checkBreak(target, targetState);
        if (decision instanceof BlockProtection.Refused refused) {
            return new Protected(refused.policy(), refused.reason());
        }
        pos = target.immutable();
        state = targetState;
        choice = ToolChoice.best(toolOptions(targetState));
        tool = choice.slot() == ToolChoice.HAND ? ItemStack.EMPTY
                : choice.slot() == ToolChoice.MAIN_HAND ? spirit.getMainHandItem()
                        : spirit.getInventory().getItem(choice.slot());
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
        return ToolChoice.best(toolOptions(targetState)).dropsAllowed();
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
        options.add(new ToolOption(ToolChoice.HAND, ToolChoice.HAND_SPEED, !needsTool));
        ItemStack hand = spirit.getMainHandItem();
        if (!hand.isEmpty()) {
            options.add(new ToolOption(ToolChoice.MAIN_HAND, hand.getDestroySpeed(targetState),
                    !needsTool || hand.isCorrectToolForDrops(targetState)));
        }
        Container inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                options.add(new ToolOption(slot, stack.getDestroySpeed(targetState),
                        !needsTool || stack.isCorrectToolForDrops(targetState)));
            }
        }
        return options;
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
            usedTool.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
            toolBroke = usedTool.isEmpty();
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
