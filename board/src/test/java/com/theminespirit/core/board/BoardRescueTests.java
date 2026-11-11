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
    void rescue_HelperCompletes_KeepsRescueeMarkUntilEscapeSeen() {
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

        assertTrue(
                board.stuck().isStuck(RESCUEE, now[0] + 1),
                "Закрытие задания само по себе не подтверждает выход: метка спасённого держится, пока "
                        + "наблюдение не покажет его на поверхности (observe25 B6)");
        board.stuck().see(RESCUEE, new BlockCoord(AT.x() + 3, AT.y() + 4, AT.z()), now[0] + 2, true);
        assertFalse(
                board.stuck().isStuck(RESCUEE, now[0] + 2),
                "Наблюдённый выход снимает метку: иначе спасённый навсегда остаётся без работы (observe23 A)");
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
    void dispatch_NoPathSpiritAfterTtl_TakesWorkAgain() {
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
                chosen.isPresent(),
                "Устаревшая метка «пути нет» не держит духа вечно: после срока он снова берёт работу, "
                        + "а повторный провал поставит свежую метку (observe25 B1)");
        assertFalse(
                round.why(gather, List.of(candidate(RESCUEE, "spirit-2"))).contains("не двигается"),
                "Причина выбора больше не называет духа недвижимым: метка истекла");
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

    @Test
    void rescue_PrematureCompletion_KeepsMarkAndReopensForRetry() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        BoardTicket rescue = rescue(RESCUEE, "graph:rescue_spirit@1000");
        board.submit(rescue);
        board.claim(rescue.id(), RESCUEE, now[0]);

        assertTrue(board.complete(rescue.id()), "Спасение закрылось по отчёту задачи, но выход не наблюдён");
        assertTrue(
                board.stuck().isStuck(RESCUEE, now[0]),
                "Пока дух не показан на поверхности, метка держится: иначе он снова берёт обычную "
                        + "работу из пещеры и падает обратно (observe25 B6)");
        assertTrue(
                board.reopen(rescue.id()),
                "Граф переоткрывает закрытое спасение, пока метка жива: выход повторяется, а не бросается");

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(rescue(RESCUEE, "graph:rescue_spirit@1000")),
                "Тот же эпизод снова ставится на доску после переоткрытия");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(rescue.id(), RESCUEE, now[0]),
                "Дух снова берёт собственное спасение: обычная работа ему по-прежнему закрыта");
    }

    @Test
    void waitReason_MovedInsidePit_NamesCurrentPosition() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "провал движения");
        BlockCoord moved = new BlockCoord(AT.x() + 3, AT.y() - 2, AT.z() + 1);
        now[0] += 40;
        board.stuck().see(RESCUEE, moved, now[0]);
        BoardTicket gather = new BoardTicket(
                "graph:make:jungle_log",
                BoardCase.GATHER_MATERIALS,
                Optional.of(RESCUEE),
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                now[0],
                1,
                "graph:make:jungle_log",
                List.of(),
                Map.of());
        board.submit(gather);

        String why = BoardWhy.waitReason(board, gather, new StockView(Map.of()), now[0])
                .orElseThrow();

        assertTrue(
                why.contains(moved.toString()),
                "waitReason называет текущее место духа, а не устаревшую точку постановки метки " + "(observe25 B15): "
                        + why);
        assertFalse(why.contains(AT.toString()), "Старые координаты в причине ожидания не показываются: " + why);
    }

    @Test
    void claimable_OwnRescueOfStuckSpirit_StaysAssignedToIt() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "провал движения");
        BoardTicket rescue = rescue(RESCUEE, "graph:rescue_spirit@1000");
        board.submit(rescue);
        assertEquals(BoardOutcome.CLAIMED, board.claim(rescue.id(), RESCUEE, now[0]), "Дух берёт своё спасение");

        board.claimable(new StockView(Map.of()), now[0] + 1);

        assertEquals(
                Optional.of(BoardStatus.ASSIGNED),
                board.statusOf(rescue.id()),
                "Своё спасение нельзя отбирать у застрявшего: claimable снимал задание с него каждый тик и тут же "
                        + "выдавал обратно — dig_out перезапускался 20 раз в секунду и каждый раз искал путь заново "
                        + "(сервер 0.1.3595, тик 3,4 с; rescue:f58b83b6 ASSIGNED 100 раз за 25 с)");
        assertTrue(
                board.assignedTo(RESCUEE).contains(rescue),
                "Задание спасения остаётся за спасённым: снятие возвращает его тому же духу и крутит пересборку");
    }

    @Test
    void claimable_RescueHeldByStuckHelper_ReleasedToFreeSpirit() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "провал движения");
        stuck(board, HELPER, "провал движения");
        BoardTicket rescue = rescue(RESCUEE, "graph:rescue_spirit@1000");
        board.submit(rescue);
        board.claim(rescue.id(), HELPER, now[0]);

        board.claimable(new StockView(Map.of()), now[0] + 1);

        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(rescue.id()),
                "Застрявшего помощника спасение не держит: задание возвращается доске и достаётся свободному духу");
    }

    @Test
    void claimable_NormalTicketOfStuckSpirit_ReleasedToFreeSpirit() {
        long[] now = {1000};
        Board board = new Board(() -> now[0]);
        stuck(board, RESCUEE, "провал движения");
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
        board.claim(gather.id(), RESCUEE, now[0]);

        board.claimable(new StockView(Map.of()), now[0] + 1);

        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(gather.id()),
                "Обычная работа застрявшего возвращается доске: иначе она ждёт духа, который не двигается");
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
