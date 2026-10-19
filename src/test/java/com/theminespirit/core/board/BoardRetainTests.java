package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardRetainTests {

    private static Board boardAt(long now) {
        return new Board(() -> now);
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, String cause) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), BoardRules.priorityOf(boardCase), 0, 1, cause, List.of(), Map.of());
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, String cause, List<String> dependsOn) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), BoardRules.priorityOf(boardCase), 0, 1, cause, dependsOn, Map.of());
    }

    @Test
    void retain_DeadCause_DetachesTicket() {
        Board board = boardAt(0);
        board.submit(ticket("crater_1", BoardCase.FILL_CRATER, "crater:1:64:2"));

        List<String> detached = board.retain(Set.of(), 0);

        assertEquals(
                List.of("crater_1"),
                detached,
                "Исчезнувшая воронка снимает FILL_CRATER: причина отпала, а не срок истёк");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("crater_1"),
                "Снятое по актуальности задание помечается CANCELLED: prune уберёт его из очереди");
    }

    @Test
    void retain_LiveCause_KeepsTicket() {
        Board board = boardAt(0);
        board.submit(ticket("crater_1", BoardCase.FILL_CRATER, "crater:1:64:2"));

        List<String> detached = board.retain(Set.of("crater:1:64:2"), 0);

        assertEquals(List.of(), detached, "Живая причина оставляет задание: воронка на месте — задание висит");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("crater_1"),
                "Задание с живой причиной остаётся в выдаче");
    }

    @Test
    void retain_AlwaysCause_NeverDetached() {
        Board board = boardAt(0);
        board.submit(ticket("eat_1", BoardCase.EAT_TO_FULL, BoardTicket.ALWAYS_CAUSE));

        List<String> detached = board.retain(Set.of(), 0);

        assertEquals(
                List.of(),
                detached,
                "Постоянное задание не снимается никогда: always переживает любое снятие "
                        + "по актуальности, иначе вечное задание умрёт на первой пересборке");
    }

    @Test
    void retain_Cascade_DetachesDependants() {
        Board board = boardAt(0);
        board.submit(ticket("lure_1", BoardCase.LURE_ANIMAL, "animal:capture_1"));
        board.submit(ticket("dismantle_1", BoardCase.DISMANTLE_SHELTER, "shelter:5:64:5", List.of("lure_1")));

        List<String> detached = board.retain(Set.of("shelter:5:64:5"), 0);

        assertEquals(
                List.of("lure_1"),
                detached,
                "Напрямую снято только «приманить»: причина укрытия жива, само укрытие стоит");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED), board.statusOf("lure_1"), "«Приманить» снято: животное погибло");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("dismantle_1"),
                "«Разобрать укрытие» снято каскадом при живой причине: без приманки разбирать "
                        + "нечего, иначе задание ждало бы отменённого предшественника навсегда");
    }

    @Test
    void retain_DoneBuilding_StaysCondition() {
        Board board = boardAt(0);
        board.submit(ticket("pen_1", BoardCase.BUILD_PEN, "animal:capture_1"));
        board.submit(ticket("lure_1", BoardCase.LURE_ANIMAL, "animal:capture_1", List.of("pen_1")));
        board.claim("pen_1", UUID.randomUUID(), 0);
        board.complete("pen_1");

        board.retain(Set.of(), 0);

        assertEquals(
                Optional.of(BoardStatus.DONE),
                board.statusOf("pen_1"),
                "Выполненная постройка не трогается снятием: DONE остаётся условием " + "для других заданий");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("lure_1"),
                "Незавершённое с мёртвой причиной снято, выполненное — нет");
    }

    @Test
    void retain_EmptyCauses_DetachesOnlyNonAlways() {
        Board board = boardAt(0);
        board.submit(ticket("crater_1", BoardCase.FILL_CRATER, "crater:1:64:2"));
        board.submit(ticket("eat_1", BoardCase.EAT_TO_FULL, BoardTicket.ALWAYS_CAUSE));

        List<String> detached = board.retain(Set.of(), 0);

        assertEquals(
                List.of("crater_1"),
                detached,
                "Пустой набор причин снимает только не-always: постоянное остаётся, " + "событийное уходит");
    }

    @Test
    void retain_Twice_IsIdempotent() {
        Board board = boardAt(0);
        board.submit(ticket("crater_1", BoardCase.FILL_CRATER, "crater:1:64:2"));

        board.retain(Set.of(), 0);
        List<String> second = board.retain(Set.of(), 0);

        assertEquals(
                List.of(),
                second,
                "Двойное снятие идемпотентно: второе снятие не находит работу, иначе "
                        + "пересборка писала бы дубли в журнал");
    }

    @Property
    void retain_AnyCauses_IsDeterministic(@ForAll("causeSets") Set<String> live) {
        Board first = boardWithCraterAndEat();
        Board second = boardWithCraterAndEat();

        List<String> firstRun = first.retain(live, 0);
        List<String> secondRun = second.retain(live, 0);

        assertEquals(
                firstRun,
                secondRun,
                "Снятие детерминировано: при тех же живых причинах те же задания снимаются "
                        + "в том же порядке, иначе журнал плывёт между пересборками");
    }

    private static Board boardWithCraterAndEat() {
        Board board = boardAt(0);
        board.submit(ticket("crater_1", BoardCase.FILL_CRATER, "crater:1:64:2"));
        board.submit(ticket("eat_1", BoardCase.EAT_TO_FULL, BoardTicket.ALWAYS_CAUSE));
        return board;
    }

    @Provide
    Arbitrary<Set<String>> causeSets() {
        return Arbitraries.of("", "crater:1:64:2", "fence:9:64:9")
                .set()
                .ofMinSize(0)
                .ofMaxSize(2);
    }
}
