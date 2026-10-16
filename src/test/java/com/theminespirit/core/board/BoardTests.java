package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardTests {

    private static final UUID SPIRIT_ONE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SPIRIT_TWO = UUID.fromString("66666666-7777-8888-9999-000000000000");

    private static Board boardAt(long now) {
        return new Board(() -> now);
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, long createdAt, long ttlTicks,
            List<String> dependsOn) {
        return new BoardTicket(id, boardCase, Optional.empty(), BoardRules.priorityOf(boardCase), createdAt,
                ttlTicks, dependsOn, Map.of());
    }

    private static BoardTicket ticketWithResources(String id, BoardCase boardCase, long createdAt,
            long ttlTicks, List<String> dependsOn, Map<String, Integer> requiredItems) {
        return new BoardTicket(id, boardCase, Optional.empty(), BoardRules.priorityOf(boardCase), createdAt,
                ttlTicks, dependsOn, requiredItems);
    }

    @Test
    void submit_DuplicateId_Rejected() {
        Board board = boardAt(0);
        BoardTicket first = ticket("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of());
        BoardTicket second = ticket("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of());

        assertEquals(BoardOutcome.ACCEPTED, board.submit(first),
                "Первое задание обязано быть принято: доска хранит его в очереди");
        assertEquals(BoardOutcome.DUPLICATE, board.submit(second),
                "Дубль id обязан отвергаться: два задания с одним id слили бы зависимости, "
                        + "выдачу и отчёт наблюдателя");
    }

    @Test
    void submit_ExpiredTicket_Rejected() {
        Board board = boardAt(200);
        BoardTicket expired = ticket("build_1", BoardCase.BUILD_HOUSE, 0, 100, List.of());

        assertEquals(BoardOutcome.EXPIRED, board.submit(expired),
                "Просроченное задание обязано не попадать в очередь: дух получил бы "
                        + "задание, срок которого истёк до выдачи");
    }

    @Test
    void submit_NullTicket_Rejected() {
        assertThrows(NullPointerException.class, () -> boardAt(0).submit(null),
                "tasket=null обязан отвергаться: доска хранит задание, а не пустую заявку");
    }

    @Test
    void submit_RepairCase_HasNoTtl() {
        Board board = boardAt(1_000_000);
        BoardTicket repair = ticket("fence_1", BoardCase.REPAIR_FENCE, 0, 6000, List.of());

        assertEquals(BoardOutcome.ACCEPTED, board.submit(repair),
                "Ремонт ограждений живёт до выполнения без срока (поправка 8): просроченный "
                        + "ремонт значил бы открытые ворота навсегда");
    }

    @Test
    void release_AssignedTicket_ReturnsToQueueWithoutOwner() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of());

        board.submit(build);
        assertEquals(BoardOutcome.CLAIMED, board.claim("build_1", SPIRIT_ONE, 0),
                "Перед проверкой возврата задание обязано быть выдано первому духу");
        assertTrue(board.release("build_1"),
                "Выданное задание обязано возвращаться в очередь: погибший владелец не "
                        + "должен держать задание навсегда");
        assertEquals(BoardOutcome.CLAIMED, board.claim("build_1", SPIRIT_TWO, 0),
                "После возврата владелец сброшен: задание обязано быть выдано любому "
                        + "свободному духу, а не лежать в очереди с мёртвым владельцем");
    }

    @Test
    void release_UnassignedTicket_NotTouched() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of());
        BoardTicket waiting = ticket("supply_1", BoardCase.SUPPLY_SITE, 0, 6000, List.of());

        board.submit(build);
        board.submit(waiting);
        assertFalse(board.release("build_1"),
                "Задание, которое никто не брал, возвращать нечего: оно уже в очереди");
        assertFalse(board.release("missing"),
                "Неизвестный id возвращать нечего: доска не плодит задания из отказов");
    }

    @Test
    void claimable_UnfinishedDependency_Excluded() {
        Board board = boardAt(0);
        BoardTicket capture = ticket("capture_1", BoardCase.CAPTURE_ANIMAL, 0, 6000, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, 6000, List.of("capture_1"));
        board.submit(capture);
        board.submit(lure);

        List<BoardTicket> ready = board.claimable(new StockView(Map.of()), 0);

        assertEquals(List.of(capture), ready,
                "«Приманить» не раньше «окружить»: зависимость не выполнена — задание не "
                        + "попадает в выдачу, иначе дух пришёл бы к пустому загону");
    }

    @Test
    void claimable_Complete_UnlocksChain() {
        Board board = boardAt(0);
        BoardTicket capture = ticket("capture_1", BoardCase.CAPTURE_ANIMAL, 0, 6000, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, 6000, List.of("capture_1"));
        board.submit(capture);
        board.submit(lure);

        assertEquals(List.of(capture), board.claimable(new StockView(Map.of()), 0),
                "Цепочка начинается с capture: он готов к выдаче");
        assertEquals(BoardOutcome.CLAIMED, board.claim("capture_1", SPIRIT_ONE, 0),
                "capture выдан первому духу");
        assertTrue(board.complete("capture_1"), "capture завершён");

        assertEquals(List.of(lure), board.claimable(new StockView(Map.of()), 0),
                "Завершение разблокирует зависящее: доска сама выдаёт ждущее задание по "
                        + "порядку, без второго вызова источника");
    }

    @Test
    void claimable_Chain_Unlocks() {
        Board board = boardAt(0);
        BoardTicket pen = ticket("pen_1", BoardCase.BUILD_PEN, 0, 6000, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, 6000, List.of("pen_1"));
        BoardTicket dismantle = ticket("dismantle_1", BoardCase.DISMANTLE_SHELTER, 0, 6000,
                List.of("lure_1"));
        board.submit(pen);
        board.submit(lure);
        board.submit(dismantle);

        assertEquals(List.of(pen), board.claimable(new StockView(Map.of()), 0),
                "Цепочка начинается с загона: он готов к выдаче");
        board.claim("pen_1", SPIRIT_ONE, 0);
        board.complete("pen_1");
        assertEquals(List.of(lure), board.claimable(new StockView(Map.of()), 0),
                "После загона — приманить: цепочка идёт строго по порядку");
        board.claim("lure_1", SPIRIT_ONE, 0);
        board.complete("lure_1");
        assertEquals(List.of(dismantle), board.claimable(new StockView(Map.of()), 0),
                "После приманки — разобрать укрытие: последнее звено цепочки");
    }

    @Test
    void claimable_Gating_HoldsInQueue() {
        Board board = boardAt(0);
        BoardTicket build = ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of(),
                Map.of("dirt", 9));
        board.submit(build);

        List<BoardTicket> emptyStock = board.claimable(new StockView(Map.of()), 0);

        assertEquals(List.of(), emptyStock,
                "Гейт WAITING держит задание вне выдачи: дух не начнёт стройку без земли");
        assertEquals(Optional.of(BoardStatus.WAITING_RESOURCES), board.statusOf("build_1"),
                "Статус WAITING_RESOURCES виден наблюдателю: задание ждёт ресурс, а не исчезает");
    }

    @Test
    void claimable_StockArrived_ReleasesWaiting() {
        Board board = boardAt(0);
        BoardTicket build = ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of(),
                Map.of("dirt", 9));
        board.submit(build);

        board.claimable(new StockView(Map.of()), 0);
        List<BoardTicket> ready = board.claimable(new StockView(Map.of("dirt", 9)), 0);

        assertEquals(List.of(build), ready,
                "С появлением ресурса доска сама выдаёт ждущее задание по приоритету, "
                        + "без повторного вызова источника");
    }

    @Test
    void claim_SameTicket_TakenByOther() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of());
        board.submit(build);
        board.claimable(new StockView(Map.of()), 0);

        assertEquals(BoardOutcome.CLAIMED, board.claim("build_1", SPIRIT_ONE, 0),
                "Первый дух получает задание");
        assertEquals(BoardOutcome.TAKEN_BY_OTHER, board.claim("build_1", SPIRIT_TWO, 0),
                "Второй не получает чужое задание: один исполнитель, остальные прикрывают "
                        + "существующим механизмом координатора");
        assertEquals(BoardOutcome.CLAIMED, board.claim("build_1", SPIRIT_ONE, 0),
                "Повторный claim того же духа идемпотентен: адаптер не теряет задание при "
                        + "перевыдаче после пересборки");
    }

    @Test
    void claim_MissingResources_GateBlocked() {
        Board board = boardAt(0);
        BoardTicket build = ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of(),
                Map.of("dirt", 9));
        board.submit(build);
        board.claimable(new StockView(Map.of()), 0);

        assertEquals(BoardOutcome.GATE_BLOCKED, board.claim("build_1", SPIRIT_ONE, 0),
                "Нехватка ресурсов на момент выдачи возвращает GATE_BLOCKED: задание "
                        + "остаётся WAITING_RESOURCES, доска ставит добычу недостающего");
    }

    @Test
    void claim_UnknownId_NotFound() {
        Board board = boardAt(0);

        assertEquals(BoardOutcome.NOT_FOUND, board.claim("ghost_1", SPIRIT_ONE, 0),
                "Неизвестный id обязан возвращать NOT_FOUND: адаптер отличает опечатку "
                        + "от выдачи и не выдумывает задание");
    }

    @Test
    void claim_NegativeNow_Rejected() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, 6000, List.of());
        board.submit(build);

        assertThrows(IllegalArgumentException.class, () -> board.claim("build_1", SPIRIT_ONE, -1),
                "Отрицательное время обязано отвергаться: оно сломало бы просрочку и сортировку");
    }

    @Test
    void cancel_Dependants_Removed() {
        Board board = boardAt(0);
        BoardTicket pen = ticket("pen_1", BoardCase.BUILD_PEN, 0, 6000, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, 6000, List.of("pen_1"));
        BoardTicket dismantle = ticket("dismantle_1", BoardCase.DISMANTLE_SHELTER, 0, 6000,
                List.of("lure_1"));
        board.submit(pen);
        board.submit(lure);
        board.submit(dismantle);

        assertTrue(board.cancel("pen_1"), "Отмена pen снимает задание");

        assertEquals(Optional.of(BoardStatus.CANCELLED), board.statusOf("pen_1"),
                "pen снят: жизненный цикл животного прерван");
        assertEquals(Optional.of(BoardStatus.CANCELLED), board.statusOf("lure_1"),
                "Зависящее снимается вместе с предшественником: иначе «приманить» ждало "
                        + "бы отменённый загон навсегда");
        assertEquals(Optional.of(BoardStatus.CANCELLED), board.statusOf("dismantle_1"),
                "Каскад отмены проходит всю цепочку: задания без предшественника не могут "
                        + "появиться в выдаче");
    }

    @Test
    void cancel_UnknownId_Noop() {
        Board board = boardAt(0);

        assertFalse(board.cancel("ghost_1"),
                "Отмена неизвестного задания не делает ничего: ложная тревога каскадом не идёт");
    }

    @Test
    void prune_Removes_OnlyExpired() {
        Board board = boardAt(0);
        BoardTicket expired = ticket("old_1", BoardCase.BUILD_HOUSE, 0, 100, List.of());
        BoardTicket fresh = ticket("new_1", BoardCase.BUILD_HOUSE, 100, 1000, List.of());
        board.submit(expired);
        board.submit(fresh);

        int removed = board.prune(200);

        assertEquals(1, removed,
                "prune снимает только просроченные: свежее задание живёт дальше, иначе "
                        + "пересборка выкидывала бы работу при каждом тике");
        assertEquals(Optional.empty(), board.find("old_1"),
                "Просроченное задание удалено из очереди");
        assertEquals(Optional.of(fresh), board.find("new_1"),
                "Свежее задание осталось в очереди");
    }

    @Test
    void prune_Oldest_Evicted() {
        Board board = boardAt(0);
        for (int i = 0; i < BoardTuning.MAX_PENDING + 1; i++) {
            board.submit(ticket("build_" + i, BoardCase.BUILD_HOUSE, i, 6000, List.of()));
        }

        int removed = board.prune(0);

        assertEquals(1, removed,
                "При превышении MAX_PENDING вытесняется самое старое невыданное: очередь "
                        + "поселения ограничена, иначе разрасталась бы неограниченно");
        assertEquals(Optional.of(BoardStatus.CANCELLED), board.statusOf("build_0"),
                "Самое старое задание вытеснено: переполнение очереди не молча теряет "
                        + "постановку, а переводит её в отменённые");
    }

    @Test
    void pendingFor_Returns_SpiritTickets() {
        Board board = boardAt(0);
        BoardTicket mine = new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.of(SPIRIT_ONE),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE), 0, 6000, List.of(), Map.of());
        BoardTicket other = new BoardTicket("build_2", BoardCase.BUILD_HOUSE, Optional.of(SPIRIT_TWO),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE), 0, 6000, List.of(), Map.of());
        board.submit(mine);
        board.submit(other);

        assertEquals(List.of(mine), board.pendingFor(SPIRIT_ONE),
                "pendingFor возвращает задания владельца: адаптер знает, чем дух занят");
        assertEquals(List.of(other), board.pendingFor(SPIRIT_TWO),
                "Задания другого духа не попадают в список: занятость считается по владельцу");
    }

    @Test
    void assignedTo_Returns_TicketClaimedBySpirit() {
        Board board = boardAt(0);
        BoardTicket supply = ticket("supply:dirt", BoardCase.SUPPLY_SITE, 0, 6000, List.of());
        board.submit(supply);
        board.claim("supply:dirt", SPIRIT_ONE, 0);

        assertEquals(List.of(supply), board.assignedTo(SPIRIT_ONE),
                "Задание без владельца в заявке, взятое духом, числится за ним: иначе провал "
                        + "духа не закрыл бы добычу, и она висела бы ASSIGNED до срока");
        assertEquals(List.of(), board.assignedTo(SPIRIT_TWO),
                "Чужое задание не числится за вторым духом: занятость считается по держателю");
    }

    @Test
    void assignedCounts_Counts_ClaimedTicket() {
        Board board = boardAt(0);
        BoardTicket supply = ticket("supply:dirt", BoardCase.SUPPLY_SITE, 0, 6000, List.of());
        board.submit(supply);
        board.claim("supply:dirt", SPIRIT_ONE, 0);

        assertEquals(Map.of(SPIRIT_ONE, 1), board.assignedCounts(),
                "Держатель задания виден в счётчике занятости: по нему доска решает, свободен ли дух");
    }

    @Test
    void all_Sorted_ByPriority() {
        Board board = boardAt(0);
        BoardTicket house = ticket("house_1", BoardCase.BUILD_HOUSE, 100, 6000, List.of());
        BoardTicket crater = ticket("crater_1", BoardCase.FILL_CRATER, 200, 6000, List.of());
        BoardTicket fence = ticket("fence_1", BoardCase.REPAIR_FENCE, 300, 6000, List.of());
        board.submit(house);
        board.submit(crater);
        board.submit(fence);

        List<BoardTicket> all = board.all();

        assertEquals(List.of(fence, crater, house), all,
                "Сортировка: приоритет убывает, при равенстве — раньше созданный; доска "
                        + "выдаёт первым самое важное, а не первое поступившее");
    }

    @Test
    void all_Is_Deterministic() {
        Board board = boardAt(0);
        board.submit(ticket("house_1", BoardCase.BUILD_HOUSE, 100, 6000, List.of()));
        board.submit(ticket("fence_1", BoardCase.REPAIR_FENCE, 300, 6000, List.of()));

        assertEquals(board.all(), board.all(),
                "Порядок обхода детерминирован: по нему доска выдаёт задания и наблюдатель "
                        + "сравнивает снимки между пересборками");
    }

    @Property
    void claimable_Order_Deterministic(@ForAll("boardTickets") BoardTicket first,
            @ForAll("boardTickets") BoardTicket second, @ForAll("stockViews") StockView stock) {
        if (first.id().equals(second.id())) {
            return;
        }
        Board board = boardAt(0);
        board.submit(first);
        board.submit(second);

        List<BoardTicket> firstRun = board.claimable(stock, 0);
        List<BoardTicket> secondRun = board.claimable(stock, 0);

        assertEquals(firstRun, secondRun,
                "Порядок выдачи обязан быть детерминирован: иначе два духа при одинаковой "
                        + "очереди получили бы разные задания при одинаковом входе");
    }

    @Property
    void claimable_UnfinishedDependency_NeverIssued(@ForAll("boardTickets") BoardTicket first,
            @ForAll("boardTickets") BoardTicket second, @ForAll("stockViews") StockView stock) {
        if (second.dependsOn().contains(first.id())) {
            Board board = boardAt(0);
            board.submit(first);
            board.submit(second);

            List<BoardTicket> ready = board.claimable(stock, 0);

            assertFalse(ready.contains(second),
                    "Задание с невыполненной зависимостью никогда не выдаётся: доска не "
                            + "обходит цепочку порядка, даже если ресурсы уже есть");
        }
    }

    @Property
    void prune_AfterRun_LeavesNoExpired(@ForAll("boardTickets") BoardTicket ticket) {
        Board board = boardAt(1_000_000);
        board.submit(ticket);

        board.prune(1_000_000);

        assertTrue(board.claimable(new StockView(Map.of()), 1_000_000).stream()
                        .noneMatch(remaining -> remaining.id().equals(ticket.id())
                                && expiredInPast(remaining, 1_000_000)),
                "После prune просроченных нет: пересборка не оставляет задания с истёкшим "
                        + "сроком, которые дух взял бы впустую");
    }

    private boolean expiredInPast(BoardTicket ticket, long now) {
        return !BoardTuning.hasNoTtl(ticket.boardCase())
                && ticket.createdAt() + ticket.ttlTicks() <= now;
    }

    @Provide
    Arbitrary<BoardTicket> boardTickets() {
        return Arbitraries.of(
                ticket("capture_1", BoardCase.CAPTURE_ANIMAL, 0, 6000, List.of()),
                ticket("pen_1", BoardCase.BUILD_PEN, 10, 6000, List.of("capture_1")),
                ticket("lure_1", BoardCase.LURE_ANIMAL, 20, 6000, List.of("pen_1")),
                ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 30, 6000, List.of(),
                        Map.of("dirt", 9)),
                ticket("fence_1", BoardCase.REPAIR_FENCE, 40, 6000, List.of()));
    }

    @Provide
    Arbitrary<StockView> stockViews() {
        return Arbitraries.of(
                new StockView(Map.of()),
                new StockView(Map.of("dirt", 9)));
    }
}
