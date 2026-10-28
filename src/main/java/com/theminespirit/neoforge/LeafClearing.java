package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.LeafObstacles;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class LeafClearing {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ROUNDS = 3;

    private enum Step {
        RUNNING,
        OUT_OF_REACH,
        DONE
    }

    private final SpiritEntity spirit;
    private final TreeView view;
    private final Approach approach;
    private final BlockBreaker breaker;
    private Tree tree;
    private List<BlockCoord> plan = List.of();
    private int index;
    private int rounds;
    private boolean breaking;
    private boolean active;

    LeafClearing(SpiritEntity spirit, TreeView view) {
        this.spirit = spirit;
        this.view = view;
        this.approach = new Approach(spirit);
        this.breaker = new BlockBreaker(spirit);
    }

    boolean active() {
        return active;
    }

    void begin(Tree target, BlockCoord from) {
        tree = target;
        plan = LeafObstacles.blockingStand(target, view, from);
        index = 0;
        rounds = 0;
        breaking = false;
        active = !plan.isEmpty();
        if (!active) {
            return;
        }
        LOGGER.info(
                "[chop] {} у дерева {}: стоянку у ствола закрывает листва, ломаю руками {} блок(ов)",
                spirit.getName().getString(),
                target.base(),
                plan.size());
        walkToTree();
    }

    boolean tick() {
        if (!active) {
            return false;
        }
        if (approach.isActive() && approach.tick() == Approach.State.MOVING) {
            return true;
        }
        return switch (step()) {
            case RUNNING -> true;
            case DONE -> finish();
            case OUT_OF_REACH -> retry();
        };
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
        active = false;
    }

    void suspend() {
        approach.suspend();
        breaker.cancel();
        breaking = false;
    }

    private boolean retry() {
        rounds++;
        if (rounds > MAX_ROUNDS) {
            LOGGER.warn(
                    "[chop] {} у дерева {}: листва у стоянки так и осталась вне досягаемости — подхожу к стволу как есть",
                    spirit.getName().getString(),
                    tree.base());
            return finish();
        }
        walkToTree();
        return true;
    }

    private boolean finish() {
        active = false;
        return false;
    }

    private void walkToTree() {
        approach.begin(Vec3.atCenterOf(toPos(tree.base())), ChopTuning.STAND_DISTANCE);
    }

    private Step step() {
        while (index < plan.size()) {
            if (breaking) {
                if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                    return Step.RUNNING;
                }
                breaking = false;
                index++;
                continue;
            }
            BlockPos pos = toPos(plan.get(index));
            double reach = BlockBreaker.REACH * BlockBreaker.REACH;
            if (spirit.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach) {
                return Step.OUT_OF_REACH;
            }
            BlockBreaker.Begin start = breaker.beginWithSlot(pos, ToolChoice.HAND);
            if (start instanceof BlockBreaker.Started) {
                breaking = true;
                return Step.RUNNING;
            }
            index++;
        }
        return Step.DONE;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
