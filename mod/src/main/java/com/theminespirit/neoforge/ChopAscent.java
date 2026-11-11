package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class ChopAscent {
    enum Step {
        RUNNING,
        READY,
        RETRY,
        GIVE_UP,
        NO_STAND
    }

    private enum Stage {
        APPROACH,
        CLIMB,
        CLEAR
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final Approach approach;
    private final LeafClearing clearing;
    private final ChopScaffold scaffold;
    private final ChopPursuit pursuit;
    private final ChopPass pass;
    private Tree tree;
    private Stage stage;

    ChopAscent(SpiritEntity spirit, TreeView view, ChopPass pass, ChopScaffold scaffold) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
        this.clearing = new LeafClearing(spirit, view);
        this.scaffold = scaffold;
        this.pursuit = new ChopPursuit(spirit);
        this.pass = pass;
    }

    Step begin(Tree target, BlockCoord from) {
        tree = target;
        pursuit.begin(target);
        scaffold.reset();
        clearing.begin(target, from);
        return startApproach();
    }

    Step tick() {
        return switch (stage) {
            case APPROACH -> approach();
            case CLIMB -> climb();
            case CLEAR -> clear();
        };
    }

    @Nullable
    BlockPos pursue(List<BlockCoord> rest) {
        ChopStand.Placement picked = pursuit.next(rest);
        if (picked == null) {
            return null;
        }
        startApproachTo(picked);
        return picked.feet();
    }

    @Nullable
    BlockPos stand() {
        return pursuit.stand();
    }

    int tried() {
        return pursuit.tried();
    }

    void cancel() {
        approach.cancel();
        clearing.cancel();
        scaffold.cancel();
    }

    void suspend() {
        approach.suspend();
        clearing.suspend();
    }

    private Step startApproach() {
        ChopStand.Placement picked = pursuit.first();
        if (picked == null && clearing.active()) {
            stage = Stage.CLEAR;
            return Step.RUNNING;
        }
        if (picked == null) {
            LOGGER.warn(
                    "[chop] {}: у дерева {} нет стоянки вплотную к стволу на достижимой высоте — рубка брошена",
                    spirit.getName().getString(),
                    tree.base());
            return Step.NO_STAND;
        }
        pass.begin(tree, toCoord(picked.feet()));
        startApproachTo(picked);
        return Step.RUNNING;
    }

    private void startApproachTo(ChopStand.Placement picked) {
        BlockCoord base = picked.climbFrom();
        if (base == null) {
            stage = Stage.APPROACH;
            approach.begin(Vec3.atBottomCenterOf(picked.feet()), ChopTuning.CHOP_ARRIVAL);
            return;
        }
        stage = Stage.CLIMB;
        approach.begin(Vec3.atBottomCenterOf(toPos(base)), ChopTuning.CHOP_ARRIVAL);
    }

    private Step approach() {
        return switch (approach.tick()) {
            case MOVING -> Step.RUNNING;
            case ARRIVED -> Step.READY;
            case STUCK, TIMEOUT -> Step.RETRY;
        };
    }

    private Step climb() {
        if (!scaffold.climbing()) {
            return switch (approach.tick()) {
                case MOVING -> Step.RUNNING;
                case ARRIVED -> startScaffold();
                case STUCK, TIMEOUT -> Step.RETRY;
            };
        }
        return switch (scaffold.tickClimb()) {
            case RUNNING -> Step.RUNNING;
            case BUILT -> Step.READY;
            case FAILED -> {
                LOGGER.warn(
                        "[chop] {} у дерева {}: подмости к стоянке {} не построены — {}",
                        spirit.getName().getString(),
                        tree.base(),
                        pursuit.stand(),
                        scaffold.failureReason());
                yield Step.RETRY;
            }
        };
    }

    private Step startScaffold() {
        BlockCoord base = pursuit.climbFrom();
        BlockPos feet = pursuit.stand();
        if (base == null || feet == null) {
            return Step.RETRY;
        }
        if (!scaffold.begin(base, feet.getY())) {
            LOGGER.warn(
                    "[chop] {} у дерева {}: до стоянки {} не подняться — {}",
                    spirit.getName().getString(),
                    tree.base(),
                    feet.toShortString(),
                    scaffold.failureReason());
            return Step.GIVE_UP;
        }
        return scaffold.climbing() ? Step.RUNNING : Step.READY;
    }

    private Step clear() {
        if (clearing.tick()) {
            return Step.RUNNING;
        }
        return startApproach();
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
