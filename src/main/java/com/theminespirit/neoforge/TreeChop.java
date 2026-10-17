package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
        REPLANT
    }

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
            case REPLANT -> replant();
        };
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
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
            case DONE -> replant ? startReplant() : finish();
            case FAILED -> {
                cancel();
                yield Outcome.failed("подбор дропа после рубки: " + collector.failureReason());
            }
        };
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
        standingLogs += plan.leftStanding();
        return Outcome.done(standingLogs, plantedSaplings, skippedCells, lackedSaplings);
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
