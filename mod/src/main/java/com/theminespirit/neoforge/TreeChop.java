package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopPlan;
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
        ASCENT,
        CHOP,
        RETURN
    }

    private final SpiritEntity spirit;
    private final BlockBreaker breaker;
    private final ChopScaffold scaffold;
    private final ChopAscent ascent;
    private final ChopPass pass;
    private final ChopReturn chopReturn;
    private Tree tree;
    private TreeSpecies species;
    private Phase phase;
    private int standingLogs;
    private @Nullable ChopOutcome refusal;

    TreeChop(SpiritEntity spirit, IdFilter logFilter, boolean replant, boolean orchard, TreeView view) {
        this.spirit = spirit;
        this.breaker = new BlockBreaker(spirit);
        this.scaffold = new ChopScaffold(spirit);
        this.pass = new ChopPass(spirit, view, orchard, breaker);
        this.ascent = new ChopAscent(spirit, view, pass, scaffold);
        this.chopReturn = new ChopReturn(spirit, scaffold, new ChopHarvest(spirit, logFilter, replant, orchard, view));
    }

    void begin(Tree target) {
        tree = target;
        species = ChopSpecies.of(spirit.level(), target);
        standingLogs = 0;
        ChopAscent.Step step =
                ascent.begin(target, new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()));
        if (step == ChopAscent.Step.NO_STAND) {
            refusal = ChopOutcome.simple(ChopOutcome.State.UNREACHABLE);
        }
        phase = step == ChopAscent.Step.READY ? Phase.CHOP : Phase.ASCENT;
        BlockPos stand = ascent.stand();
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

    ChopOutcome tick() {
        if (refusal != null) {
            ChopOutcome outcome = refusal;
            refusal = null;
            return outcome;
        }
        return switch (phase) {
            case ASCENT -> ascent();
            case CHOP -> chop();
            case RETURN -> returning();
        };
    }

    void cancel() {
        breaker.cancel();
        ascent.cancel();
        chopReturn.cancel();
    }

    void suspend() {
        ascent.suspend();
        pass.suspend();
        chopReturn.suspend();
    }

    private ChopOutcome ascent() {
        return switch (ascent.tick()) {
            case RUNNING -> ChopOutcome.running();
            case READY -> resumeChop();
            case RETRY -> retryStand();
            case GIVE_UP -> giveUp();
            case NO_STAND -> ChopOutcome.simple(ChopOutcome.State.UNREACHABLE);
        };
    }

    private ChopOutcome retryStand() {
        BlockPos feet = ascent.stand();
        List<BlockCoord> rest =
                pass.rest(feet == null ? tree.base() : toCoord(feet)).order();
        if (rest.isEmpty()) {
            return startReturn();
        }
        if (ascent.pursue(rest) != null) {
            return ChopOutcome.running();
        }
        return giveUp();
    }

    private ChopOutcome giveUp() {
        if (pass.remaining() == 0) {
            return startReturn();
        }
        return ascent.tried() > 1 ? leftStanding() : ChopOutcome.simple(ChopOutcome.State.UNREACHABLE);
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
        BlockPos stand = ascent.stand();
        ChopPlan rest = pass.rest(stand == null ? tree.base() : toCoord(stand));
        pass.continueWith(rest);
        if (rest.order().isEmpty()) {
            return startReturn();
        }
        BlockPos picked = ascent.pursue(rest.order());
        if (picked == null) {
            return leftStanding();
        }
        LOGGER.info(
                "[chop] {} у дерева {}: после прохода стоят {} брёвен — добивка со стоянки {} (попытка {})",
                spirit.getName().getString(),
                tree.base(),
                rest.order().size(),
                picked.toShortString(),
                ascent.tried());
        phase = Phase.ASCENT;
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
        return startReturn();
    }

    private ChopOutcome startReturn() {
        chopReturn.begin(tree, species, standingLogs);
        phase = Phase.RETURN;
        return ChopOutcome.running();
    }

    private ChopOutcome returning() {
        ChopOutcome outcome = chopReturn.tick();
        if (outcome.state() == ChopOutcome.State.FAILED) {
            cancel();
        }
        return outcome;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
