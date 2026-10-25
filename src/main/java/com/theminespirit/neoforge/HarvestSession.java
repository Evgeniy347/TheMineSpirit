package com.theminespirit.neoforge;

import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.InventorySpace;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.mining.ApproachFailureTracker;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.DropRetries;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.mining.ProgressWatchdog;
import com.theminespirit.core.mining.SoilLayer;
import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import com.theminespirit.neoforge.inventory.StackMapper;
import com.theminespirit.neoforge.inventory.UnloadScript;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
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
        COLLECT,
        DEPOSIT
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final double APPROACH_PROGRESS_STEP = 0.05;
    private static final int COLLECT_RADIUS = 4;

    private final SpiritEntity spirit;
    private final Level level;
    private final HarvestTargets targets;
    private final WorkKind kind;
    private final String label;
    private final int count;
    private final int radius;
    private final boolean skipWithoutTool;
    private final boolean vein;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final TargetBuffer buffer = new TargetBuffer();
    private final ProgressWatchdog watchdog = new ProgressWatchdog(CatalogLimits.HARVEST_NO_PROGRESS_TICKS);
    private final DropRetries dropRetries = new DropRetries(COLLECT_RADIUS);

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private BlockCoord currentTarget;
    private BlockCoord currentStand;
    private BlockCoord lastUnreachable;
    private double closestApproach = Double.MAX_VALUE;
    private final ApproachFailureTracker approachFailures = new ApproachFailureTracker();
    private final Set<BlockCoord> reachStandsTried = new HashSet<>();
    private DropCollector collector;
    private UnloadScript deposit;
    private boolean depositTried;
    private boolean depositMandatory;
    private boolean depositFreed;
    private String depositBlockedItem;
    private WorkTarget reserved;

    private int mined;
    private int unsafe;
    private int noStand;
    private int unreachable;
    private int noTool;
    private int protectedSkipped;
    private int planCalls;
    private int collectTicks;
    private int collectRuns;

    private int entityQueries;
    private final int surfaceY;
    private final SoilCells soil;

    private HarvestSession(SpiritEntity spirit, Spec spec) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.targets = spec.targets();
        this.kind = spec.kind();
        this.label = spec.label();
        this.count = spec.count();
        this.radius = spec.radius();
        this.skipWithoutTool = spec.skipWithoutTool();
        this.vein = spec.vein();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.surfaceY = spirit.blockPosition().getY();
        this.soil = new SoilCells(level);
    }

    public static HarvestSession mineVein(SpiritEntity spirit, OreKind ore, int count, int radius) {
        Set<Block> ores = BlockSets.resolve(IdFilter.of(List.copyOf(OreKind.blocks(ore))));
        return new HarvestSession(
                spirit,
                new Spec(
                        new VeinTargets(spirit.level(), spirit.blockPosition(), radius, ores),
                        WorkKind.VEIN,
                        ore.name().toLowerCase(Locale.ROOT),
                        count,
                        radius,
                        ore == OreKind.ANY,
                        true));
    }

    public static HarvestSession gather(SpiritEntity spirit, String block, int count, int radius) {
        return new HarvestSession(
                spirit,
                new Spec(
                        new GatherTargets(
                                spirit.level(),
                                spirit.blockPosition(),
                                radius,
                                IdFilter.of(SoilLayer.dirt(block) ? SoilLayer.TURF_BLOCKS : List.of(block)),
                                spirit.home().isPresent()),
                        WorkKind.GATHER,
                        block,
                        count,
                        radius,
                        IdFilter.isPattern(block),
                        false));
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время добычи");
        }
        if (mined >= count && collector == null) {
            if (dropRetries.isEmpty()) {
                outcome = Outcome.SUCCEEDED;
                return outcome;
            }
            beginRetryCollect();
        }

        watchdog.tick();
        if (watchdog.stalled()) {
            return fail(stallReason());
        }
        switch (state) {
            case FIND -> find();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
            case DEPOSIT -> depositTick();
        }
        return outcome;
    }

    public void cancel() {
        releaseReserved();
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        if (deposit != null) {
            deposit.cancel();
        }
    }

    public void suspend() {
        approach.suspend();
        if (state == State.BREAK) {
            breaker.cancel();
            buffer.offer(List.of(currentTarget));
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
        }
        if (collector != null) {
            collector.suspend();
        }
        if (deposit != null) {
            deposit.suspend();
        }
    }

    public int crackStage() {
        return breaker.crackStage();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(Math.min(mined, count), count);
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

    public int slicesUsed() {
        return targets.slicesUsed();
    }

    public int collectTicks() {
        return collectTicks;
    }

    public int collectRuns() {
        return collectRuns;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int maxSlicesPerTick() {
        return targets.maxSlicesPerTick();
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
        List<BlockCoord> candidates = buffer.targets(origin);
        if (kind == WorkKind.GATHER) {
            candidates = shallow(candidates);
            if (candidates.isEmpty()) {
                state = State.FIND;
                return;
            }
        }
        for (BlockCoord candidate : candidates) {
            boolean ownColumn = candidate.x() == origin.x() && candidate.z() == origin.z();
            if (SpiritReach.reachable(origin, candidate) && !ownColumn && (vein || exposed(candidate))) {
                if (acceptTarget(candidate)) {
                    currentStand = origin;
                    prepareBreak();
                }
                return;
            }
        }
        MiningPlanner.Outcome result =
                MiningPlanner.next(origin, candidates, safety, approachFailures.excludedStands());
        if (result instanceof MiningPlanner.Exhausted exhausted) {
            if ((exhausted.noStand() > 0 || exhausted.unreachable() > 0) && approachReachStand(origin, candidates)) {
                return;
            }
            unsafe += exhausted.unsafe();
            noStand += exhausted.noStand();
            unreachable += exhausted.unreachable();
            buffer.clearActive();
            state = State.FIND;
            return;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        if (kind == WorkKind.GATHER && !standReachable(next.stand())) {
            unreachable++;
            lastUnreachable = next.target();
            approachFailures.excludeStand(next.stand(), next.target());
            state = State.PICK;
            return;
        }
        if (!acceptTarget(next.target())) {
            return;
        }
        currentStand = next.stand();
        beginApproach(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5));
        state = State.APPROACH;
    }

    private List<BlockCoord> shallow(List<BlockCoord> candidates) {
        List<BlockCoord> kept = new ArrayList<>();
        for (BlockCoord candidate : candidates) {
            BlockState state = level.getBlockState(toPos(candidate));
            boolean loose = state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL);
            boolean refused =
                    state.is(BlockTags.DIRT) ? !soil.takeable(candidate) : loose && candidate.y() < surfaceY - 1;
            if (refused) {
                buffer.remove(candidate);
            } else {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private boolean standReachable(BlockCoord stand) {
        Path path = spirit.getNavigation().createPath(new BlockPos(stand.x(), stand.y(), stand.z()), 1);
        return path != null && path.canReach();
    }

    private void beginApproach(Vec3 point) {
        closestApproach = Double.MAX_VALUE;
        approach.begin(point, APPROACH_ARRIVAL);
    }

    private void noteApproachProgress() {
        if (currentStand == null) {
            return;
        }
        double distance = spirit.position()
                .distanceTo(new Vec3(currentStand.x() + 0.5, currentStand.y(), currentStand.z() + 0.5));
        if (distance + APPROACH_PROGRESS_STEP < closestApproach) {
            closestApproach = distance;
            watchdog.progress();
        }
    }

    private boolean acceptTarget(BlockCoord target) {
        BlockState targetState = level.getBlockState(toPos(target));
        buffer.remove(target);
        if (!breaker.dropsAllowedFor(targetState)) {
            if (breaker.toolBroke()) {
                fail("инструмент сломался: добыто " + mined + " из " + count);
                return false;
            }
            if (mined == 0 && !skipWithoutTool) {
                fail(tierReason(targetState));
                return false;
            }
            noTool++;
            return false;
        }
        currentTarget = target;
        return true;
    }

    private boolean exposed(BlockCoord cell) {
        BlockPos pos = toPos(cell);
        for (Direction side : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(side));
            if (neighbour.isAir() || neighbour.canBeReplaced()) {
                return true;
            }
        }
        return false;
    }

    private boolean approachReachStand(BlockCoord origin, List<BlockCoord> candidates) {
        ReachPlanner.World world = new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return MiningSafety.canStand(feet, safety);
            }
        };
        List<BlockCoord> reachable =
                vein ? candidates : candidates.stream().filter(this::exposed).toList();
        Optional<BlockCoord> stand = ReachPlanner.nextStand(
                SpiritReach.current(), origin, reachable, world, reachStandsTried, Set.copyOf(candidates));
        if (stand.isEmpty()) {
            return false;
        }
        reachStandsTried.add(stand.get());
        if (kind == WorkKind.GATHER && !standReachable(stand.get())) {
            unreachable++;
            lastUnreachable = candidates.isEmpty() ? null : candidates.get(0);
            return false;
        }
        currentTarget = null;
        currentStand = stand.get();
        beginApproach(new Vec3(currentStand.x() + 0.5, currentStand.y(), currentStand.z() + 0.5));
        state = State.APPROACH;
        return true;
    }

    private void approachTick() {
        noteApproachProgress();
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                if (currentTarget == null) {
                    state = State.PICK;
                } else {
                    prepareBreak();
                }
            }
            case STUCK, TIMEOUT -> {
                if (currentTarget == null) {
                    currentStand = null;
                    state = State.PICK;
                    return;
                }
                if (approachFailures.recordFailure(currentStand, currentTarget)) {
                    unreachable++;
                } else {
                    buffer.offer(List.of(currentTarget));
                }
                currentTarget = null;
                currentStand = null;
                state = State.PICK;
            }
        }
    }

    private void prepareBreak() {
        if (!(MiningSafety.canBreak(currentTarget, safety) instanceof BreakSafety.Safe)) {
            unsafe++;
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        BlockPos feet = spirit.blockPosition();
        boolean inReach = SpiritReach.reachable(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), currentTarget);
        BlockBreaker.Begin begin =
                inReach ? breaker.beginAnyDistance(toPos(currentTarget)) : breaker.begin(toPos(currentTarget));
        if (begin instanceof BlockBreaker.Protected) {
            protectedSkipped++;
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused && refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
            fail(refused.message());
            return;
        }
        if (!(begin instanceof BlockBreaker.Started)) {
            noStand++;
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        if (breaker.dropsAllowed()) {
            BlockPos pos = toPos(currentTarget);
            BlockState stateToBreak = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            ItemStack tool = breaker.tool();
            List<ItemStack> drops = Block.getDrops(stateToBreak, (ServerLevel) level, pos, blockEntity, spirit, tool);
            StackMapper mapper = new StackMapper();
            List<Stack> inventory = mapper.map(spirit.getInventory());
            List<Stack> incoming = new ArrayList<>();
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    incoming.add(mapper.of(drop));
                }
            }
            boolean fits = incoming.isEmpty() || InventorySpace.holds(inventory, incoming);
            boolean nearlyFull = occupiedSlots() >= CatalogLimits.UNLOAD_TRIGGER_SLOTS;
            if (!fits || (nearlyFull && !depositTried)) {
                if (!depositTried) {
                    startDeposit(incoming, !fits);
                    return;
                }
                fail(fullReason());
                return;
            }
        }
        WorkTarget target = new WorkTarget(kind, currentTarget);
        if (!WorkReserveHub.reserve(spirit, target)) {
            breaker.cancel();
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        reserved = target;
        state = State.BREAK;
    }

    private void releaseReserved() {
        if (reserved != null) {
            WorkReserveHub.release(spirit, reserved);
            reserved = null;
        }
    }

    private void startDeposit(List<Stack> incoming, boolean mandatory) {
        int toolSlot = breaker.toolSlot();
        breaker.cancel();
        approach.cancel();
        depositMandatory = mandatory;
        depositBlockedItem = incoming.isEmpty() ? null : incoming.get(0).item();
        depositTried = true;
        depositFreed = false;
        deposit = UnloadScript.relief(spirit, toolSlot);
        buffer.offer(List.of(currentTarget));
        currentTarget = null;
        currentStand = null;
        state = State.DEPOSIT;
    }

    private void depositTick() {
        ScriptStatus status = deposit.tick();
        if (status == ScriptStatus.RUNNING) {
            return;
        }
        boolean moved = deposit.movedTotal() > 0;
        deposit = null;
        if (status == ScriptStatus.SUCCEEDED && (moved || !depositMandatory)) {
            depositFreed = moved;
            state = State.PICK;
            return;
        }
        if (!depositMandatory) {
            state = State.PICK;
            return;
        }
        fail(fullReason());
    }

    private String fullReason() {
        return depositBlockedItem == null
                ? "инвентарь полон: не помещается добыча"
                : "инвентарь полон: не помещается " + depositBlockedItem;
    }

    private int occupiedSlots() {
        int occupied = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (!spirit.getInventory().getItem(slot).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {}
            case BROKEN -> {
                releaseReserved();
                mined++;
                watchdog.progress();
                collectRuns++;
                collector = newCollector(currentTarget);
                state = State.COLLECT;
            }
            case GONE -> {
                releaseReserved();
                currentTarget = null;
                currentStand = null;
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
            String reason = collector.failureReason();
            if (mined >= count) {
                fail("не удалось подобрать дроп у " + currentTarget + ": " + reason);
                return;
            }
            dropRetries.failed(currentTarget);
            collector = null;
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        collector = null;
        currentTarget = null;
        currentStand = null;
        if (depositFreed || occupiedSlots() < CatalogLimits.UNLOAD_TRIGGER_SLOTS) {
            depositTried = false;
            depositFreed = false;
        }
        state = State.PICK;
    }

    private void beginRetryCollect() {
        currentTarget = dropRetries.poll();
        currentStand = null;
        collector = newCollector(currentTarget);
        state = State.COLLECT;
    }

    private DropCollector newCollector(BlockCoord center) {
        return new DropCollector(
                spirit,
                IdFilter.matchAll(),
                center,
                COLLECT_RADIUS,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }

    private String stopReason() {
        String base = summary() + "; подходящих блоков в радиусе " + radius + " не осталось";
        if (vein && noStand > 0) {
            return base + "; руда закрыта, нужен проход: блоки руды есть, но нет места для духа — прокопайте "
                    + "проход к руде (dig_descent или dig_area) и повторите mine_vein";
        }
        if (kind == WorkKind.GATHER && mined == 0 && lastUnreachable != null) {
            return "нет пути к блоку " + coordText(lastUnreachable) + ": " + base + "; " + pathCause();
        }
        return base;
    }

    private String stallReason() {
        String timing = "нет прогресса " + watchdog.limitTicks() + " тиков (" + (watchdog.limitTicks() / 20) + " с): "
                + summary();
        if (mined == 0 && (state == State.FIND || state == State.PICK || state == State.APPROACH)) {
            return "нет пути к блоку " + coordText(targetForReason()) + " — " + timing + "; " + pathCause();
        }
        return timing + "; текущий шаг " + state + " — проверьте доступность блоков и подходящий инструмент";
    }

    private BlockCoord targetForReason() {
        if (currentTarget != null) {
            return currentTarget;
        }
        return lastUnreachable;
    }

    private static String coordText(BlockCoord target) {
        return target == null
                ? "(координата неизвестна)"
                : "(" + target.x() + ", " + target.y() + ", " + target.z() + ")";
    }

    private String pathCause() {
        if (noStand > 0) {
            return "блок есть, но к нему нет места для стоянки духа — преграда вплотную к цели";
        }
        if (unreachable > 0) {
            return "блок есть, но все стоянки у него недостижимы — путь перекрыт";
        }
        return "блок есть, но дух не смог к нему подойти: путь перекрыт или цель недостижима";
    }

    private String summary() {
        String counts = "добыто " + mined + " из " + count
                + "; пропущено небезопасных: " + unsafe
                + ", без места для духа: " + noStand
                + ", без подходящего инструмента: " + noTool
                + ", пропущено защищённых: " + protectedSkipped;
        if (unreachable > 0) {
            counts += "; недостижимо: " + unreachable;
        }
        return counts;
    }

    private String tierReason(BlockState state) {
        String kind = RequiredTier.kindOf(state);
        String tier = RequiredTier.of(state);
        String need = kind.isEmpty() ? "нужен инструмент" : "нужен инструмент (" + kind + ")";
        if (tier.isEmpty()) {
            return need + " для " + label + ": подходящего в инвентаре нет или он изношен";
        }
        return need + " для " + label + ": " + tier + " или лучше в инвентаре нет или он изношен";
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private record Spec(
            HarvestTargets targets,
            WorkKind kind,
            String label,
            int count,
            int radius,
            boolean skipWithoutTool,
            boolean vein) {}
}
