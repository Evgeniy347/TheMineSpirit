package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class TreeChop {
    enum State {
        RUNNING,
        DONE,
        PROTECTED,
        UNREACHABLE,
        FAILED
    }

    record Outcome(State state, int standingLogs, int plantedSaplings, int skippedCells, boolean lackedSaplings,
            String reason) {
        static Outcome running() {
            return new Outcome(State.RUNNING, 0, 0, 0, false, null);
        }

        static Outcome done(int standingLogs, int plantedSaplings, int skippedCells, boolean lackedSaplings) {
            return new Outcome(State.DONE, standingLogs, plantedSaplings, skippedCells, lackedSaplings, null);
        }

        static Outcome simple(State state) {
            return new Outcome(state, 0, 0, 0, false, null);
        }

        static Outcome failed(String reason) {
            return new Outcome(State.FAILED, 0, 0, 0, false, reason);
        }
    }

    private enum Phase {
        APPROACH,
        CHOP,
        APPROACH_LOG,
        COLLECT,
        PILLAR_APPROACH,
        PILLAR_BUILD,
        CHOP_HIGH,
        DESCEND_PILLAR,
        DISMANTLE_PILLAR,
        COLLECT_HIGH,
        REPLANT
    }

    private static final PickupTuning HIGH_PICKUP = new PickupTuning(PickupTuning.REFRESH_TICKS,
            PickupTuning.PICKUP_REACH, 200, PickupTuning.MAX_DELAY_WAIT_TICKS);

    private final SpiritEntity spirit;
    private final IdFilter logFilter;
    private final boolean replant;
    private final Approach approach;
    private final BlockBreaker breaker;
    private Tree tree;
    private TreeSpecies species;
    private ChopPlan plan;
    private DropCollector collector;
    private Replanter replanter;
    private Phase phase;
    private List<BlockCoord> highLogs = List.of();
    private PillarColumn pillar;
    private final List<BlockCoord> pillarRemaining = new ArrayList<>();
    private PillarDismantler dismantler;
    private DropCollector highCollector;
    private boolean highBreaking;
    private int highLogIndex;
    private int descendStall;
    private String pillarFailure;
    private int planIndex;
    private int standingLogs;
    private int plantedSaplings;
    private int skippedCells;
    private boolean lackedSaplings;
    private boolean breaking;
    private boolean movedCloser;

    TreeChop(SpiritEntity spirit, IdFilter logFilter, boolean replant) {
        this.spirit = spirit;
        this.logFilter = logFilter;
        this.replant = replant;
        this.approach = new Approach(spirit);
        this.breaker = new BlockBreaker(spirit);
    }

    Tree tree() {
        return tree;
    }

    void begin(Tree target) {
        tree = target;
        species = speciesOf(target);
        plan = ChopPlan.of(target, ChopTuning.REACH_ABOVE_BASE);
        highLogs = target.logs().stream()
                .filter(coord -> coord.y() > target.base().y() + ChopTuning.REACH_ABOVE_BASE)
                .sorted(Comparator.comparingInt(BlockCoord::y).reversed())
                .toList();
        pillar = null;
        pillarRemaining.clear();
        pillarFailure = null;
        planIndex = 0;
        standingLogs = 0;
        plantedSaplings = 0;
        skippedCells = 0;
        lackedSaplings = false;
        breaking = false;
        movedCloser = false;
        approach.begin(Vec3.atCenterOf(toPos(target.base())), ChopTuning.STAND_DISTANCE);
        phase = Phase.APPROACH;
    }

    Outcome tick() {
        return switch (phase) {
            case APPROACH -> approach();
            case CHOP -> chop();
            case APPROACH_LOG -> approachLog();
            case COLLECT -> collect();
            case PILLAR_APPROACH -> pillarApproach();
            case PILLAR_BUILD -> pillarBuild();
            case CHOP_HIGH -> chopHigh();
            case DESCEND_PILLAR -> descendPillar();
            case DISMANTLE_PILLAR -> dismantlePillar();
            case COLLECT_HIGH -> collectHigh();
            case REPLANT -> replant();
        };
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        if (highCollector != null) {
            highCollector.cancel();
        }
        if (replanter != null) {
            replanter.cancel();
        }
    }

    void suspend() {
        approach.suspend();
        if (breaking) {
            breaker.cancel();
            breaking = false;
        }
        if (collector != null) {
            collector.suspend();
        }
        if (highCollector != null) {
            highCollector.suspend();
        }
        if (replanter != null) {
            replanter.suspend();
        }
    }

    private Outcome approach() {
        return switch (approach.tick()) {
            case MOVING -> Outcome.running();
            case ARRIVED -> resumeChop();
            case STUCK, TIMEOUT -> Outcome.simple(State.UNREACHABLE);
        };
    }

    private Outcome approachLog() {
        return switch (approach.tick()) {
            case MOVING -> Outcome.running();
            case ARRIVED -> resumeChop();
            case STUCK, TIMEOUT -> {
                leaveStanding();
                yield resumeChop();
            }
        };
    }

    private Outcome resumeChop() {
        phase = Phase.CHOP;
        return Outcome.running();
    }

    private Outcome chop() {
        if (breaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return Outcome.running();
            }
            breaking = false;
            skipLog();
        }
        while (planIndex < plan.reachable().size()) {
            BlockCoord log = plan.reachable().get(planIndex);
            BlockBreaker.Begin begin = breaker.begin(toPos(log));
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return Outcome.running();
            }
            if (begin instanceof BlockBreaker.Protected) {
                return Outcome.simple(State.PROTECTED);
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH && !movedCloser) {
                movedCloser = true;
                approach.begin(standingPointUnder(log), ChopTuning.CLOSE_DISTANCE);
                phase = Phase.APPROACH_LOG;
                return Outcome.running();
            }
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                skipLog();
            } else {
                leaveStanding();
            }
        }
        return startCollect();
    }

    private void leaveStanding() {
        standingLogs++;
        skipLog();
    }

    private void skipLog() {
        planIndex++;
        movedCloser = false;
    }

    private Vec3 standingPointUnder(BlockCoord log) {
        return Vec3.atBottomCenterOf(new BlockPos(log.x(), tree.base().y(), log.z()));
    }

    private Outcome startCollect() {
        collector = new DropCollector(spirit, logFilter, tree.base(), ChopTuning.COLLECT_RADIUS,
                PickupTuning.DEFAULT, OverflowMode.FAIL);
        phase = Phase.COLLECT;
        return Outcome.running();
    }

    private Outcome collect() {
        return switch (collector.tick()) {
            case RUNNING -> Outcome.running();
            case DONE -> afterLowCollect();
            case FAILED -> {
                cancel();
                yield Outcome.failed("подбор дропа после рубки: " + collector.failureReason());
            }
        };
    }

    private Outcome afterLowCollect() {
        collector = null;
        if (highLogs.isEmpty()) {
            return replant ? startReplant() : finish();
        }
        if (cheapBlock() == null) {
            return leaveHigh("нет материала для столба: нужны земля или булыжник");
        }
        return startPillarBuild();
    }

    private Outcome startReplant() {
        collector = null;
        int baseY = tree.base().y();
        List<BlockPos> cells = tree.logs().stream().filter(log -> log.y() == baseY).map(TreeChop::toPos).toList();
        replanter = new Replanter(spirit, species, cells);
        phase = Phase.REPLANT;
        return Outcome.running();
    }

    private Outcome replant() {
        if (replanter.tick() == Replanter.State.RUNNING) {
            return Outcome.running();
        }
        plantedSaplings += replanter.planted();
        skippedCells += replanter.skipped();
        lackedSaplings |= replanter.lackedSaplings();
        replanter = null;
        return finish();
    }

    private Outcome finish() {
        collector = null;
        highCollector = null;
        return Outcome.done(standingLogs, plantedSaplings, skippedCells, lackedSaplings);
    }

    String pillarFailure() {
        return pillarFailure;
    }

    private Outcome startPillarBuild() {
        BlockPos stand = standingCellNearTrunk();
        if (stand == null) {
            return leaveHigh("рядом со стволом нет места для столба");
        }
        if (!stand.equals(spirit.blockPosition())) {
            approach.begin(Vec3.atBottomCenterOf(stand), ChopTuning.CLOSE_DISTANCE);
            phase = Phase.PILLAR_APPROACH;
            return Outcome.running();
        }
        return beginPillar();
    }

    private Outcome pillarApproach() {
        return switch (approach.tick()) {
            case MOVING -> Outcome.running();
            case ARRIVED -> beginPillar();
            case STUCK, TIMEOUT -> leaveHigh("не подошёл к месту столба рядом со стволом");
        };
    }

    private Outcome beginPillar() {
        int topY = highLogs.stream().mapToInt(BlockCoord::y).max().orElse(spirit.blockPosition().getY());
        int height = topY - 2 - spirit.blockPosition().getY();
        if (height < 1) {
            highLogIndex = 0;
            highBreaking = false;
            phase = Phase.CHOP_HIGH;
            return Outcome.running();
        }
        String blockId = cheapBlock();
        if (blockId == null) {
            return leaveHigh("нет материала для столба: нужны земля или булыжник");
        }
        int have = PillarColumn.count(spirit, blockId);
        if (have < height) {
            return leaveHigh("нет материала '" + blockId + "': нужно " + height + ", есть " + have);
        }
        pillar = new PillarColumn(spirit, blockId, height);
        phase = Phase.PILLAR_BUILD;
        return Outcome.running();
    }

    private Outcome pillarBuild() {
        return switch (pillar.tick()) {
            case RUNNING -> Outcome.running();
            case BUILT -> {
                pillarRemaining.clear();
                pillarRemaining.addAll(topDown(pillar.cells()));
                highLogIndex = 0;
                highBreaking = false;
                phase = Phase.CHOP_HIGH;
                yield Outcome.running();
            }
            case FAILED -> leaveHigh("столб не построен: " + pillar.failureReason());
        };
    }

    private Outcome chopHigh() {
        if (highBreaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return Outcome.running();
            }
            highBreaking = false;
            highLogIndex++;
        }
        while (highLogIndex < highLogs.size()) {
            BlockCoord log = highLogs.get(highLogIndex);
            BlockBreaker.Begin begin = breaker.begin(toPos(log));
            if (begin instanceof BlockBreaker.Started) {
                highBreaking = true;
                return Outcome.running();
            }
            if (begin instanceof BlockBreaker.Protected) {
                pillar = null;
                pillarRemaining.clear();
                return Outcome.simple(State.PROTECTED);
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                highLogIndex++;
                continue;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH && !pillarRemaining.isEmpty()) {
                BlockCoord cell = pillarRemaining.remove(0);
                dismantler = new PillarDismantler(spirit, List.of(cell));
                descendStall = 0;
                phase = Phase.DESCEND_PILLAR;
                return Outcome.running();
            }
            standingLogs++;
            highLogIndex++;
        }
        return startDismantle();
    }

    private Outcome descendPillar() {
        if (dismantler == null) {
            phase = Phase.CHOP_HIGH;
            return Outcome.running();
        }
        PillarDismantler.Outcome result = dismantler.tick();
        if (result == PillarDismantler.Outcome.RUNNING) {
            return Outcome.running();
        }
        if (result == PillarDismantler.Outcome.FAILED) {
            dismantler = null;
            standingLogs++;
            highLogIndex++;
            phase = Phase.CHOP_HIGH;
            return Outcome.running();
        }
        if (!spirit.onGround()) {
            if (++descendStall > 40) {
                descendStall = 0;
                dismantler = null;
                phase = Phase.CHOP_HIGH;
            }
            return Outcome.running();
        }
        descendStall = 0;
        dismantler = null;
        phase = Phase.CHOP_HIGH;
        return Outcome.running();
    }

    private Outcome startDismantle() {
        if (pillarRemaining.isEmpty()) {
            pillar = null;
            return startHighCollect();
        }
        dismantler = new PillarDismantler(spirit, new ArrayList<>(pillarRemaining));
        phase = Phase.DISMANTLE_PILLAR;
        return Outcome.running();
    }

    private Outcome dismantlePillar() {
        if (dismantler == null) {
            return startHighCollect();
        }
        PillarDismantler.Outcome result = dismantler.tick();
        if (result == PillarDismantler.Outcome.RUNNING) {
            return Outcome.running();
        }
        dismantler = null;
        pillar = null;
        pillarRemaining.clear();
        return startHighCollect();
    }

    private Outcome startHighCollect() {
        if (highLogs.isEmpty()) {
            return replant ? startReplant() : finish();
        }
        highCollector = new DropCollector(spirit, logFilter, tree.base(), ChopTuning.COLLECT_RADIUS, HIGH_PICKUP,
                OverflowMode.FAIL);
        phase = Phase.COLLECT_HIGH;
        return Outcome.running();
    }

    private Outcome collectHigh() {
        return switch (highCollector.tick()) {
            case RUNNING -> Outcome.running();
            case DONE -> replant ? startReplant() : finish();
            case FAILED -> {
                pillarFailure = "часть дропа после рубки верха не подобрана: " + highCollector.failureReason();
                yield replant ? startReplant() : finish();
            }
        };
    }

    private Outcome leaveHigh(String reason) {
        standingLogs += highLogs.size();
        pillarFailure = reason;
        pillar = null;
        pillarRemaining.clear();
        return replant ? startReplant() : finish();
    }

    private BlockPos standingCellNearTrunk() {
        BlockPos base = toPos(tree.base());
        int feetY = spirit.blockPosition().getY();
        for (int radius = 1; radius <= 2; radius++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(base.getX() + dx, feetY, base.getZ() + dz);
                    if (!isStandable(pos)) {
                        continue;
                    }
                    double distance = spirit.position().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private boolean isStandable(BlockPos pos) {
        Level level = spirit.level();
        BlockPos below = pos.below();
        return (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())
                && (level.getBlockState(pos.above()).isAir() || level.getBlockState(pos.above()).canBeReplaced())
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private static List<BlockCoord> topDown(List<BlockCoord> cells) {
        List<BlockCoord> order = new ArrayList<>(cells);
        Collections.reverse(order);
        return order;
    }

    private String cheapBlock() {
        return PillarColumn.cheapBlock(spirit);
    }

    private TreeSpecies speciesOf(Tree found) {
        BlockCoord base = found.base();
        String blockId = BuiltInRegistries.BLOCK.getKey(spirit.level().getBlockState(toPos(base)).getBlock()).toString();
        return TreeSpecies.ofLog(blockId).orElseThrow(() -> new IllegalStateException("Основание дерева " + base
                + " — блок '" + blockId + "', а не бревно известной породы: поиск брал только брёвна из TreeSpecies"));
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
