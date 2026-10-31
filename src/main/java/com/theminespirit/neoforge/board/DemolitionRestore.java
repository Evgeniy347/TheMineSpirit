package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class DemolitionRestore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DemolitionRestore() {}

    static boolean restoreLost(
            ServerLevel level,
            BoardService service,
            StageSite site,
            List<BoardTicket> open,
            List<BlockCoord> stray,
            List<PlannedBlock> blocks,
            DemolitionTickets.Placement placement) {
        String current = stray.isEmpty() ? "" : DemolitionTickets.idFor(stray);
        boolean restored = false;
        for (BoardTicket ticket : open) {
            if (service.hasTarget(ticket.id())) {
                continue;
            }
            if (!stray.isEmpty() && ticket.id().equals(current)) {
                DemolitionTickets.restore(service, ticket, site, blocks, placement);
                restored = true;
                LOGGER.info(
                        "[tidy] {}: цель уборки потеряна при перезапуске — восстановлена по текущему набору блоков",
                        ticket.id());
            } else {
                service.cancel(level, ticket.id());
                LOGGER.info(
                        "[tidy] {}: цель уборки потеряна при перезапуске, набор устарел — задание снято, "
                                + "уборка пересоздастся по текущему миру",
                        ticket.id());
            }
        }
        return restored;
    }
}
