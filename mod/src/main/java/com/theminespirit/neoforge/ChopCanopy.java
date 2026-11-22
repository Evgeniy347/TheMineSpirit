package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopMode;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeCanopy;
import com.theminespirit.core.forestry.api.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class ChopCanopy {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final TreeView view;
    private final ChopMode mode;
    private final BlockBreaker breaker;
    private Tree tree;
    private List<BlockCoord> plan = List.of();
    private int index;
    private int removed;
    private int skipped;
    private boolean breaking;

    ChopCanopy(SpiritEntity spirit, TreeView view, ChopMode mode) {
        this.spirit = spirit;
        this.view = view;
        this.mode = mode;
        this.breaker = new BlockBreaker(spirit);
    }

    void begin(Tree target) {
        tree = target;
        index = 0;
        removed = 0;
        skipped = 0;
        breaking = false;
        plan = mode == ChopMode.SETTLEMENT
                ? TreeCanopy.clearance(target, view, this::plannedCell)
                : TreeCanopy.plan(target, view);
        if (plan.isEmpty()) {
            return;
        }
        LOGGER.info(
                "[chop] {} у дерева {}: снимаю крону, в плане {} блок(ов) листвы",
                spirit.getName().getString(),
                target.base(),
                plan.size());
    }

    boolean active() {
        return !plan.isEmpty() && index < plan.size();
    }

    boolean tick() {
        while (index < plan.size()) {
            if (breaking) {
                if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                    return true;
                }
                breaking = false;
                removed++;
                index++;
                continue;
            }
            BlockPos pos = toPos(plan.get(index));
            if (breaker.beginChop(pos, false) instanceof BlockBreaker.Started) {
                breaking = true;
                return true;
            }
            skipped++;
            index++;
        }
        finish();
        return false;
    }

    void cancel() {
        breaker.cancel();
        plan = List.of();
        index = 0;
    }

    void suspend() {
        breaker.cancel();
        breaking = false;
    }

    int removed() {
        return removed;
    }

    int left() {
        return Math.max(0, plan.size() - removed);
    }

    private boolean plannedCell(BlockCoord cell) {
        if (!(spirit.level() instanceof ServerLevel server)) {
            return false;
        }
        return SettlementPlotStore.liveSite(server)
                .map(site ->
                        site.planCell(cell.x(), cell.y(), cell.z()) || site.orchardTree(cell.x(), cell.y(), cell.z()))
                .orElse(false);
    }

    private void finish() {
        if (removed > 0) {
            LOGGER.info(
                    "[chop] {} у дерева {}: снято листвы {}, без доступа {}, осталось {}",
                    spirit.getName().getString(),
                    tree.base(),
                    removed,
                    skipped,
                    left());
        }
        plan = List.of();
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
