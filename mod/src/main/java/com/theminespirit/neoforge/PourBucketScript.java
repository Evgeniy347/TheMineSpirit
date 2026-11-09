package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.script.FluidKind;
import com.theminespirit.core.script.PourBucketParams;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class PourBucketScript implements ScriptInstance {
    private static final double ARRIVAL = 2.5;
    private static final double REACH = 4.5;

    private enum Phase {
        APPROACH,
        POUR
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final FluidKind fluid;
    private final BlockPos target;
    private final Approach approach;
    private Phase phase = Phase.APPROACH;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    PourBucketScript(SpiritEntity spirit, PourBucketParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.fluid = params.fluid();
        this.target = new BlockPos(params.x(), params.y(), params.z());
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время выливания " + fluid.id());
        }
        return switch (phase) {
            case APPROACH -> approachStep();
            case POUR -> pour();
        };
    }

    private ScriptStatus approachStep() {
        BlockState state = level.getBlockState(target);
        if (state.is(block())) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (!state.isAir()) {
            return fail("клетка " + target.toShortString() + " занята блоком "
                    + BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                    + ": лить " + fluid.id() + " некуда");
        }
        if (!approach.isActive()) {
            approach.begin(Vec3.atCenterOf(target), ARRIVAL);
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                if (distanceTo(target) > REACH) {
                    yield fail("к клетке " + target.toShortString() + " не дотянуться: далеко для ведра");
                }
                phase = Phase.POUR;
                yield ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> fail("не подошёл к клетке " + target.toShortString() + ": " + approach.refusal());
        };
    }

    private ScriptStatus pour() {
        BlockState state = level.getBlockState(target);
        if (state.is(block())) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (!state.isAir()) {
            return fail("клетка " + target.toShortString() + " занята: лить " + fluid.id() + " некуда");
        }
        BlockProtection.Decision allowed = spirit.protectionGate().checkPlace(target);
        if (allowed instanceof BlockProtection.Refused refused) {
            return fail(refused.reason());
        }
        if (!takeFilledBucket()) {
            return fail("ведра " + fluid.bucketItem() + " в инвентаре нет: лить нечего");
        }
        SpiritBlocks.place((ServerLevel) spirit.level(), target, block().defaultBlockState(), spirit, "POUR_BUCKET");
        giveEmptyBucket();
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    private boolean takeFilledBucket() {
        ItemStack filled = new ItemStack(fluid == FluidKind.WATER ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).is(filled.getItem())) {
                spirit.getInventory().removeItem(slot, 1);
                return true;
            }
        }
        ItemStack hand = spirit.getMainHandItem();
        if (hand.is(filled.getItem()) && spirit.mainHandOwned()) {
            spirit.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private void giveEmptyBucket() {
        ItemStack empty = new ItemStack(Items.BUCKET);
        if (!spirit.getInventory().addItem(empty).isEmpty()) {
            spirit.spawnAtLocation(empty);
        }
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
                ? Optional.of(new ScriptResult.Data(
                        Map.of("fluid", fluid.id(), "x", target.getX(), "y", target.getY(), "z", target.getZ())))
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
}
