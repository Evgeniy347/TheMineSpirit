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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class TreeChop {
    private static final Logger LOGGER = LogUtils.getLogger();

    private enum Phase {
        APPROACH,
        CLEAR,
        CHOP,
        COLLECT,
        REPLANT
    }

    private final SpiritEntity spirit;
    private final IdFilter logFilter;
    private final boolean replant;
    private final boolean orchard;
    private final TreeView view;
    private final Approach approach;
    private final LeafClearing clearing;
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
    private boolean guarded;
    private boolean secondStand;
    private @Nullable BlockPos stand;
    private @Nullable ChopOutcome refusal;

    TreeChop(SpiritEntity spirit, IdFilter logFilter, boolean replant, boolean orchard, TreeView view) {
        this.spirit = spirit;
        this.logFilter = logFilter;
        this.replant = replant;
        this.orchard = orchard;
        this.view = view;
        this.approach = new Approach(spirit);
        this.clearing = new LeafClearing(spirit, view);
        this.breaker = new BlockBreaker(spirit);
    }

    void begin(Tree target) {
        tree = target;
        species = ChopSpecies.of(spirit.level(), target);
        planIndex = 0;
        standingLogs = 0;
        plantedSaplings = 0;
        skippedCells = 0;
        lackedSaplings = false;
        breaking = false;
        guarded = false;
        secondStand = false;
        clearing.begin(target, new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()));
        refusal = startApproach();
        if (stand != null) {
            BlockCoord base = tree.base();
            double trunk = Math.sqrt(spirit.position().distanceToSqr(Vec3.atBottomCenterOf(toPos(base))));
            LOGGER.info(
                    "[chop] {} идёт к стволу {}: стоянка {}, до ствола {}",
                    spirit.getName().getString(),
                    base,
                    stand.toShortString(),
                    String.format(Locale.ROOT, "%.1f", trunk));
        }
    }

    private @Nullable ChopOutcome startApproach() {
        stand = ChopStand.first(spirit, tree);
        if (stand == null && clearing.active()) {
            phase = Phase.CLEAR;
            return null;
        }
        if (stand == null) {
            LOGGER.warn(
                    "[chop] {}: у дерева {} нет достижимой стоянки на земле — рубка брошена",
                    spirit.getName().getString(),
                    tree.base());
            return ChopOutcome.simple(ChopOutcome.State.UNREACHABLE);
        }
        BlockCoord from = new BlockCoord(stand.getX(), stand.getY(), stand.getZ());
        plan = ChopPlan.of(tree, from);
        planIndex = 0;
        approach.begin(Vec3.atBottomCenterOf(stand), ChopTuning.CHOP_ARRIVAL);
        phase = Phase.APPROACH;
        return null;
    }

    ChopOutcome tick() {
        if (refusal != null) {
            ChopOutcome outcome = refusal;
            refusal = null;
            return outcome;
        }
        return switch (phase) {
            case APPROACH -> approach();
            case CLEAR -> clear();
            case CHOP -> chop();
            case COLLECT -> collect();
            case REPLANT -> replant();
        };
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
        clearing.cancel();
        if (collector != null) {
            collector.cancel();
        }
        if (replanter != null) {
            replanter.cancel();
        }
    }

    void suspend() {
        approach.suspend();
        clearing.suspend();
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

    private ChopOutcome approach() {
        return switch (approach.tick()) {
            case MOVING -> ChopOutcome.running();
            case ARRIVED -> resumeChop();
            case STUCK, TIMEOUT -> secondStand ? leftStanding() : ChopOutcome.simple(ChopOutcome.State.UNREACHABLE);
        };
    }

    private ChopOutcome clear() {
        if (clearing.tick()) {
            return ChopOutcome.running();
        }
        ChopOutcome outcome = startApproach();
        return outcome == null ? ChopOutcome.running() : outcome;
    }

    private ChopOutcome resumeChop() {
        phase = Phase.CHOP;
        return ChopOutcome.running();
    }

    private ChopOutcome chop() {
        if (breaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return ChopOutcome.running();
            }
            breaking = false;
            planIndex++;
        }
        if (!guarded) {
            guarded = true;
            if (ChopGuard.protectedLog(spirit, tree, plan.order(), orchard)) {
                return ChopOutcome.simple(ChopOutcome.State.PROTECTED);
            }
        }
        while (planIndex < plan.order().size()) {
            BlockCoord log = plan.order().get(planIndex);
            BlockBreaker.Begin begin = breaker.beginChop(toPos(log), orchard);
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return ChopOutcome.running();
            }
            if (begin instanceof BlockBreaker.Protected) {
                return ChopOutcome.simple(ChopOutcome.State.PROTECTED);
            }
            planIndex++;
        }
        return afterPass();
    }

    private ChopOutcome afterPass() {
        BlockCoord from = stand == null ? tree.base() : new BlockCoord(stand.getX(), stand.getY(), stand.getZ());
        ChopPlan rest = plan.rest(view, from);
        if (rest.order().isEmpty()) {
            return startCollect();
        }
        plan = rest;
        if (secondStand) {
            return leftStanding();
        }
        secondStand = true;
        planIndex = 0;
        stand = ChopStand.second(spirit, tree, rest.order(), stand);
        if (stand == null) {
            return leftStanding();
        }
        LOGGER.info(
                "[chop] {} у дерева {}: после прохода стоят {} брёвен — добивка со второй стоянки {}",
                spirit.getName().getString(),
                tree.base(),
                rest.order().size(),
                stand.toShortString());
        approach.begin(Vec3.atBottomCenterOf(stand), ChopTuning.CHOP_ARRIVAL);
        phase = Phase.APPROACH;
        return ChopOutcome.running();
    }

    private ChopOutcome leftStanding() {
        standingLogs = plan.rest(view, tree.base()).order().size();
        LOGGER.warn(
                "[chop] {} у дерева {}: и со второй стоянки не срублено {} брёвен: {}",
                spirit.getName().getString(),
                tree.base(),
                standingLogs,
                plan.order());
        return startCollect();
    }

    private ChopOutcome startCollect() {
        collector = new DropCollector(
                spirit,
                logFilter,
                tree.base(),
                ChopTuning.COLLECT_RADIUS,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
        phase = Phase.COLLECT;
        return ChopOutcome.running();
    }

    private ChopOutcome collect() {
        return switch (collector.tick()) {
            case RUNNING -> ChopOutcome.running();
            case DONE -> {
                collector = null;
                yield replant ? startReplant() : finish();
            }
            case FAILED -> {
                cancel();
                yield ChopOutcome.failed("подбор дропа после рубки: " + collector.failureReason());
            }
        };
    }

    private ChopOutcome startReplant() {
        int baseY = tree.base().y();
        List<BlockPos> cells = tree.logs().stream()
                .filter(log -> log.y() == baseY)
                .map(TreeChop::toPos)
                .toList();
        replanter = new Replanter(spirit, species, cells, view);
        phase = Phase.REPLANT;
        return ChopOutcome.running();
    }

    private ChopOutcome replant() {
        if (replanter.tick() == Replanter.State.RUNNING) {
            return ChopOutcome.running();
        }
        plantedSaplings += replanter.planted();
        skippedCells += replanter.skipped();
        lackedSaplings |= replanter.lackedSaplings();
        replanter = null;
        return finish();
    }

    private ChopOutcome finish() {
        return ChopOutcome.done(standingLogs, plantedSaplings, skippedCells, lackedSaplings);
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
