package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class MineLevels {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "mine_level_";

    private MineLevels() {}

    static boolean post(GraphPost post, String node, int priority, Map<String, Integer> need) {
        ServerLevel level = post.level;
        List<SpiritEntity> spirits = post.spirits;
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty() || !node.startsWith(PREFIX)) {
            return false;
        }
        int levelValue;
        try {
            levelValue = Integer.parseInt(node.substring(PREFIX.length()));
        } catch (NumberFormatException notLevel) {
            return false;
        }
        BoardStatus status = post.service.board().statusOf(node).orElse(null);
        if (status == BoardStatus.DONE
                || status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED) {
            return true;
        }
        Optional<StageSite> stage = SettlementPlotStore.get(level).site();
        BlockCoord home = stage.map(StageSite::mineEntrance)
                .orElseGet(() -> SettlementPlotStore.get(level)
                        .plot()
                        .map(plot -> plot.anchor())
                        .orElseGet(() -> homeOf(spirits)));
        BlockCoord site;
        try {
            site = MineDescentBottom.standOf(home).orElse(null);
        } catch (IllegalStateException | IllegalArgumentException unbuildable) {
            LOGGER.warn(
                    "Уровень шахты {} не поставлен: низ спуска не считается от дома {}: {}",
                    levelValue,
                    home,
                    unbuildable.getMessage());
            return false;
        }
        if (site == null) {
            LOGGER.warn(
                    "Уровень шахты {} не поставлен: нет схемы спуска {}", levelValue, TicketPlans.MINE_DESCENT_SCHEME);
            return false;
        }
        post.submit(
                post.leveled(
                        post.ticket(
                                node,
                                BoardCase.BUILD_MINE,
                                Optional.empty(),
                                priority,
                                BoardTicket.ALWAYS_CAUSE,
                                Map.copyOf(need)),
                        levelValue,
                        List.of(BoardLedger.key(BoardCase.BUILD_MINE, levelValue - 1))),
                TicketTarget.at(site));
        return true;
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        return spirits.stream()
                .flatMap(spirit -> spirit.home().stream())
                .findFirst()
                .orElse(new BlockCoord(0, 64, 0));
    }
}
