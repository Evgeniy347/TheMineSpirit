package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

final class ChopPass {
    enum Step {
        RUNNING,
        PROTECTED,
        PASS_DONE
    }

    private final SpiritEntity spirit;
    private final TreeView view;
    private final boolean orchard;
    private final BlockBreaker breaker;
    private Tree tree;
    private ChopPlan plan = new ChopPlan(List.of());
    private int index;
    private boolean breaking;

    ChopPass(SpiritEntity spirit, TreeView view, boolean orchard, BlockBreaker breaker) {
        this.spirit = spirit;
        this.view = view;
        this.orchard = orchard;
        this.breaker = breaker;
    }

    void begin(Tree target, BlockCoord from) {
        tree = target;
        plan = ChopPlan.of(target, from, this::protectedCell);
        index = 0;
        breaking = false;
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
            BlockBreaker.Begin begin = breaker.beginChop(toPos(log), orchard);
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return Step.RUNNING;
            }
            if (begin instanceof BlockBreaker.Protected) {
                return Step.PROTECTED;
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
        BlockProtection.Decision decision = orchard
                ? spirit.protectionGate().checkHarvest(pos, state)
                : spirit.protectionGate().checkBreak(pos, state);
        return decision instanceof BlockProtection.Refused;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
