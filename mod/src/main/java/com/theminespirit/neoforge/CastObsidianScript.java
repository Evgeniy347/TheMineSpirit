package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CastObsidianParams;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class CastObsidianScript implements ScriptInstance {
    private static final double ARRIVAL = 2.5;
    private static final double REACH = 4.5;
    private static final double PICKUP = 3.0;
    private static final int SLICES = 4;

    private enum Phase {
        SEARCH,
        APPROACH,
        CAST,
        MINE,
        BREAKING
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final CastObsidianParams params;
    private final Approach approach;
    private final BlockBreaker breaker;
    private Phase phase = Phase.SEARCH;
    private BlockLocator.Search search;
    private BlockPos target;
    private int cast;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    CastObsidianScript(SpiritEntity spirit, CastObsidianParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.params = params;
        this.approach = new Approach(spirit);
        this.breaker = new BlockBreaker(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время добычи обсидиана");
        }
        return switch (phase) {
            case SEARCH -> search();
            case APPROACH -> approach();
            case CAST -> cast();
            case MINE -> beginMine();
            case BREAKING -> breakTick();
        };
    }

    private ScriptStatus search() {
        if (search == null) {
            search = BlockLocator.search(level, spirit.blockPosition(), params.radius(), Set.of(Blocks.LAVA));
            target = null;
        }
        for (BlockPos found : search.step(SLICES)) {
            if (isLavaSource(found)) {
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
        return cast > 0
                ? succeed()
                : fail("источника лавы в радиусе " + params.radius() + " нет: ведро воды лить не на что");
    }

    private ScriptStatus approach() {
        if (target == null || !isLavaSource(target)) {
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        if (!approach.isActive()) {
            approach.begin(Vec3.atCenterOf(target), ARRIVAL);
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                phase = distanceTo(target) <= REACH ? Phase.CAST : Phase.APPROACH;
                yield ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> fail("не подошёл к источнику лавы " + side(target) + ": " + approach.refusal());
        };
    }

    private ScriptStatus cast() {
        if (target == null || !isLavaSource(target)) {
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        if (!takeWaterBucket()) {
            return fail("нет ведра воды: лить на лаву нечем (осталось " + (params.count() - cast) + ")");
        }
        level.setBlock(target, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        phase = Phase.MINE;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus beginMine() {
        if (target == null || !level.getBlockState(target).is(Blocks.OBSIDIAN)) {
            phase = Phase.SEARCH;
            return ScriptStatus.RUNNING;
        }
        BlockBreaker.Begin begin = breaker.begin(target, true);
        if (begin instanceof BlockBreaker.Refused refused) {
            return fail("обсидиан не сломать: " + refused.message());
        }
        if (begin instanceof BlockBreaker.Protected protectedBlock) {
            return fail("обсидиан под защитой: " + protectedBlock.reason());
        }
        phase = Phase.BREAKING;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus breakTick() {
        return switch (breaker.tick()) {
            case BREAKING -> ScriptStatus.RUNNING;
            case BROKEN -> {
                pickupObsidian();
                cast++;
                target = null;
                phase = Phase.SEARCH;
                yield cast >= params.count() ? succeed() : ScriptStatus.RUNNING;
            }
            case GONE -> {
                target = null;
                phase = Phase.SEARCH;
                yield ScriptStatus.RUNNING;
            }
        };
    }

    private void pickupObsidian() {
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class,
                spirit.getBoundingBox().inflate(PICKUP),
                drop -> !drop.isRemoved() && drop.getItem().is(Items.OBSIDIAN));
        for (ItemEntity drop : drops) {
            ItemStack rest = spirit.getInventory().addItem(drop.getItem().copy());
            if (rest.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(rest);
            }
        }
    }

    private boolean takeWaterBucket() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).is(Items.WATER_BUCKET)) {
                spirit.getInventory().removeItem(slot, 1);
                giveBucket();
                return true;
            }
        }
        ItemStack hand = spirit.getMainHandItem();
        if (hand.is(Items.WATER_BUCKET) && spirit.mainHandOwned()) {
            spirit.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            return true;
        }
        return false;
    }

    private void giveBucket() {
        ItemStack bucket = new ItemStack(Items.BUCKET);
        if (!spirit.getInventory().addItem(bucket).isEmpty()) {
            spirit.spawnAtLocation(bucket);
        }
    }

    private boolean isLavaSource(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.LAVA) && state.getFluidState().isSource();
    }

    private double distanceTo(BlockPos pos) {
        return Math.sqrt(spirit.position().distanceToSqr(Vec3.atCenterOf(pos)));
    }

    private static BlockCoord side(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private ScriptStatus succeed() {
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        breaker.cancel();
        approach.cancel();
        return outcome;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(cast, params.count()));
    }

    @Override
    public void cancel() {
        breaker.cancel();
        approach.cancel();
        outcome = ScriptStatus.CANCELLED;
    }

    @Override
    public void suspend() {
        breaker.cancel();
        approach.cancel();
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(
                "phase",
                phase.name(),
                "cast",
                cast,
                "x",
                target == null ? 0 : target.getX(),
                "y",
                target == null ? 0 : target.getY(),
                "z",
                target == null ? 0 : target.getZ());
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        cast = state.get("cast") instanceof Number number ? number.intValue() : 0;
        target = state.get("x") instanceof Number x
                        && state.get("y") instanceof Number y
                        && state.get("z") instanceof Number z
                ? new BlockPos(x.intValue(), y.intValue(), z.intValue())
                : null;
        phase = target == null ? Phase.SEARCH : Phase.APPROACH;
    }
}
