package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.FillBucketParams;
import com.theminespirit.core.script.FluidKind;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

final class FillBucketScript implements ScriptInstance {
    private static final double ARRIVAL = 2.5;
    private static final double REACH = 4.5;
    private static final int SLICES = 4;

    private enum Phase {
        SEARCH,
        APPROACH,
        FILL
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final FluidKind fluid;
    private final int radius;
    private final Approach approach;
    private Phase phase = Phase.SEARCH;
    private BlockLocator.Search search;
    private BlockPos target;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    FillBucketScript(SpiritEntity spirit, FillBucketParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.fluid = params.fluid();
        this.radius = params.radius();
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время набора " + fluid.id());
        }
        return switch (phase) {
            case SEARCH -> search();
            case APPROACH -> approachStep();
            case FILL -> fill();
        };
    }

    private ScriptStatus search() {
        if (emptyBucketSlot() < 0) {
            return fail("пустого ведра в инвентаре нет: набирать " + fluid.id() + " нечем");
        }
        if (search == null) {
            search = BlockLocator.search(level, spirit.blockPosition(), radius, Set.of(block()));
        }
        for (BlockPos found : search.step(SLICES)) {
            if (isSource(found)) {
                target = found.immutable();
                search = null;
                phase = Phase.APPROACH;
                return ScriptStatus.RUNNING;
            }
        }
        if (!search.done()) {
            return ScriptStatus.RUNNING;
        }
        search = null;
        return fail("источника " + fluid.id() + " в радиусе " + radius + " нет");
    }

    private ScriptStatus approachStep() {
        if (target == null || !isSource(target)) {
            target = null;
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        if (!approach.isActive()) {
            approach.begin(Vec3.atCenterOf(target), ARRIVAL);
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                phase = distanceTo(target) <= REACH ? Phase.FILL : Phase.APPROACH;
                yield ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> fail("не подошёл к источнику " + fluid.id() + ": " + approach.refusal());
        };
    }

    private ScriptStatus fill() {
        if (target == null || !isSource(target)) {
            target = null;
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        if (emptyBucketSlot() < 0) {
            return fail("пустого ведра не осталось: набрать " + fluid.id() + " нечем");
        }
        if (!takeEmptyBucket()) {
            return fail("пустое ведро не удалось взять в руки");
        }
        giveFilled();
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    private boolean takeEmptyBucket() {
        int slot = emptyBucketSlot();
        if (slot >= 0) {
            spirit.getInventory().removeItem(slot, 1);
            return true;
        }
        ItemStack hand = spirit.getMainHandItem();
        if (hand.is(Items.BUCKET) && spirit.mainHandOwned()) {
            spirit.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private void giveFilled() {
        ItemStack filled = new ItemStack(fluid == FluidKind.WATER ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
        if (!spirit.getInventory().addItem(filled).isEmpty()) {
            spirit.spawnAtLocation(filled);
        }
    }

    private int emptyBucketSlot() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).is(Items.BUCKET)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isSource(BlockPos pos) {
        return level.isLoaded(pos)
                && level.getFluidState(pos).isSource()
                && level.getFluidState(pos).is(fluidTag());
    }

    private TagKey<Fluid> fluidTag() {
        return fluid == FluidKind.WATER ? FluidTags.WATER : FluidTags.LAVA;
    }

    private Block block() {
        return fluid == FluidKind.WATER ? Blocks.WATER : Blocks.LAVA;
    }

    private double distanceTo(BlockPos pos) {
        return Math.sqrt(spirit.position().distanceToSqr(Vec3.atCenterOf(pos)));
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        approach.cancel();
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(outcome == ScriptStatus.SUCCEEDED ? 1 : 0, 1));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(Map.of("fluid", fluid.id(), "bucket", fluid.bucketItem())))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.cancel();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(
                "phase",
                phase.name(),
                "x",
                target == null ? 0 : target.getX(),
                "y",
                target == null ? 0 : target.getY(),
                "z",
                target == null ? 0 : target.getZ());
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get("x") instanceof Number x
                && state.get("y") instanceof Number y
                && state.get("z") instanceof Number z
                && (x.intValue() != 0 || y.intValue() != 0 || z.intValue() != 0)) {
            target = new BlockPos(x.intValue(), y.intValue(), z.intValue());
        }
    }
}
