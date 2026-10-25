package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class TreeChop {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DIAGONAL_PENALTY = 10_000;

    enum State {
        RUNNING,
        DONE,
        PROTECTED,
        UNREACHABLE,
        FAILED
    }

    record Outcome(
            State state,
            int standingLogs,
            int plantedSaplings,
            int skippedCells,
            boolean lackedSaplings,
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
        COLLECT,
        REPLANT
    }

    private final SpiritEntity spirit;
    private final IdFilter logFilter;
    private final boolean replant;
    private final TreeView view;
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
    private @Nullable BlockPos stand;

    TreeChop(SpiritEntity spirit, IdFilter logFilter, boolean replant, TreeView view) {
        this.spirit = spirit;
        this.logFilter = logFilter;
        this.replant = replant;
        this.view = view;
        this.approach = new Approach(spirit);
        this.breaker = new BlockBreaker(spirit);
    }

    Tree tree() {
        return tree;
    }

    void begin(Tree target) {
        tree = target;
        species = speciesOf(target);
        BlockPos stand = standingCellBesideTrunk(target);
        BlockCoord from = stand == null ? target.base() : new BlockCoord(stand.getX(), stand.getY(), stand.getZ());
        plan = ChopPlan.of(target, from);
        planIndex = 0;
        standingLogs = 0;
        plantedSaplings = 0;
        skippedCells = 0;
        lackedSaplings = false;
        breaking = false;
        Vec3 approachPoint = stand == null ? Vec3.atCenterOf(toPos(target.base())) : Vec3.atBottomCenterOf(stand);
        this.stand = stand;
        approach.begin(approachPoint, stand == null ? ChopTuning.STAND_DISTANCE : ChopTuning.CHOP_ARRIVAL);
        phase = Phase.APPROACH;
    }

    Outcome tick() {
        return switch (phase) {
            case APPROACH -> approach();
            case CHOP -> chop();
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

    private Outcome resumeChop() {
        if (phase == Phase.APPROACH) {
            BlockCoord base = tree.base();
            double trunk = Math.sqrt(spirit.position().distanceToSqr(Vec3.atBottomCenterOf(toPos(base))));
            LOGGER.info(
                    "[chop] {} у дерева {}: стоянка {}, до ствола {}",
                    spirit.getName().getString(),
                    base,
                    stand == null ? "нет (подход к стволу)" : stand.toShortString(),
                    String.format(Locale.ROOT, "%.1f", trunk));
        }
        phase = Phase.CHOP;
        return Outcome.running();
    }

    private Outcome chop() {
        if (breaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return Outcome.running();
            }
            breaking = false;
            planIndex++;
        }
        while (planIndex < plan.order().size()) {
            BlockCoord log = plan.order().get(planIndex);
            BlockBreaker.Begin begin = breaker.beginAnyDistance(toPos(log));
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return Outcome.running();
            }
            if (begin instanceof BlockBreaker.Protected) {
                return Outcome.simple(State.PROTECTED);
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            if (refused.reason() != BlockBreaker.RefuseReason.AIR) {
                standingLogs++;
            }
            planIndex++;
        }
        return startCollect();
    }

    private Outcome startCollect() {
        collector = new DropCollector(
                spirit,
                logFilter,
                tree.base(),
                ChopTuning.COLLECT_RADIUS,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
        phase = Phase.COLLECT;
        return Outcome.running();
    }

    private Outcome collect() {
        return switch (collector.tick()) {
            case RUNNING -> Outcome.running();
            case DONE -> {
                collector = null;
                yield replant ? startReplant() : finish();
            }
            case FAILED -> {
                cancel();
                yield Outcome.failed("подбор дропа после рубки: " + collector.failureReason());
            }
        };
    }

    private Outcome startReplant() {
        int baseY = tree.base().y();
        List<BlockPos> cells = tree.logs().stream()
                .filter(log -> log.y() == baseY)
                .map(TreeChop::toPos)
                .toList();
        replanter = new Replanter(spirit, species, cells, view);
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
        return Outcome.done(standingLogs, plantedSaplings, skippedCells, lackedSaplings);
    }

    private BlockPos standingCellBesideTrunk(Tree target) {
        int baseY = target.base().y();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockCoord log : target.logs()) {
            if (log.y() != baseY) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos pos = new BlockPos(log.x() + dx, baseY + dy, log.z() + dz);
                        if (!isStandable(pos)) {
                            continue;
                        }
                        double diagonal = dx != 0 && dz != 0 ? DIAGONAL_PENALTY : 0;
                        double distance = spirit.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) + diagonal;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isStandable(BlockPos pos) {
        Level level = spirit.level();
        BlockPos below = pos.below();
        return (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())
                && (level.getBlockState(pos.above()).isAir()
                        || level.getBlockState(pos.above()).canBeReplaced())
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private TreeSpecies speciesOf(Tree found) {
        BlockCoord base = found.base();
        String blockId = BuiltInRegistries.BLOCK
                .getKey(spirit.level().getBlockState(toPos(base)).getBlock())
                .toString();
        return TreeSpecies.ofLog(blockId)
                .orElseThrow(() -> new IllegalStateException("Основание дерева " + base + " — блок '" + blockId
                        + "', а не бревно известной породы: поиск брал только брёвна из TreeSpecies"));
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
