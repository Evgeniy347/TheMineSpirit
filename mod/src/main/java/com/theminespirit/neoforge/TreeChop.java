package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
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
        CLIMB,
        CLEAR,
        CHOP,
        DESCEND,
        COLLECT
    }

    private final SpiritEntity spirit;
    private final Approach approach;
    private final LeafClearing clearing;
    private final BlockBreaker breaker;
    private Tree tree;
    private TreeSpecies species;
    private Phase phase;
    private int standingLogs;
    private final ChopScaffold scaffold;
    private final ChopPass pass;
    private final ChopHarvest harvest;
    private final ChopPursuit pursuit;
    private @Nullable ChopOutcome refusal;

    TreeChop(SpiritEntity spirit, IdFilter logFilter, boolean replant, boolean orchard, TreeView view) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
        this.clearing = new LeafClearing(spirit, view);
        this.breaker = new BlockBreaker(spirit);
        this.scaffold = new ChopScaffold(spirit);
        this.pass = new ChopPass(spirit, view, orchard, breaker);
        this.harvest = new ChopHarvest(spirit, logFilter, replant, orchard, view);
        this.pursuit = new ChopPursuit(spirit);
    }

    void begin(Tree target) {
        tree = target;
        species = ChopSpecies.of(spirit.level(), target);
        standingLogs = 0;
        pursuit.begin(target);
        scaffold.reset();
        clearing.begin(target, new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()));
        refusal = startApproach();
        BlockPos stand = pursuit.stand();
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
        ChopStand.Placement picked = pursuit.first();
        if (picked == null && clearing.active()) {
            phase = Phase.CLEAR;
            return null;
        }
        if (picked == null) {
            LOGGER.warn(
                    "[chop] {}: у дерева {} нет стоянки вплотную к стволу на достижимой высоте — рубка брошена",
                    spirit.getName().getString(),
                    tree.base());
            return ChopOutcome.simple(ChopOutcome.State.UNREACHABLE);
        }
        pass.begin(tree, toCoord(picked.feet()));
        startApproachTo(picked);
        return null;
    }

    private void startApproachTo(ChopStand.Placement picked) {
        BlockCoord base = picked.climbFrom();
        if (base == null) {
            phase = Phase.APPROACH;
            approach.begin(Vec3.atBottomCenterOf(picked.feet()), ChopTuning.CHOP_ARRIVAL);
            return;
        }
        phase = Phase.CLIMB;
        approach.begin(Vec3.atBottomCenterOf(toPos(base)), ChopTuning.CHOP_ARRIVAL);
    }

    ChopOutcome tick() {
        if (refusal != null) {
            ChopOutcome outcome = refusal;
            refusal = null;
            return outcome;
        }
        return switch (phase) {
            case APPROACH -> approach();
            case CLIMB -> climb();
            case CLEAR -> clear();
            case CHOP -> chop();
            case DESCEND -> descend();
            case COLLECT -> collect();
        };
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
        clearing.cancel();
        scaffold.cancel();
        harvest.cancel();
    }

    void suspend() {
        approach.suspend();
        clearing.suspend();
        pass.suspend();
        harvest.suspend();
    }

    private ChopOutcome approach() {
        return switch (approach.tick()) {
            case MOVING -> ChopOutcome.running();
            case ARRIVED -> resumeChop();
            case STUCK, TIMEOUT -> retryStand();
        };
    }

    private ChopOutcome climb() {
        if (!scaffold.climbing()) {
            return switch (approach.tick()) {
                case MOVING -> ChopOutcome.running();
                case ARRIVED -> startScaffold();
                case STUCK, TIMEOUT -> retryStand();
            };
        }
        return switch (scaffold.tickClimb()) {
            case RUNNING -> ChopOutcome.running();
            case BUILT -> resumeChop();
            case FAILED -> {
                LOGGER.warn(
                        "[chop] {} у дерева {}: подмости к стоянке {} не построены — {}",
                        spirit.getName().getString(),
                        tree.base(),
                        pursuit.stand(),
                        scaffold.failureReason());
                yield retryStand();
            }
        };
    }

    private ChopOutcome startScaffold() {
        BlockCoord base = pursuit.climbFrom();
        BlockPos feet = pursuit.stand();
        if (base == null || feet == null) {
            return retryStand();
        }
        if (!scaffold.begin(base, feet.getY())) {
            LOGGER.warn(
                    "[chop] {} у дерева {}: до стоянки {} не подняться — {}",
                    spirit.getName().getString(),
                    tree.base(),
                    feet.toShortString(),
                    scaffold.failureReason());
            return giveUp();
        }
        return scaffold.climbing() ? ChopOutcome.running() : resumeChop();
    }

    private ChopOutcome retryStand() {
        BlockPos feet = pursuit.stand();
        List<BlockCoord> rest =
                pass.rest(feet == null ? tree.base() : toCoord(feet)).order();
        if (rest.isEmpty()) {
            return startDescent();
        }
        ChopStand.Placement picked = pursuit.next(rest);
        if (picked == null) {
            return giveUp();
        }
        startApproachTo(picked);
        return ChopOutcome.running();
    }

    private ChopOutcome giveUp() {
        if (pass.remaining() == 0) {
            return startDescent();
        }
        return pursuit.tried() > 1 ? leftStanding() : ChopOutcome.simple(ChopOutcome.State.UNREACHABLE);
    }

    private ChopOutcome descend() {
        return switch (scaffold.tickDescent()) {
            case RUNNING -> ChopOutcome.running();
            case DONE -> startCollect();
            case FAILED -> {
                LOGGER.warn(
                        "[chop] {} у дерева {}: подмости не разобраны — {}",
                        spirit.getName().getString(),
                        tree.base(),
                        scaffold.failureReason());
                yield startCollect();
            }
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
        return switch (pass.tick()) {
            case RUNNING -> ChopOutcome.running();
            case PROTECTED -> ChopOutcome.simple(ChopOutcome.State.PROTECTED);
            case PASS_DONE -> afterPass();
        };
    }

    private ChopOutcome afterPass() {
        BlockPos stand = pursuit.stand();
        ChopPlan rest = pass.rest(stand == null ? tree.base() : toCoord(stand));
        pass.continueWith(rest);
        if (rest.order().isEmpty()) {
            return startDescent();
        }
        ChopStand.Placement picked = pursuit.next(rest.order());
        if (picked == null) {
            return leftStanding();
        }
        LOGGER.info(
                "[chop] {} у дерева {}: после прохода стоят {} брёвен — добивка со стоянки {} (попытка {})",
                spirit.getName().getString(),
                tree.base(),
                rest.order().size(),
                picked.feet().toShortString(),
                pursuit.tried());
        startApproachTo(picked);
        return ChopOutcome.running();
    }

    private ChopOutcome startDescent() {
        if (!scaffold.hasScaffold()) {
            return startCollect();
        }
        scaffold.startDescent();
        phase = Phase.DESCEND;
        return ChopOutcome.running();
    }

    private ChopOutcome leftStanding() {
        standingLogs = pass.remaining();
        LOGGER.warn(
                "[chop] {} у дерева {}: и с последней стоянки не срублено {} брёвен: {}",
                spirit.getName().getString(),
                tree.base(),
                standingLogs,
                pass.order());
        return startDescent();
    }

    private ChopOutcome startCollect() {
        harvest.begin(tree, species);
        phase = Phase.COLLECT;
        return ChopOutcome.running();
    }

    private ChopOutcome collect() {
        return switch (harvest.tick()) {
            case RUNNING -> ChopOutcome.running();
            case DONE -> finish();
            case FAILED -> {
                cancel();
                yield ChopOutcome.failed("подбор дропа после рубки: " + harvest.failureReason());
            }
        };
    }

    private ChopOutcome finish() {
        return ChopOutcome.done(standingLogs, harvest.planted(), harvest.skipped(), harvest.lacked());
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
