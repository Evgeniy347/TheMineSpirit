package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.UpgradePlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class HouseLevels {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HouseLevels() {}

    static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        OptionalInt next =
                BuildingLevels.nextLevel(BoardCase.BUILD_HOUSE, service.board().ledger());
        if (next.isEmpty() || next.getAsInt() < 2) {
            return;
        }
        int levelValue = next.getAsInt();
        BoardLedger ledger = service.board().ledger();
        if (BuildingLevels.state(BoardCase.BUILD_HOUSE, levelValue, ledger) != BuildingLevels.State.UPGRADING
                || !SettlementStage.allows(BoardCase.BUILD_HOUSE, levelValue, ledger)
                || FirstStepsRunner.stageOneBuilding(service)) {
            return;
        }
        String id = "house_level_" + levelValue;
        if (active(service, id)) {
            return;
        }
        Optional<StageSite> stage = SettlementPlotStore.get(level).site();
        if (stage.isEmpty()) {
            LOGGER.warn("Дом уровня {} не поставлен: у поселения нет участка для хижины", levelValue);
            return;
        }
        String scheme = BuildingLevels.scheme(BoardCase.BUILD_HOUSE, levelValue).orElse(null);
        if (scheme == null) {
            LOGGER.warn("Дом уровня {} не поставлен: в stages.json нет схемы уровня", levelValue);
            return;
        }
        String previous = levelValue > 1
                ? BuildingLevels.scheme(BoardCase.BUILD_HOUSE, levelValue - 1).orElse(null)
                : null;
        BlockCoord origin = stage.orElseThrow().houseOrigin();
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        BoardPriorities.effective(BoardCase.BUILD_HOUSE, PriorityContext.none()),
                        now,
                        levelValue,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(BoardLedger.key(BoardCase.BUILD_HOUSE, levelValue - 1)),
                        UpgradePlan.required(previous, scheme)),
                TicketTarget.at(origin).withScheme(scheme));
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }
}
