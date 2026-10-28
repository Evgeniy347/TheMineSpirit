package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.script.StripLogParams;
import com.theminespirit.core.tactics.adapter.Visibility;
import com.theminespirit.neoforge.blocks.BlockReach;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jspecify.annotations.Nullable;

public final class StripLogScript implements ScriptInstance {
    private enum Phase {
        SEARCH,
        APPROACH,
        USE,
        DONE,
        FAILED
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final TreeSpecies species;
    private final int count;
    private final int radius;
    private final Set<Block> logs;
    private final BlockLocator.Search search;
    private final Deque<BlockPos> candidates = new ArrayDeque<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private Phase phase = Phase.SEARCH;
    private @Nullable BlockPos target;
    private @Nullable BlockReach reach;
    private int stripped;
    private String failureReason;

    public StripLogScript(SpiritEntity spirit, StripLogParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.species = params.species();
        this.count = params.count();
        this.radius = params.radius();
        this.logs = TreeBlocks.logs(List.of(params.species()));
        this.search = BlockLocator.search(level, spirit.blockPosition(), params.radius(), Set.copyOf(logs));
    }

    @Override
    public ScriptStatus tick() {
        if (phase == Phase.DONE) {
            return ScriptStatus.SUCCEEDED;
        }
        if (phase == Phase.FAILED) {
            return ScriptStatus.FAILED;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время обтёсывания брёвен");
        }
        if (stripped >= count) {
            phase = Phase.DONE;
            return ScriptStatus.SUCCEEDED;
        }
        if (axe().isEmpty()) {
            return fail("нет топора для обтёсывания брёвен породы " + species.id());
        }
        return switch (phase) {
            case SEARCH -> searchTick();
            case APPROACH -> approachTick();
            case USE -> useTick();
            case DONE, FAILED -> ScriptStatus.RUNNING;
        };
    }

    private ScriptStatus searchTick() {
        BlockPos next = candidates.poll();
        if (next == null) {
            if (search.done()) {
                return fail("обтёсано брёвен " + stripped + " из " + count + ": брёвна породы " + species.id()
                        + " в радиусе " + radius + " закончились");
            }
            search.step(1).stream()
                    .filter(pos -> !skipped.contains(pos))
                    .filter(pos -> logs.contains(level.getBlockState(pos).getBlock()))
                    .forEach(candidates::add);
            return ScriptStatus.RUNNING;
        }
        if (skipped.contains(next) || !logs.contains(level.getBlockState(next).getBlock())) {
            return ScriptStatus.RUNNING;
        }
        target = next;
        reach = new BlockReach(spirit, new BlockCoord(next.getX(), next.getY(), next.getZ()));
        phase = Phase.APPROACH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approachTick() {
        BlockReach walk = reach;
        BlockPos pos = target;
        if (walk == null || pos == null) {
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        if (walk.inReach()) {
            phase = Phase.USE;
            return ScriptStatus.RUNNING;
        }
        if (walk.stuck()) {
            skipTarget();
            return ScriptStatus.RUNNING;
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus useTick() {
        BlockPos pos = target;
        if (pos == null) {
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        BlockState before = level.getBlockState(pos);
        if (!logs.contains(before.getBlock())) {
            skipTarget();
            return ScriptStatus.RUNNING;
        }
        BlockProtection.Decision allowed = spirit.protectionGate().checkUse(pos);
        if (allowed instanceof BlockProtection.Refused) {
            skipTarget();
            return ScriptStatus.RUNNING;
        }
        BlockProtection.Decision breaking = spirit.protectionGate().checkBreak(pos, before);
        if (breaking instanceof BlockProtection.Refused) {
            skipTarget();
            return ScriptStatus.RUNNING;
        }
        if (spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > BlockBreaker.REACH) {
            skipTarget();
            return ScriptStatus.RUNNING;
        }
        if (BreakSight.visible(spirit, pos, false) != Visibility.CLEAR) {
            skipTarget();
            return ScriptStatus.RUNNING;
        }
        ItemStack axe = axe().orElse(ItemStack.EMPTY);
        if (axe.isEmpty()) {
            return fail("топор сломался во время обтёсывания: обтёсано " + stripped + " из " + count);
        }
        axe.getItem()
                .useOn(new UseOnContext(
                        level,
                        presser(),
                        InteractionHand.MAIN_HAND,
                        axe,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        BlockState after = level.getBlockState(pos);
        if (!strippedBlock(after)) {
            skipTarget();
            return ScriptStatus.RUNNING;
        }
        SpiritGesture.workOn(spirit, pos);
        breakStripped(pos, after, axe);
        stripped++;
        resetTarget();
        return ScriptStatus.RUNNING;
    }

    private void breakStripped(BlockPos pos, BlockState stripped, ItemStack axe) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ServerLevel server = (ServerLevel) level;
        level.destroyBlock(pos, false, spirit);
        stripped.getBlock().playerDestroy(server, presser(), pos, stripped, blockEntity, axe);
        if (axe.isDamageableItem()) {
            axe.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
        }
    }

    private void resetTarget() {
        target = null;
        reach = null;
        phase = Phase.SEARCH;
    }

    private void skipTarget() {
        if (target != null) {
            skipped.add(target);
        }
        resetTarget();
    }

    private Player presser() {
        Player presser = FakePlayerFactory.getMinecraft((ServerLevel) level);
        presser.moveTo(spirit.getX(), spirit.getY(), spirit.getZ(), spirit.getYRot(), spirit.getXRot());
        return presser;
    }

    private Optional<ItemStack> axe() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    private static boolean strippedBlock(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_");
    }

    private ScriptStatus fail(String reason) {
        cancel();
        failureReason = reason;
        phase = Phase.FAILED;
        return ScriptStatus.FAILED;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(stripped, count));
    }

    @Override
    public Optional<ScriptResult> result() {
        return phase == Phase.DONE
                ? Optional.of(new ScriptResult.Text("обтёсано брёвен " + stripped + " из " + count))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        if (reach != null) {
            reach.cancel();
        }
    }

    @Override
    public void suspend() {
        if (reach != null) {
            reach.suspend();
        }
    }

    @Override
    public @Nullable String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of("phase", phase.name(), "stripped", stripped);
    }
}
