package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TicketTargetsTests {
    private static final String GOOD = "repair:hut";
    private static final String BROKEN = "world:backfill:1,64,2";

    @Test
    void decode_UnreadableTarget_IsReportedAndOthersSurvive() {
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        raw.put(GOOD, Map.of("Target", List.of(1, 64, 2)));
        raw.put(BROKEN, Map.of("Target", List.of(1, 64)));

        TicketTargets decoded = TicketTargets.decode(raw);

        assertEquals(
                Map.of(GOOD, TicketTarget.at(new BlockCoord(1, 64, 2))),
                decoded.targets(),
                "Одна битая цель не отменяет загрузку доски: остальные задания сохраняют свои точки");
        assertEquals(
                List.of(BROKEN),
                decoded.broken().stream().map(TicketTargets.Broken::id).toList(),
                "Битая цель названа по id: иначе задание молча исполнилось бы у дома");
    }

    @Test
    void decode_BrokenTargetReason_NamesTheField() {
        TicketTargets decoded = TicketTargets.decode(Map.of(BROKEN, Map.of("Target", List.of(1, 64))));

        assertTrue(
                decoded.broken().get(0).message().contains("Target"),
                "Причина отказа называет поле NBT, а не только id: "
                        + decoded.broken().get(0).message());
    }

    @Test
    void decode_ReadableTargets_AreRestoredWithoutBroken() {
        TicketTargets decoded = TicketTargets.decode(Map.of(GOOD, Map.of("Target", List.of(-3, 70, 5))));

        assertEquals(
                Map.of(GOOD, TicketTarget.at(new BlockCoord(-3, 70, 5))),
                decoded.targets(),
                "Читаемая цель восстанавливается целиком");
        assertTrue(decoded.broken().isEmpty(), "Читаемая доска не даёт битых целей");
    }

    @Test
    void dropBroken_BrokenTicket_IsCancelledSoGraphRepostsIt() {
        Board board = new Board(() -> 0L);
        board.submit(new BoardTicket(
                BROKEN,
                BoardCase.FILL_CRATER,
                Optional.empty(),
                1,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of()));

        List<String> cancelled = TicketTargets.dropBroken(board, List.of(BROKEN, "world:backfill:нет-такого"));

        assertEquals(
                List.of(BROKEN),
                cancelled,
                "Снимается только найденное битое задание: чужой id не считается отменённым");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf(BROKEN),
                "Задание без читаемой цели снимается: иначе оно исполнялось бы у дома или висело навсегда");
    }
}
