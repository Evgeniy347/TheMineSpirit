package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import org.slf4j.Logger;

final class SettleNotes {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SettleNotes() {}

    static void note(
            SpiritEntity spirit, BoardTicket ticket, boolean success, Map<String, Long> supplyClosed, long now) {
        if (ticket.boardCase() == BoardCase.SUPPLY_SITE) {
            supplyClosed.put(ticket.id(), now);
        }
        if (ticket.boardCase() == BoardCase.RESTOCK_FOOD) {
            LOGGER.info(
                    "[food] дух {} {} задание еды {}: food {} saturation {} (питание {})",
                    spirit.displayName(),
                    success ? "завершил" : "провалил",
                    ticket.id(),
                    spirit.hunger().foodLevel(),
                    spirit.hunger().state().saturation(),
                    spirit.hunger().personalNutrition());
        }
    }
}
