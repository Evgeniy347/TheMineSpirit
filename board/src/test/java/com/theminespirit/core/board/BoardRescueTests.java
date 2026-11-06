package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardRescueTests {
    private static final UUID RESCUEE = UUID.fromString("c48663b4-da59-43d9-9804-81a680580300");
    private static final UUID HELPER = UUID.fromString("60776689-3fb3-4070-a4d3-dfb99af78008");
    private static final BlockCoord AT = new BlockCoord(-211, 71, -495);
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void rescue_HelperCompletes_ReleasesRescueeMark() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        BoardTicket rescue = rescue(RESCUEE, "graph:rescue_spirit@1000");
        assertEquals(BoardOutcome.ACCEPTED, board.submit(rescue), "Помощнику ставится задание спасения застрявшего");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(rescue.id(), HELPER, now[0]),
                "Свободный помощник берёт спасение: сам застрявший выйти не может");
        assertTrue(board.complete(rescue.id()), "Выполненное спасение закрывает задание");

        assertFalse(
                board.stuck().isStuck(RESCUEE, now[0] + 1),
                "Спасение снимает метку с того, кого спасли, а не с помощника: иначе застрявший "
                        + "навсегда остаётся без работы (observe23 A)");
    }

    @Test
    void dispatch_HelperRescue_StuckSpiritRefused() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        stuck(board, HELPER, "маршрут не найден: цель BlockCoord[x=-207, y=68, z=-432]");
        BoardTicket rescue = rescue(RESCUEE, "graph:rescue_spirit@1000");
        board.submit(rescue);

        DispatchRound round = new DispatchRound(board, now[0]);
        Optional<DispatchRound.Candidate> chosen = round.choose(rescue, List.of(candidate(HELPER, "spirit-5")));

        assertTrue(
                chosen.isEmpty(),
                "Застрявший дух не может быть помощником: он сам не двигается и повторит провал "
                        + "спасения (observe23 A)");
        assertTrue(
                round.why(rescue, List.of(candidate(HELPER, "spirit-5"))).contains("не двигается"),
                "Причина отказа называет застревание помощника, а не молчит");
        assertTrue(
                round.choose(rescue, List.of(candidate(UUID.randomUUID(), "spirit-1")))
                        .isPresent(),
                "Свободный дух по-прежнему берёт спасение: запрет адресный, а не на весь кейс");
    }

    @Test
    void dispatch_NoPathSpiritAfterTtl_DistantWorkRefused() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        BoardTicket gather = new BoardTicket(
                "graph:make:jungle_log",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                now[0],
                1,
                "graph:make:jungle_log",
                List.of(),
                Map.of());
        board.submit(gather);
        now[0] += BoardTuning.STUCK_TTL_TICKS + 1;

        DispatchRound round = new DispatchRound(board, now[0]);
        Optional<DispatchRound.Candidate> chosen = round.choose(gather, List.of(candidate(RESCUEE, "spirit-2")));

        assertTrue(
                chosen.isEmpty(),
                "«Пути нет» — факт о духе, а не тайм-аут: дух без пути домой не получает дальнюю работу, "
                        + "пока не вышел или его не спасли (observe23 B)");
        assertTrue(
                round.why(gather, List.of(candidate(RESCUEE, "spirit-2"))).contains("не двигается"),
                "Причина ожидания честно говорит, что дух не двигается, а не «нет духов»");
    }

    @Test
    void retain_RescueCauseWithEpisode_StaysOnBoard() {
        BoardTicket rescue = rescue(RESCUEE, "graph:rescue_spirit@1000");

        assertTrue(
                SupplyGate.retains(rescue, Set.of("graph:rescue_spirit")),
                "Причина с тиком эпизода — та же причина графа: иначе доска снимет живое спасение как "
                        + "оторванное (observe23 A)");
    }

    @Test
    void rescue_DoneThenNewStuckEpisode_Reopens() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "провал движения");
        BoardTicket first = rescue(RESCUEE, "graph:rescue_spirit@1000");
        board.submit(first);
        board.claim(first.id(), RESCUEE, now[0]);
        assertTrue(board.complete(first.id()), "Первый выход выполнен и закрыт");

        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(rescue(RESCUEE, "graph:rescue_spirit@1000")),
                "Повторная постановка того же эпизода не переоткрывает закрытое спасение");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(rescue(RESCUEE, "graph:rescue_spirit@9000")),
                "Новое застревание — новый rescue: причина несёт тик эпизода, иначе DONE навсегда "
                        + "закрывает спасение (observe23 A)");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(first.id()),
                "Переоткрытое спасение снова стоит в очереди доски");
    }

    private static BoardTicket rescue(UUID spirit, String cause) {
        return new BoardTicket(
                "rescue:" + spirit,
                BoardCase.RESCUE_SPIRIT,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                1000,
                1,
                cause,
                List.of(),
                Map.of());
    }

    private static void stuck(Board board, UUID spirit, String reason) {
        board.stuck().see(spirit, AT, board.now());
        board.stuck().mark(spirit, board.now(), reason);
    }

    private static DispatchRound.Candidate candidate(UUID id, String name) {
        return new DispatchRound.Candidate(id, name, null, List.of("minecraft:stone_pickaxe"), FED, SpiritView.free());
    }
}
