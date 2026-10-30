package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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

    private static BoardTicket ticket(String id, BoardCase boardCase, long createdAt, List<String> dependsOn) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                createdAt,
                1,
                BoardTicket.ALWAYS_CAUSE,
                dependsOn,
                Map.of());
    }

    private static BoardTicket ticketWithResources(
            String id,
            BoardCase boardCase,
            long createdAt,
            List<String> dependsOn,
            Map<String, Integer> requiredItems) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                createdAt,
                1,
                BoardTicket.ALWAYS_CAUSE,
                dependsOn,
                requiredItems);
    }

    @Test
    void submit_DuplicateId_Rejected() {
        Board board = boardAt(0);
        BoardTicket first = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());
        BoardTicket second = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(first),
                "Первое задание обязано быть принято: доска хранит его в очереди");
        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(second),
                "Дубль id обязан отвергаться: два задания с одним id слили бы зависимости, "
                        + "выдачу и отчёт наблюдателя");
    }

    @Test
    void submit_DoneTicket_Replaced() {
        Board board = boardAt(0);
        board.submit(ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of()));
        board.claim("build_1", SPIRIT_ONE, 0);
        board.complete("build_1");

        BoardOutcome outcome = board.submit(ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of()));

        assertEquals(
                BoardOutcome.ACCEPTED,
                outcome,
                "Новый билет с тем же id заменяет выполненный: иначе переиздание шага (например, "
                        + "после отката мира) упиралось бы в DONE-тень и шаг не повторился бы никогда");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf("build_1").orElse(null),
                "Заменённый билет снова ждёт выдачи, а не остаётся DONE");
        assertTrue(
                board.ledger().has(BoardCase.BUILD_HOUSE, 1),
                "Отметка ledger переживает замену: условие «постройка стоит» не теряется, когда "
                        + "задание переиздано");
    }

    @Test
    void reopen_DoneFirstStep_ResubmitAcceptedAndMarkForgotten() {
        Board board = boardAt(0);
        board.submit(ticket("first:lighting", BoardCase.LIGHTING, 0, List.of()));
        board.claim("first:lighting", SPIRIT_ONE, 0);
        board.complete("first:lighting");

        boolean reopened = board.reopen("first:lighting");
        BoardOutcome outcome = board.submit(ticket("first:lighting", BoardCase.LIGHTING, 0, List.of()));

        assertTrue(reopened, "DONE шага раскладки, разошедшийся с миром, переоткрывается (INC-50)");
        assertFalse(
                board.ledger().has(BoardCase.LIGHTING, 1),
                "Отметка постройки снимается: иначе цепочка и этапы считают свет готовым при нуле факелов");
        assertEquals(
                BoardOutcome.ACCEPTED,
                outcome,
                "Переоткрытый шаг ставится заново, а не отвечает DUPLICATE навсегда (INC-50)");
    }

    @Test
    void occupy_ExternalTaskScript_ShownOnActivity() {
        Board board = boardAt(0);

        board.occupy(SPIRIT_ONE, BoardCase.ACTIVITY_EXTERNAL, "chop_tree", null, 0);

        assertEquals(
                List.of("script=chop_tree"),
                board.all().stream()
                        .filter(ticket -> ticket.id().equals(BoardActivities.idOf(SPIRIT_ONE)))
                        .findFirst()
                        .orElseThrow()
                        .modifiers(),
                "Задача духа через REST видна на доске занятием со своим скриптом, а не безымянной (INC-48)");
    }

    @Test
    void scriptOf_GotoThenChop_NamesWork() {
        assertEquals(
                "chop_tree",
                BoardActivities.scriptOf(new SequenceNode(
                        List.of(new ScriptCall("goto", Map.of()), new ScriptCall("chop_tree", Map.of())))),
                "Название задачи — работа, а не подход к месту: «goto» не показывается (INC-48)");
    }

    @Test
    void waitReason_ToolGateNote_ShownUntilClaimed() {
        Board board = boardAt(0);
        board.submit(ticket("clear:2", BoardCase.CLEAR_GROUNDS, 0, List.of()));
        board.noteWait("clear:2", "нет духа с киркой");

        assertEquals(
                Optional.of("нет духа с киркой"),
                BoardWhy.waitReason(board, board.find("clear:2").orElseThrow(), new StockView(Map.of()), 0),
                "Гейт по инструменту виден в waitReason доски (и на экране J): иначе расчистка ждёт молча (INC-67)");
        board.claim("clear:2", SPIRIT_ONE, 0);
        assertEquals(Optional.empty(), board.waitNote("clear:2"), "Выданное задание причину ожидания снимает");
    }

    @Test
    void submit_DoneFirstStep_NotReopened() {
        Board board = boardAt(0);
        board.submit(ticket("first:clear_grounds", BoardCase.CLEAR_GROUNDS, 0, List.of()));
        board.claim("first:clear_grounds", SPIRIT_ONE, 0);
        board.complete("first:clear_grounds");

        BoardOutcome outcome = board.submit(ticket("first:clear_grounds", BoardCase.CLEAR_GROUNDS, 0, List.of()));

        assertEquals(
                BoardOutcome.DUPLICATE,
                outcome,
                "Выполненный шаг первых шагов не переоткрывается: иначе DONE-тень снова становится "
                        + "PENDING и кейс зацикливается каждую пересборку");
        assertEquals(
                BoardStatus.DONE,
                board.statusOf("first:clear_grounds").orElse(null),
                "Сданный шаг остаётся DONE: факт «сделано» — условие цепочки, а не рабочая очередь");
        assertTrue(
                board.ledger().has(BoardCase.CLEAR_GROUNDS, 1),
                "Отметка ledger остаётся и после отказа переоткрыть шаг: по ней цепочка видит "
                        + "выполненный кейс после перезапуска");
    }

    @Test
    void fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        String id = "first:clear_grounds";
        for (int attempt = 1; attempt <= BoardTuning.ticketFailLimit(); attempt++) {
            UUID holder = new UUID(0, attempt);
            board.submit(ticket(id, BoardCase.CLEAR_GROUNDS, now[0], List.of()));
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(id, holder, now[0]),
                    "Попытка " + attempt + ": задание обязано выдаваться, иначе провал не к чему привязать");
            assertTrue(board.fail(id), "Попытка " + attempt + ": провал скрипта обязан дойти до доски");
            board.prune(now[0]);
            now[0] += Math.max(BoardTuning.failBackoffTicks(), board.casePauseTicks(holder, BoardCase.CLEAR_GROUNDS));
        }

        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(id).orElse(null),
                "После предела провалов подряд задание блокируется: вечный цикл «открыть → провалить» "
                        + "не должен повторяться каждую пересборку");
        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(ticket(id, BoardCase.CLEAR_GROUNDS, now[0], List.of())),
                "Заблокированное задание не переиздаётся с теми же условиями: иначе предел провалов "
                        + "ничего не ограничивает");
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(id).orElse(null),
                "Статус BLOCKED сохраняется: снимает его только изменение условий задания");
    }

    @Test
    void submit_OldTicket_Accepted() {
        Board board = boardAt(1_000_000);
        BoardTicket late = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(late),
                "У задания нет срока жизни: давняя постановка обязана попадать в очередь, "
                        + "актуальность проверяется причиной при пересборке, а не смертью по таймеру");
    }

    @Test
    void submit_NullTicket_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> boardAt(0).submit(null),
                "tasket=null обязан отвергаться: доска хранит задание, а не пустую заявку");
    }

    @Test
    void submit_RepairCase_AcceptedLate() {
        Board board = boardAt(1_000_000);
        BoardTicket repair = ticket("fence_1", BoardCase.REPAIR_FENCE, 0, List.of());

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(repair),
                "Ремонт ограждений живёт до выполнения: несделанный ремонт значил бы " + "открытые ворота навсегда");
    }

    @Test
    void release_AssignedTicket_ReturnsToQueueWithoutOwner() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());

        board.submit(build);
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("build_1", SPIRIT_ONE, 0),
                "Перед проверкой возврата задание обязано быть выдано первому духу");
        assertTrue(
                board.release("build_1"),
                "Выданное задание обязано возвращаться в очередь: погибший владелец не "
                        + "должен держать задание навсегда");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("build_1", SPIRIT_TWO, 0),
                "После возврата владелец сброшен: задание обязано быть выдано любому "
                        + "свободному духу, а не лежать в очереди с мёртвым владельцем");
    }

    @Test
    void release_UnassignedTicket_NotTouched() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());
        BoardTicket waiting = ticket("supply_1", BoardCase.SUPPLY_SITE, 0, List.of());

        board.submit(build);
        board.submit(waiting);
        assertFalse(board.release("build_1"), "Задание, которое никто не брал, возвращать нечего: оно уже в очереди");
        assertFalse(board.release("missing"), "Неизвестный id возвращать нечего: доска не плодит задания из отказов");
    }

    @Test
    void claimable_UnfinishedDependency_Excluded() {
        Board board = boardAt(0);
        BoardTicket capture = ticket("capture_1", BoardCase.CAPTURE_ANIMAL, 0, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, List.of("capture_1"));
        board.submit(capture);
        board.submit(lure);

        List<BoardTicket> ready = board.claimable(new StockView(Map.of()), 0);

        assertEquals(
                List.of(capture),
                ready,
                "«Приманить» не раньше «окружить»: зависимость не выполнена — задание не "
                        + "попадает в выдачу, иначе дух пришёл бы к пустому загону");
    }

    @Test
    void claimable_Complete_UnlocksChain() {
        Board board = boardAt(0);
        BoardTicket capture = ticket("capture_1", BoardCase.CAPTURE_ANIMAL, 0, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, List.of("capture_1"));
        board.submit(capture);
        board.submit(lure);

        assertEquals(
                List.of(capture),
                board.claimable(new StockView(Map.of()), 0),
                "Цепочка начинается с capture: он готов к выдаче");
        assertEquals(BoardOutcome.CLAIMED, board.claim("capture_1", SPIRIT_ONE, 0), "capture выдан первому духу");
        assertTrue(board.complete("capture_1"), "capture завершён");

        assertEquals(
                List.of(lure),
                board.claimable(new StockView(Map.of()), 0),
                "Завершение разблокирует зависящее: доска сама выдаёт ждущее задание по "
                        + "порядку, без второго вызова источника");
    }

    @Test
    void claimable_AfterPruneOfDone_RetainsDependency() {
        Board board = boardAt(0);
        BoardTicket capture = ticket("capture_1", BoardCase.CAPTURE_ANIMAL, 0, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, List.of("capture_1"));
        board.submit(capture);
        board.submit(lure);
        board.claim("capture_1", SPIRIT_ONE, 0);
        board.complete("capture_1");
        board.prune(1);

        assertEquals(
                List.of(lure),
                board.claimable(new StockView(Map.of()), 1),
                "Снятое пересборкой завершённое задание остаётся выполненным для зависимого: "
                        + "иначе «приманить» после «окружить» не выдалось бы никогда, и жизненный "
                        + "цикл животного рвался бы на первом же prune");
    }

    @Test
    void claimable_Chain_Unlocks() {
        Board board = boardAt(0);
        BoardTicket pen = ticket("pen_1", BoardCase.BUILD_PEN, 0, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, List.of("pen_1"));
        BoardTicket dismantle = ticket("dismantle_1", BoardCase.DISMANTLE_SHELTER, 0, List.of("lure_1"));
        board.submit(pen);
        board.submit(lure);
        board.submit(dismantle);

        assertEquals(
                List.of(pen),
                board.claimable(new StockView(Map.of()), 0),
                "Цепочка начинается с загона: он готов к выдаче");
        board.claim("pen_1", SPIRIT_ONE, 0);
        board.complete("pen_1");
        assertEquals(
                List.of(lure),
                board.claimable(new StockView(Map.of()), 0),
                "После загона — приманить: цепочка идёт строго по порядку");
        board.claim("lure_1", SPIRIT_ONE, 0);
        board.complete("lure_1");
        assertEquals(
                List.of(dismantle),
                board.claimable(new StockView(Map.of()), 0),
                "После приманки — разобрать укрытие: последнее звено цепочки");
    }

    @Test
    void claimable_Gating_HoldsInQueue() {
        Board board = boardAt(0);
        BoardTicket build = ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 0, List.of(), Map.of("dirt", 9));
        board.submit(build);

        List<BoardTicket> emptyStock = board.claimable(new StockView(Map.of()), 0);

        assertEquals(List.of(), emptyStock, "Гейт WAITING держит задание вне выдачи: дух не начнёт стройку без земли");
        assertEquals(
                Optional.of(BoardStatus.WAITING_RESOURCES),
                board.statusOf("build_1"),
                "Статус WAITING_RESOURCES виден наблюдателю: задание ждёт ресурс, а не исчезает");
    }

    @Test
    void claimable_StockArrived_ReleasesWaiting() {
        Board board = boardAt(0);
        BoardTicket build = ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 0, List.of(), Map.of("dirt", 9));
        board.submit(build);

        board.claimable(new StockView(Map.of()), 0);
        List<BoardTicket> ready = board.claimable(new StockView(Map.of("dirt", 9)), 0);

        assertEquals(
                List.of(build),
                ready,
                "С появлением ресурса доска сама выдаёт ждущее задание по приоритету, "
                        + "без повторного вызова источника");
    }

    @Test
    void claim_SameTicket_TakenByOther() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());
        board.submit(build);
        board.claimable(new StockView(Map.of()), 0);

        assertEquals(BoardOutcome.CLAIMED, board.claim("build_1", SPIRIT_ONE, 0), "Первый дух получает задание");
        assertEquals(
                BoardOutcome.TAKEN_BY_OTHER,
                board.claim("build_1", SPIRIT_TWO, 0),
                "Второй не получает чужое задание: один исполнитель, остальные прикрывают "
                        + "существующим механизмом координатора");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("build_1", SPIRIT_ONE, 0),
                "Повторный claim того же духа идемпотентен: адаптер не теряет задание при "
                        + "перевыдаче после пересборки");
    }

    @Test
    void claim_MissingResources_GateBlocked() {
        Board board = boardAt(0);
        BoardTicket build = ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 0, List.of(), Map.of("dirt", 9));
        board.submit(build);
        board.claimable(new StockView(Map.of()), 0);

        assertEquals(
                BoardOutcome.GATE_BLOCKED,
                board.claim("build_1", SPIRIT_ONE, 0),
                "Нехватка ресурсов на момент выдачи возвращает GATE_BLOCKED: задание "
                        + "остаётся WAITING_RESOURCES, доска ставит добычу недостающего");
    }

    @Test
    void claim_UnknownId_NotFound() {
        Board board = boardAt(0);

        assertEquals(
                BoardOutcome.NOT_FOUND,
                board.claim("ghost_1", SPIRIT_ONE, 0),
                "Неизвестный id обязан возвращать NOT_FOUND: адаптер отличает опечатку "
                        + "от выдачи и не выдумывает задание");
    }

    @Test
    void claim_NegativeNow_Rejected() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());
        board.submit(build);

        assertThrows(
                IllegalArgumentException.class,
                () -> board.claim("build_1", SPIRIT_ONE, -1),
                "Отрицательное время обязано отвергаться: оно сломало бы сортировку выдачи");
    }

    @Test
    void cancel_Dependants_Removed() {
        Board board = boardAt(0);
        BoardTicket pen = ticket("pen_1", BoardCase.BUILD_PEN, 0, List.of());
        BoardTicket lure = ticket("lure_1", BoardCase.LURE_ANIMAL, 0, List.of("pen_1"));
        BoardTicket dismantle = ticket("dismantle_1", BoardCase.DISMANTLE_SHELTER, 0, List.of("lure_1"));
        board.submit(pen);
        board.submit(lure);
        board.submit(dismantle);

        assertTrue(board.cancel("pen_1"), "Отмена pen снимает задание");

        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("pen_1"),
                "pen снят: жизненный цикл животного прерван");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("lure_1"),
                "Зависящее снимается вместе с предшественником: иначе «приманить» ждало "
                        + "бы отменённый загон навсегда");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("dismantle_1"),
                "Каскад отмены проходит всю цепочку: задания без предшественника не могут " + "появиться в выдаче");
    }

    @Test
    void cancel_UnknownId_Noop() {
        Board board = boardAt(0);

        assertFalse(
                board.cancel("ghost_1"),
                "Отмена неизвестного задания не делает ничего: ложная тревога каскадом не идёт");
    }

    @Test
    void prune_KeepsDone_RemovesFailedAndCancelled() {
        Board board = boardAt(0);
        board.submit(ticket("done_1", BoardCase.BUILD_HOUSE, 0, List.of()));
        board.submit(ticket("failed_1", BoardCase.SUPPLY_SITE, 0, List.of()));
        board.submit(ticket("cancelled_1", BoardCase.CLEAR_GROUNDS, 0, List.of()));
        board.submit(ticket("pending_1", BoardCase.LIGHTING, 0, List.of()));
        board.claim("done_1", SPIRIT_ONE, 0);
        board.complete("done_1");
        board.claim("failed_1", SPIRIT_ONE, 0);
        board.fail("failed_1");
        board.cancel("cancelled_1");

        int removed = board.prune(200);

        assertEquals(
                2,
                removed,
                "prune снимает FAILED и CANCELLED, но не DONE: выполненное задание — отметка "
                        + "для зависимых, а история провалов и отмен — в журнале, не в очереди");
        assertEquals(
                Optional.of(BoardStatus.DONE),
                board.statusOf("done_1"),
                "Завершённое задание остаётся на доске со статусом DONE: по нему другие задания "
                        + "видят условие «постройка стоит»");
        assertEquals(
                Optional.empty(),
                board.find("failed_1"),
                "Проваленное задание снято из очереди: иначе повторная постановка того же id "
                        + "давала бы DUPLICATE вечно и ретрай после отката не работал бы");
        assertEquals(Optional.empty(), board.find("cancelled_1"), "Отменённое задание снято из очереди");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("pending_1"),
                "Ждущее задание остаётся в очереди: prune не трогает работу");
    }

    @Test
    void prune_Oldest_Evicted() {
        Board board = boardAt(0);
        for (int i = 0; i < BoardTuning.MAX_PENDING + 1; i++) {
            board.submit(ticket("build_" + i, BoardCase.BUILD_HOUSE, i, List.of()));
        }

        int removed = board.prune(0);

        assertEquals(
                1,
                removed,
                "При превышении MAX_PENDING вытесняется самое старое невыданное: очередь "
                        + "поселения ограничена, иначе разрасталась бы неограниченно");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("build_0"),
                "Самое старое задание вытеснено: переполнение очереди не молча теряет "
                        + "постановку, а переводит её в отменённые");
    }

    @Test
    void pendingFor_EqualUuidOtherInstance_FindsTicket() {
        Board board = boardAt(0);
        BoardTicket mine = new BoardTicket(
                "build_1",
                BoardCase.BUILD_HOUSE,
                Optional.of(SPIRIT_ONE),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(mine);

        assertEquals(
                List.of(mine),
                board.pendingFor(UUID.fromString(SPIRIT_ONE.toString())),
                "Владелец задания сравнивается по значению UUID, а не по экземпляру: после перезагрузки доски "
                        + "и духа UUID — разные объекты с тем же значением, и дух терял бы свои задания");
    }

    @Test
    void pendingFor_Returns_SpiritTickets() {
        Board board = boardAt(0);
        BoardTicket mine = new BoardTicket(
                "build_1",
                BoardCase.BUILD_HOUSE,
                Optional.of(SPIRIT_ONE),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        BoardTicket other = new BoardTicket(
                "build_2",
                BoardCase.BUILD_HOUSE,
                Optional.of(SPIRIT_TWO),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(mine);
        board.submit(other);

        assertEquals(
                List.of(mine),
                board.pendingFor(SPIRIT_ONE),
                "pendingFor возвращает задания владельца: адаптер знает, чем дух занят");
        assertEquals(
                List.of(other),
                board.pendingFor(SPIRIT_TWO),
                "Задания другого духа не попадают в список: занятость считается по владельцу");
    }

    @Test
    void assignedTo_Returns_TicketClaimedBySpirit() {
        Board board = boardAt(0);
        BoardTicket supply = ticket("supply:dirt", BoardCase.SUPPLY_SITE, 0, List.of());
        board.submit(supply);
        board.claim("supply:dirt", SPIRIT_ONE, 0);

        assertEquals(
                List.of(supply),
                board.assignedTo(SPIRIT_ONE),
                "Задание без владельца в заявке, взятое духом, числится за ним: иначе провал "
                        + "духа не закрыл бы добычу, и она висела бы ASSIGNED до срока");
        assertEquals(
                List.of(),
                board.assignedTo(SPIRIT_TWO),
                "Чужое задание не числится за вторым духом: занятость считается по держателю");
    }

    @Test
    void assignedCounts_Counts_ClaimedTicket() {
        Board board = boardAt(0);
        BoardTicket supply = ticket("supply:dirt", BoardCase.SUPPLY_SITE, 0, List.of());
        board.submit(supply);
        board.claim("supply:dirt", SPIRIT_ONE, 0);

        assertEquals(
                Map.of(SPIRIT_ONE, 1),
                board.assignedCounts(),
                "Держатель задания виден в счётчике занятости: по нему доска решает, свободен ли дух");
    }

    @Test
    void holderOf_ReportsClaimHolder_ForObserver() {
        Board board = boardAt(0);
        board.submit(ticket("supply:dirt", BoardCase.SUPPLY_SITE, 0, List.of()));

        assertEquals(
                Optional.empty(),
                board.holderOf("supply:dirt"),
                "Задание без владельца и без выдачи не имеет держателя: наблюдатель видит «любой свободный»");

        board.claim("supply:dirt", SPIRIT_ONE, 0);
        assertEquals(
                Optional.of(SPIRIT_ONE),
                board.holderOf("supply:dirt"),
                "Держатель задания — тот, кто его взял: по нему наблюдатель REST показывает assignee");

        board.release("supply:dirt");
        assertEquals(
                Optional.empty(),
                board.holderOf("supply:dirt"),
                "Отпущенное задание снова без держателя: assignee пуст, задание ждёт любого свободного");

        assertEquals(
                Optional.empty(),
                board.holderOf("нет-такого"),
                "Неизвестный id не даёт держателя: наблюдатель не выдумывает духа");
    }

    @Test
    void holderOf_ReportsAssignee_FromTicket() {
        Board board = boardAt(0);
        BoardTicket addressed = new BoardTicket(
                "house_1",
                BoardCase.BUILD_HOUSE,
                Optional.of(SPIRIT_TWO),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(addressed);

        assertEquals(
                Optional.of(SPIRIT_TWO),
                board.holderOf("house_1"),
                "Адресное задание числится за владельцем из заявки ещё до выдачи: наблюдатель видит адресата");
    }

    @Test
    void all_Sorted_ByPriority() {
        Board board = boardAt(0);
        BoardTicket house = ticket("house_1", BoardCase.BUILD_HOUSE, 100, List.of());
        BoardTicket crater = ticket("crater_1", BoardCase.FILL_CRATER, 200, List.of());
        BoardTicket fence = ticket("fence_1", BoardCase.REPAIR_FENCE, 300, List.of());
        board.submit(house);
        board.submit(crater);
        board.submit(fence);

        List<BoardTicket> all = board.all();

        assertEquals(
                List.of(fence, house, crater),
                all,
                "Сортировка v2: приоритет убывает (ремонт 100, стройка 50, воронка 35), "
                        + "при равенстве — раньше созданный; доска выдаёт первым самое важное, "
                        + "а не первое поступившее");
    }

    @Test
    void all_Is_Deterministic() {
        Board board = boardAt(0);
        board.submit(ticket("house_1", BoardCase.BUILD_HOUSE, 100, List.of()));
        board.submit(ticket("fence_1", BoardCase.REPAIR_FENCE, 300, List.of()));

        assertEquals(
                board.all(),
                board.all(),
                "Порядок обхода детерминирован: по нему доска выдаёт задания и наблюдатель "
                        + "сравнивает снимки между пересборками");
    }

    @Property
    void claimable_Order_Deterministic(
            @ForAll("boardTickets") BoardTicket first,
            @ForAll("boardTickets") BoardTicket second,
            @ForAll("stockViews") StockView stock) {
        if (first.id().equals(second.id())) {
            return;
        }
        Board board = boardAt(0);
        board.submit(first);
        board.submit(second);

        List<BoardTicket> firstRun = board.claimable(stock, 0);
        List<BoardTicket> secondRun = board.claimable(stock, 0);

        assertEquals(
                firstRun,
                secondRun,
                "Порядок выдачи обязан быть детерминирован: иначе два духа при одинаковой "
                        + "очереди получили бы разные задания при одинаковом входе");
    }

    @Property
    void claimable_UnfinishedDependency_NeverIssued(
            @ForAll("boardTickets") BoardTicket first,
            @ForAll("boardTickets") BoardTicket second,
            @ForAll("stockViews") StockView stock) {
        if (second.dependsOn().contains(first.id())) {
            Board board = boardAt(0);
            board.submit(first);
            board.submit(second);

            List<BoardTicket> ready = board.claimable(stock, 0);

            assertFalse(
                    ready.contains(second),
                    "Задание с невыполненной зависимостью никогда не выдаётся: доска не "
                            + "обходит цепочку порядка, даже если ресурсы уже есть");
        }
    }

    @Property
    void prune_AfterRun_KeepsPending(@ForAll("boardTickets") BoardTicket ticket) {
        Board board = boardAt(1_000_000);
        board.submit(ticket);

        board.prune(1_000_000);

        assertTrue(
                board.find(ticket.id()).isPresent(),
                "После prune ждущее задание на месте: без срока жизни пересборка не снимает "
                        + "задания, снятие — только по актуальности причины");
    }

    @Test
    void complete_DoneTicket_StaysVisibleInAll() {
        Board board = boardAt(0);
        board.submit(ticket("mine_1", BoardCase.BUILD_MINE, 0, List.of()));
        board.claim("mine_1", SPIRIT_ONE, 0);
        board.complete("mine_1");

        assertEquals(
                Optional.of(BoardStatus.DONE),
                board.statusOf("mine_1"),
                "Завершённое задание читается со статусом DONE");
        assertTrue(
                board.all().stream().anyMatch(ticket -> "mine_1".equals(ticket.id())),
                "Завершённое задание видно в all(): DONE — отметка выполнения на доске, " + "а не удалённая строка");
        assertTrue(
                board.ledger().has(BoardCase.BUILD_MINE, 1),
                "Завершение пишет отметку в ledger: условие «уровень 1 шахты готов» переживает "
                        + "даже снятие билета и перезапуск через журнал");
    }

    @Test
    void claimable_BuildingDependency_UnlockedByLevelDone() {
        Board board = boardAt(0);
        BoardTicket levelOne = new BoardTicket(
                "mine_1",
                BoardCase.BUILD_MINE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_MINE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        BoardTicket levelTwo = new BoardTicket(
                "mine_2",
                BoardCase.LIGHTING,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.LIGHTING),
                1,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of("building:BUILD_MINE:1"),
                Map.of());
        board.submit(levelOne);
        board.submit(levelTwo);

        assertEquals(
                List.of(),
                board.claimable(new StockView(Map.of()), 0).stream()
                        .filter(ticket -> "mine_2".equals(ticket.id()))
                        .toList(),
                "Уровень 2 не выдаётся раньше уровня 1: уровень не прыгает");
        board.claim("mine_1", SPIRIT_ONE, 0);
        board.complete("mine_1");

        assertEquals(
                List.of(levelTwo),
                board.claimable(new StockView(Map.of()), 0).stream()
                        .filter(ticket -> "mine_2".equals(ticket.id()))
                        .toList(),
                "Зависимое от building:BUILD_MINE:1 выдаётся после выполнения уровня 1: условие "
                        + "считается по живому DONE-заданию");
    }

    @Test
    void claimable_BuildingDependency_LedgerNoteSatisfies() {
        Board board = boardAt(0);
        BoardTicket levelTwo = new BoardTicket(
                "mine_2",
                BoardCase.LIGHTING,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.LIGHTING),
                1,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of("building:BUILD_MINE:1"),
                Map.of());
        board.submit(levelTwo);
        board.ledger().note(BoardCase.BUILD_MINE, 1);

        assertEquals(
                List.of(levelTwo),
                board.claimable(new StockView(Map.of()), 0),
                "Отметка ledger закрывает условие без живого билета: так восстанавливается "
                        + "«выполнено» прошлой сессии из журнала при старте");
    }

    @Test
    void evictOverflow_DonePresent_SparesDone() {
        Board board = boardAt(0);
        board.submit(ticket("done_1", BoardCase.BUILD_HOUSE, 0, List.of()));
        board.claim("done_1", SPIRIT_ONE, 0);
        board.complete("done_1");
        for (int index = 0; index < BoardTuning.MAX_PENDING + 1; index++) {
            board.submit(ticket("build_" + index, BoardCase.BUILD_HOUSE, index, List.of()));
        }

        int removed = board.prune(0);

        assertEquals(
                1,
                removed,
                "Вытеснение снимает только самое старое невыданное: DONE не участвует в подсчёте " + "переполнения");
        assertEquals(
                Optional.of(BoardStatus.DONE),
                board.statusOf("done_1"),
                "Вытеснение переполнения не трогает DONE: выполненная постройка остаётся условием");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("build_0"),
                "Вытеснено самое старое невыданное задание");
    }

    @Property
    void all_AfterThousandOps_IsDeterministic(@ForAll long seed) {
        assertEquals(
                runOps(seed),
                runOps(seed),
                "После 1000 submit/complete/cancel очередь детерминирована: при том же входе та же "
                        + "очередь, иначе пересборки плывут и журнал невоспроизводим");
    }

    private static List<BoardTicket> runOps(long seed) {
        Random random = new Random(seed);
        Board board = boardAt(0);
        List<String> known = new ArrayList<>();
        StockView stock = new StockView(Map.of());
        for (int step = 0; step < 1000; step++) {
            int op = random.nextInt(6);
            String id = "op_" + random.nextInt(300);
            switch (op) {
                case 0 -> {
                    BoardCase boardCase = List.of(BoardCase.values()).get(random.nextInt(BoardCase.values().length));
                    board.submit(new BoardTicket(
                            id,
                            boardCase,
                            Optional.empty(),
                            BoardRules.priorityOf(boardCase),
                            step,
                            1 + random.nextInt(3),
                            random.nextBoolean() ? BoardTicket.ALWAYS_CAUSE : "crater:1:64:2",
                            List.of(),
                            Map.of()));
                    known.add(id);
                }
                case 1 -> board.claim(id, SPIRIT_ONE, 0);
                case 2 -> board.complete(id);
                case 3 -> board.fail(id);
                case 4 -> board.cancel(id);
                default -> {
                    board.claimable(stock, 0);
                    board.prune(0);
                }
            }
            if (!known.isEmpty() && random.nextInt(10) == 0) {
                String victim = known.get(random.nextInt(known.size()));
                board.cancel(victim);
            }
        }
        return board.all();
    }

    @Provide
    Arbitrary<BoardTicket> boardTickets() {
        return Arbitraries.of(
                ticket("capture_1", BoardCase.CAPTURE_ANIMAL, 0, List.of()),
                ticket("pen_1", BoardCase.BUILD_PEN, 10, List.of("capture_1")),
                ticket("lure_1", BoardCase.LURE_ANIMAL, 20, List.of("pen_1")),
                ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 30, List.of(), Map.of("dirt", 9)),
                ticket("fence_1", BoardCase.REPAIR_FENCE, 40, List.of()));
    }

    @Provide
    Arbitrary<StockView> stockViews() {
        return Arbitraries.of(new StockView(Map.of()), new StockView(Map.of("dirt", 9)));
    }

    @Test
    void submit_StonePartDone_Duplicate() {
        Board board = new Board(() -> 0);
        BoardTicket axe = simpleTicket("stone:craft_stone_axe", BoardCase.CRAFT_STONE_AXE);
        board.submit(axe);
        board.claim(axe.id(), SPIRIT_ONE, 0);
        board.complete(axe.id());

        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(axe),
                "Каменный шаг-помощник одноразовый, как «first:»: переподача сделанного крафтила бы топор заново");
    }

    @Test
    void resetForNewRules_StonePartWaiting_Removed() {
        Board board = new Board(() -> 0);
        BoardTicket axe = simpleTicket("stone:craft_stone_axe", BoardCase.CRAFT_STONE_AXE);
        board.submit(axe);

        assertTrue(
                board.resetForNewRules().contains(axe.id()),
                "Ожидающий каменный шаг — задание источника: при смене сборки сбрасывается (INC-39)");
    }

    @Test
    void resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        BoardTicket gather = simpleTicket("gather:oak_log", BoardCase.GATHER_MATERIALS);
        BoardTicket blocked = simpleTicket("first:craft_furnace", BoardCase.CRAFT_FURNACE);
        BoardTicket done = simpleTicket("first:crafting_station", BoardCase.CRAFTING_STATION);
        BoardTicket rest = simpleTicket("rest:keep", BoardCase.LIGHTING);
        BoardTicket assigned = simpleTicket("gather:oak_fence", BoardCase.GATHER_MATERIALS);
        board.submit(gather);
        board.submit(done);
        board.submit(rest);
        board.submit(assigned);
        board.claim(assigned.id(), new UUID(7, 7), 0);
        board.claim(done.id(), SPIRIT_ONE, 0);
        board.complete(done.id());
        for (int attempt = 1; attempt <= BoardTuning.ticketFailLimit(); attempt++) {
            UUID holder = new UUID(0, attempt);
            board.submit(blocked);
            board.claim(blocked.id(), holder, now[0]);
            board.fail(blocked.id());
            board.prune(now[0]);
            now[0] += Math.max(BoardTuning.failBackoffTicks(), board.casePauseTicks(holder, BoardCase.CRAFT_FURNACE));
        }

        assertTrue(board.blocked(blocked.id()), "Сцена: шаг первых шагов заблокирован провалами подряд");
        List<String> removed = board.resetForNewRules();

        assertTrue(
                removed.contains("gather:oak_log")
                        && board.find("gather:oak_log").isEmpty(),
                "Ожидающее задание источника старых правил сброшено: источник поставит его по новым (INC-36)");
        assertTrue(
                removed.contains("gather:oak_fence")
                        && board.find("gather:oak_fence").isEmpty(),
                "Выданное задание источника старых правил тоже сброшено: иначе живёт после выкладки (INC-39)");
        assertTrue(board.find(done.id()).isPresent(), "Сделанное остаётся: журнал этапов не теряется");
        assertTrue(board.find(rest.id()).isPresent(), "Задание REST/игрока не сбрасывается");
        assertTrue(
                !board.blocked(blocked.id()),
                "Заблокированное по старым правилам больше не держит цепочку: " + board.statusOf(blocked.id()));
    }

    @Test
    void occupy_ActivityChangeAndVacate_Journaled() {
        List<String> events = new ArrayList<>();
        Board board =
                new Board(() -> 0L, (event, ticket, holder, tick) -> events.add(event + ":" + ticket.boardCase()));

        board.occupy(SPIRIT_ONE, BoardCase.ACTIVITY_COMBAT, 0);
        board.occupy(SPIRIT_ONE, BoardCase.ACTIVITY_COMBAT, 0);
        board.vacate(SPIRIT_ONE);

        assertEquals(
                List.of("ASSIGNED:ACTIVITY_COMBAT", "CANCELLED:ACTIVITY_COMBAT"),
                events,
                "Бой и стража видны в журнале доски: постановка и снятие занятия (INC-35), без повторов");
    }

    private static BoardTicket simpleTicket(String id, BoardCase boardCase) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
