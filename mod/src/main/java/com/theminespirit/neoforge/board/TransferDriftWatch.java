package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DriftWatch;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class TransferDriftWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DELIVER = "deliver";

    private final DriftWatch drift = new DriftWatch();

    void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits) {
        long now = level.getGameTime();
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM || !open(service, ticket.id())) {
                continue;
            }
            UUID carrier = ticket.assignee().orElse(null);
            SpiritEntity spirit = find(spirits, carrier);
            if (spirit == null || !DELIVER.equals(spirit.currentTaskScript().orElse(null))) {
                continue;
            }
            Optional<DriftWatch.Drift> found = drift.observe(carrier, at(spirit), now);
            if (found.isEmpty()) {
                continue;
            }
            DriftWatch.Drift value = found.orElseThrow();
            LOGGER.warn(
                    "[transfer] дух {} по передаче {} колеблется: путь {} блоков, размах {}, возврат {} — маршрут "
                            + "перестраивается",
                    spirit.displayName(),
                    ticket.id(),
                    String.format(Locale.ROOT, "%.1f", value.path()),
                    String.format(Locale.ROOT, "%.1f", value.span()),
                    String.format(Locale.ROOT, "%.1f", value.net()));
            service.cancel(level, ticket.id());
        }
    }

    private static boolean open(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status != null && TransferWatch.open(status);
    }

    private static SpiritEntity find(List<SpiritEntity> spirits, UUID id) {
        if (id == null) {
            return null;
        }
        return spirits.stream()
                .filter(spirit -> spirit.getUUID().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static BlockCoord at(SpiritEntity spirit) {
        return new BlockCoord(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
    }
}
