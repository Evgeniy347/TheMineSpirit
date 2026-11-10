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
        Optional<StageSite> stage = SettlementPlotStore.liveSite(level);
        Optional<BlockCoord> home = home(
                stage.map(StageSite::mineEntrance),
                SettlementPlotStore.get(level).plot().map(plot -> plot.anchor()),
                spirits.stream().flatMap(spirit -> spirit.home().stream()).toList());
        if (home.isEmpty()) {
            LOGGER.warn(
                    "Уровень шахты {} не поставлен: нет ни размеченного участка, ни дома духа — место спуска "
                            + "неизвестно",
                    levelValue);
            return false;
        }
        BlockCoord homePoint = home.orElseThrow();
        BlockCoord site;
        try {
            site = MineDescentBottom.standOf(homePoint).orElse(null);
        } catch (IllegalStateException | IllegalArgumentException unbuildable) {
            LOGGER.warn(
                    "Уровень шахты {} не поставлен: низ спуска не считается от дома {}: {}",
                    levelValue,
                    homePoint,
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

    static Optional<BlockCoord> home(
            Optional<BlockCoord> entrance, Optional<BlockCoord> plotAnchor, List<BlockCoord> spiritHomes) {
        return entrance.or(() -> plotAnchor).or(() -> spiritHomes.stream().findFirst());
    }
}
