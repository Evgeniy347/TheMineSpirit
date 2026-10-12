package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class HarvestSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        FIND,
        PICK,
        APPROACH,
        BREAK,
        COLLECT
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 4;

    private final SpiritEntity spirit;
    private final Level level;
    private final HarvestTargets targets;
    private final String label;
    private final int count;
    private final int radius;
    private final boolean skipWithoutTool;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final TargetBuffer buffer = new TargetBuffer();

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private BlockCoord currentTarget;
    private DropCollector collector;

    private int mined;
    private int unsafe;
    private int noStand;
    private int noTool;
    private int planCalls;
    private int collectTicks;
    private int collectRuns;
    private int ticks;
    private int entityQueries;

    private HarvestSession(SpiritEntity spirit, HarvestTargets targets, String label, int count, int radius,
            boolean skipWithoutTool) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.targets = targets;
        this.label = label;
        this.count = count;
        this.radius = radius;
        this.skipWithoutTool = skipWithoutTool;
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
    }

    public static HarvestSession mineVein(SpiritEntity spirit, OreKind ore, int count, int radius) {
        Set<Block> ores = BlockSets.resolve(IdFilter.of(List.copyOf(OreKind.blocks(ore))));
        return new HarvestSession(spirit, new VeinTargets(spirit.level(), spirit.blockPosition(), radius, ores),
                ore.name().toLowerCase(Locale.ROOT), count, radius, ore == OreKind.ANY);
    }

    public static HarvestSession gather(SpiritEntity spirit, String block, int count, int radius) {
        return new HarvestSession(spirit, new GatherTargets(spirit.level(), spirit.blockPosition(), radius,
                IdFilter.of(List.of(block))), block, count, radius, block.indexOf('*') >= 0);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время добычи");
        }
        if (mined >= count && collector == null) {
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        ticks++;
        switch (state) {
            case FIND -> find();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public int mined() {
        return mined;
    }

    public Map<String, Object> saveState() {
        return Map.of("mined", mined);
    }

    public void restoreState(Map<String, Object> state) {
        Object value = state.get("mined");
        if (value instanceof Number number) {
            mined = Math.max(mined, number.intValue());
        }
    }

    public int planCalls() {
        return planCalls;
    }

    public int safetyReads() {
        return safety.reads();
    }

    public int slicesUsed() {
        return targets.slicesUsed();
    }

    public int collectTicks() {
        return collectTicks;
    }

    public int collectRuns() {
        return collectRuns;
    }

    public int totalTicks() {
        return ticks;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int maxSlicesPerTick() {
        return targets.maxSlicesPerTick();
    }

    public int maxBreakerHitsPerTick() {
        return breaker.maxHitsPerTick();
    }

    private void find() {
        if (targets.refusalReason() != null) {
            fail(targets.refusalReason());
            return;
        }
        if (!buffer.needsRefill()) {
            state = State.PICK;
            return;
        }
        buffer.offer(targets.advance());
        if (!buffer.isEmpty()) {
            state = State.PICK;
            return;
        }
        if (targets.done()) {
            fail(stopReason());
        }
    }

    private void pick() {
        if (buffer.isEmpty()) {
            state = State.FIND;
            return;
        }
        if (buffer.needsRefill() && !targets.done()) {
            buffer.offer(targets.advance());
        }
        planCalls++;
        BlockPos from = spirit.blockPosition();
        BlockCoord origin = new BlockCoord(from.getX(), from.getY(), from.getZ());
        MiningPlanner.Outcome result = MiningPlanner.next(origin, buffer.targets(origin), safety);
        if (result instanceof MiningPlanner.Exhausted exhausted) {
            unsafe += exhausted.unsafe();
            noStand += exhausted.noStand();
            buffer.clearActive();
            state = State.FIND;
            return;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        BlockState targetState = level.getBlockState(toPos(next.target()));
        if (!breaker.dropsAllowedFor(targetState)) {
            buffer.remove(next.target());
            if (breaker.toolBroke()) {
                fail("инструмент сломался: добыто " + mined + " из " + count);
                return;
            }
            if (mined == 0 && !skipWithoutTool) {
                fail(tierReason(targetState));
                return;
            }
            noTool++;
            return;
        }
        buffer.remove(next.target());
        currentTarget = next.target();
        approach.begin(new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5),
                APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> prepareBreak();
            case STUCK, TIMEOUT -> {
                noStand++;
                currentTarget = null;
                state = State.PICK;
            }
        }
    }

    private void prepareBreak() {
        if (!(MiningSafety.canBreak(currentTarget, safety) instanceof BreakSafety.Safe)) {
            unsafe++;
            currentTarget = null;
            state = State.PICK;
            return;
        }
        if (!(breaker.begin(toPos(currentTarget)) instanceof BlockBreaker.Started)) {
            noStand++;
            currentTarget = null;
            state = State.PICK;
            return;
        }
        state = State.BREAK;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {
            }
            case BROKEN -> {
                mined++;
                collectRuns++;
                collector = new DropCollector(spirit, IdFilter.matchAll(), currentTarget, COLLECT_RADIUS,
                        PickupTuning.DEFAULT);
                state = State.COLLECT;
            }
            case GONE -> {
                currentTarget = null;
                state = State.PICK;
            }
        }
    }

    private void collectTick() {
        collectTicks++;
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        entityQueries += collector.entityQueries();
        if (collected == DropCollector.State.FAILED) {
            fail("не удалось подобрать дроп у " + currentTarget + ": " + collector.failureReason());
            return;
        }
        collector = null;
        currentTarget = null;
        if (mined >= count) {
            outcome = Outcome.SUCCEEDED;
        } else {
            state = State.PICK;
        }
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }

    private String stopReason() {
        return summary() + "; подходящих блоков в радиусе " + radius + " не осталось";
    }

    private String summary() {
        return "добыто " + mined + " из " + count
                + "; пропущено небезопасных: " + unsafe
                + ", без места для духа: " + noStand
                + ", без подходящего инструмента: " + noTool;
    }

    private String tierReason(BlockState state) {
        String tier = RequiredTier.of(state);
        if (tier.isEmpty()) {
            return "для " + label + " нет подходящего инструмента в инвентаре";
        }
        return "для " + label + " нужна " + tier + " или лучше; в инвентаре подходящего инструмента нет";
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}