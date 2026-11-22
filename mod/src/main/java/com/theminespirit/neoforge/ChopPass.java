package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopMode;
import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.api.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class ChopPass {
    private static final Logger LOGGER = LogUtils.getLogger();

    enum Step {
        RUNNING,
        PROTECTED,
        PASS_DONE
    }

    private final SpiritEntity spirit;
    private final TreeView view;
    private final ChopMode mode;
    private final BlockBreaker breaker;
    private Tree tree;
    private ChopPlan plan = new ChopPlan(List.of());
    private int index;
    private boolean breaking;
    private boolean skipLogged;

    ChopPass(SpiritEntity spirit, TreeView view, ChopMode mode, BlockBreaker breaker) {
        this.spirit = spirit;
        this.view = view;
        this.mode = mode;
        this.breaker = breaker;
    }

    void begin(Tree target, BlockCoord from) {
        tree = target;
        plan = ChopPlan.of(target, from, this::protectedCell);
        index = 0;
        breaking = false;
        skipLogged = false;
    }

    Step tick() {
        if (breaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return Step.RUNNING;
            }
            breaking = false;
            index++;
        }
        while (index < plan.order().size()) {
            BlockCoord log = plan.order().get(index);
            BlockBreaker.Begin begin = breaker.beginChop(toPos(log), mode == ChopMode.ORCHARD);
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return Step.RUNNING;
            }
            if (begin instanceof BlockBreaker.Protected) {
                return Step.PROTECTED;
            }
            if (begin instanceof BlockBreaker.Refused refused && !skipLogged) {
                skipLogged = true;
                LOGGER.warn(
                        "[chop] {} у дерева {}: бревно {} не берётся с места — {}: {}",
                        spirit.getName().getString(),
                        tree.base(),
                        log,
                        refused.reason(),
                        refused.message());
            }
            index++;
        }
        plan = rest(tree.base());
        index = 0;
        return Step.PASS_DONE;
    }

    ChopPlan rest(BlockCoord from) {
        return plan.rest(view, from, this::protectedCell);
    }

    void continueWith(ChopPlan rest) {
        plan = rest;
        index = 0;
        breaking = false;
    }

    void suspend() {
        breaker.cancel();
        breaking = false;
    }

    List<BlockCoord> order() {
        return plan.order();
    }

    int remaining() {
        return plan.rest(view, tree.base(), this::protectedCell).order().size();
    }

    private boolean protectedCell(BlockCoord log) {
        BlockPos pos = toPos(log);
        BlockState state = spirit.level().getBlockState(pos);
        BlockProtection.Decision decision = mode == ChopMode.ORCHARD
                ? spirit.protectionGate().checkHarvest(pos, state)
                : spirit.protectionGate().checkBreak(pos, state);
        return decision instanceof BlockProtection.Refused;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
