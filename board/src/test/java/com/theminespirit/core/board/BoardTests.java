package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
                "Первое задание обязано быть принято: доска хранит его в очереди Иначе тест submit_DuplicateId_Rejected перестанет ловить эту поломку. При смене submit_DuplicateId_Rejected проверь это утверждение.");
        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(second),
                "Дубль id обязан отвергаться: два задания с одним id слили бы зависимости, "
                        + "выдачу и отчёт наблюдателя Иначе тест submit_DuplicateId_Rejected перестанет ловить эту поломку. При смене submit_DuplicateId_Rejected проверь это утверждение.");
    }

    @Test
    void reopen_DoneFirstStep_ResubmitAcceptedAndMarkForgotten() {
        Board board = boardAt(0);
        board.submit(ticket("first:lighting", BoardCase.LIGHTING, 0, List.of()));
        board.claim("first:lighting", SPIRIT_ONE, 0);
        board.complete("first:lighting");

        boolean reopened = board.reopen("first:lighting");
        BoardOutcome outcome = board.submit(ticket("first:lighting", BoardCase.LIGHTING, 0, List.of()));

        assertTrue(
                reopened,
                "DONE шага раскладки, разошедшийся с миром, переоткрывается (INC-50) Иначе тест reopen_DoneFirstStep_ResubmitAcceptedAndMarkForgotten перестанет ловить эту поломку. При смене reopen_DoneFirstStep_ResubmitAcceptedAndMarkForgotten проверь это утверждение.");
        assertFalse(
                board.ledger().has(BoardCase.LIGHTING, 1),
                "Отметка постройки снимается: иначе цепочка и этапы считают свет готовым при нуле факелов При смене reopen_DoneFirstStep_ResubmitAcceptedAndMarkForgotten проверь это утверждение.");
        assertEquals(
                BoardOutcome.ACCEPTED,
                outcome,
                "Переоткрытый шаг ставится заново, а не отвечает DUPLICATE навсегда (INC-50) Иначе тест reopen_DoneFirstStep_ResubmitAcceptedAndMarkForgotten перестанет ловить эту поломку. При смене reopen_DoneFirstStep_ResubmitAcceptedAndMarkForgotten проверь это утверждение.");
    }

    @Test
    void scriptOf_GotoThenChop_NamesWork() {
        assertEquals(
                "chop_tree",
                BoardActivities.scriptOf(new SequenceNode(
                        List.of(new ScriptCall("goto", Map.of()), new ScriptCall("chop_tree", Map.of())))),
                "Название задачи — работа, а не подход к месту: «goto» не показывается (INC-48) Иначе тест scriptOf_GotoThenChop_NamesWork перестанет ловить эту поломку. При смене scriptOf_GotoThenChop_NamesWork проверь это утверждение.");
    }

    @Test
    void waitReason_ToolGateNote_ShownUntilClaimed() {
        Board board = boardAt(0);
        board.submit(ticket("clear:2", BoardCase.CLEAR_GROUNDS, 0, List.of()));
        board.noteWait("clear:2", "нет духа с киркой");

        assertEquals(
                Optional.of("нет духа с киркой"),
                BoardWhy.waitReason(board, board.find("clear:2").orElseThrow(), new StockView(Map.of()), 0),
                "Гейт по инструменту виден в waitReason доски (и на экране J): иначе расчистка ждёт молча (INC-67) При смене waitReason_ToolGateNote_ShownUntilClaimed проверь это утверждение.");
        board.claim("clear:2", SPIRIT_ONE, 0);
        assertEquals(
                Optional.empty(),
                board.waitNote("clear:2"),
                "Выданное задание причину ожидания снимает Иначе тест waitReason_ToolGateNote_ShownUntilClaimed перестанет ловить эту поломку. При смене waitReason_ToolGateNote_ShownUntilClaimed проверь это утверждение.");
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
                        + "PENDING и кейс зацикливается каждую пересборку При смене submit_DoneFirstStep_NotReopened проверь это утверждение.");
        assertEquals(
                BoardStatus.DONE,
                board.statusOf("first:clear_grounds").orElse(null),
                "Сданный шаг остаётся DONE: факт «сделано» — условие цепочки, а не рабочая очередь Иначе тест submit_DoneFirstStep_NotReopened перестанет ловить эту поломку. При смене submit_DoneFirstStep_NotReopened проверь это утверждение.");
        assertTrue(
                board.ledger().has(BoardCase.CLEAR_GROUNDS, 1),
                "Отметка ledger остаётся и после отказа переоткрыть шаг: по ней цепочка видит "
                        + "выполненный кейс после перезапуска Иначе тест submit_DoneFirstStep_NotReopened перестанет ловить эту поломку. При смене submit_DoneFirstStep_NotReopened проверь это утверждение.");
    }

    @Test
    void fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        String id = "first:clear_grounds";
        for (int attempt = 1; attempt <= BoardTuning.ticketFailLimit(); attempt++) {
            UUID holder = new UUID(0, attempt);
            board.submit(ticketWithResources(id, BoardCase.CLEAR_GROUNDS, now[0], List.of(), Map.of("dirt", 9)));
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(id, holder, now[0]),
                    "Попытка " + attempt
                            + ": задание обязано выдаваться, иначе провал не к чему привязать При смене fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked проверь это утверждение.");
            assertTrue(
                    board.fail(id),
                    "Попытка " + attempt
                            + ": провал скрипта обязан дойти до доски Иначе тест fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked перестанет ловить эту поломку. При смене fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked проверь это утверждение.");
            board.prune(now[0]);
            now[0] += Math.max(BoardTuning.failBackoffTicks(), board.casePauseTicks(holder, BoardCase.CLEAR_GROUNDS));
        }

        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(id).orElse(null),
                "После предела провалов подряд задание блокируется: вечный цикл «открыть → провалить» "
                        + "не должен повторяться каждую пересборку Иначе тест fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked перестанет ловить эту поломку. При смене fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked проверь это утверждение.");
        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(ticketWithResources(id, BoardCase.CLEAR_GROUNDS, now[0], List.of(), Map.of("dirt", 9))),
                "Заблокированное задание не переиздаётся с теми же условиями: иначе предел провалов "
                        + "ничего не ограничивает При смене fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked проверь это утверждение.");
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(id).orElse(null),
                "Статус BLOCKED сохраняется: снимает его только изменение условий задания Иначе тест fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked перестанет ловить эту поломку. При смене fail_RepeatedFailures_BlockTicketAtLimitAndKeepItBlocked проверь это утверждение.");
    }

    @Test
    void submit_OldTicket_Accepted() {
        Board board = boardAt(1_000_000);
        BoardTicket late = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(late),
                "У задания нет срока жизни: давняя постановка обязана попадать в очередь, "
                        + "актуальность проверяется причиной при пересборке, а не смертью по таймеру Иначе тест submit_OldTicket_Accepted перестанет ловить эту поломку. При смене submit_OldTicket_Accepted проверь это утверждение.");
    }

    @Test
    void submit_NullTicket_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> boardAt(0).submit(null),
                "tasket=null обязан отвергаться: доска хранит задание, а не пустую заявку Иначе тест submit_NullTicket_Rejected перестанет ловить эту поломку. При смене submit_NullTicket_Rejected проверь это утверждение.");
    }

    @Test
    void submit_RepairCase_AcceptedLate() {
        Board board = boardAt(1_000_000);
        BoardTicket repair = ticket("fence_1", BoardCase.REPAIR_FENCE, 0, List.of());

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(repair),
                "Ремонт ограждений живёт до выполнения: несделанный ремонт значил бы "
                        + "открытые ворота навсегда Иначе тест submit_RepairCase_AcceptedLate перестанет ловить эту поломку. При смене submit_RepairCase_AcceptedLate проверь это утверждение.");
    }

    @Test
    void release_AssignedTicket_ReturnsToQueueWithoutOwner() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());

        board.submit(build);
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("build_1", SPIRIT_ONE, 0),
                "Перед проверкой возврата задание обязано быть выдано первому духу Иначе тест release_AssignedTicket_ReturnsToQueueWithoutOwner перестанет ловить эту поломку. При смене release_AssignedTicket_ReturnsToQueueWithoutOwner проверь это утверждение.");
        assertTrue(
                board.release("build_1"),
                "Выданное задание обязано возвращаться в очередь: погибший владелец не "
                        + "должен держать задание навсегда Иначе тест release_AssignedTicket_ReturnsToQueueWithoutOwner перестанет ловить эту поломку. При смене release_AssignedTicket_ReturnsToQueueWithoutOwner проверь это утверждение.");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("build_1", SPIRIT_TWO, 0),
                "После возврата владелец сброшен: задание обязано быть выдано любому "
                        + "свободному духу, а не лежать в очереди с мёртвым владельцем Иначе тест release_AssignedTicket_ReturnsToQueueWithoutOwner перестанет ловить эту поломку. При смене release_AssignedTicket_ReturnsToQueueWithoutOwner проверь это утверждение.");
    }

    @Test
    void release_UnassignedTicket_NotTouched() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());
        BoardTicket waiting = ticket("supply_1", BoardCase.SUPPLY_SITE, 0, List.of());

        board.submit(build);
        board.submit(waiting);
        assertFalse(
                board.release("build_1"),
                "Задание, которое никто не брал, возвращать нечего: оно уже в очереди Иначе тест release_UnassignedTicket_NotTouched перестанет ловить эту поломку. При смене release_UnassignedTicket_NotTouched проверь это утверждение.");
        assertFalse(
                board.release("missing"),
                "Неизвестный id возвращать нечего: доска не плодит задания из отказов Иначе тест release_UnassignedTicket_NotTouched перестанет ловить эту поломку. При смене release_UnassignedTicket_NotTouched проверь это утверждение.");
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
                        + "попадает в выдачу, иначе дух пришёл бы к пустому загону При смене claimable_UnfinishedDependency_Excluded проверь это утверждение.");
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
                "Цепочка начинается с capture: он готов к выдаче Иначе тест claimable_Complete_UnlocksChain перестанет ловить эту поломку. При смене claimable_Complete_UnlocksChain проверь это утверждение.");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("capture_1", SPIRIT_ONE, 0),
                "capture выдан первому духу Иначе тест claimable_Complete_UnlocksChain перестанет ловить эту поломку. При смене claimable_Complete_UnlocksChain проверь это утверждение.");
        assertTrue(
                board.complete("capture_1"),
                "capture завершён Иначе тест claimable_Complete_UnlocksChain перестанет ловить эту поломку. При смене claimable_Complete_UnlocksChain проверь это утверждение.");

        assertEquals(
                List.of(lure),
                board.claimable(new StockView(Map.of()), 0),
                "Завершение разблокирует зависящее: доска сама выдаёт ждущее задание по "
                        + "порядку, без второго вызова источника Иначе тест claimable_Complete_UnlocksChain перестанет ловить эту поломку. При смене claimable_Complete_UnlocksChain проверь это утверждение.");
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
                        + "цикл животного рвался бы на первом же prune При смене claimable_AfterPruneOfDone_RetainsDependency проверь это утверждение.");
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
                "Цепочка начинается с загона: он готов к выдаче Иначе тест claimable_Chain_Unlocks перестанет ловить эту поломку. При смене claimable_Chain_Unlocks проверь это утверждение.");
        board.claim("pen_1", SPIRIT_ONE, 0);
        board.complete("pen_1");
        assertEquals(
                List.of(lure),
                board.claimable(new StockView(Map.of()), 0),
                "После загона — приманить: цепочка идёт строго по порядку Иначе тест claimable_Chain_Unlocks перестанет ловить эту поломку. При смене claimable_Chain_Unlocks проверь это утверждение.");
        board.claim("lure_1", SPIRIT_ONE, 0);
        board.complete("lure_1");
        assertEquals(
                List.of(dismantle),
                board.claimable(new StockView(Map.of()), 0),
                "После приманки — разобрать укрытие: последнее звено цепочки Иначе тест claimable_Chain_Unlocks перестанет ловить эту поломку. При смене claimable_Chain_Unlocks проверь это утверждение.");
    }

    @Test
    void claimable_Gating_HoldsInQueue() {
        Board board = boardAt(0);
        BoardTicket build = ticketWithResources("build_1", BoardCase.BUILD_HOUSE, 0, List.of(), Map.of("dirt", 9));
        board.submit(build);

        List<BoardTicket> emptyStock = board.claimable(new StockView(Map.of()), 0);

        assertEquals(
                List.of(),
                emptyStock,
                "Гейт WAITING держит задание вне выдачи: дух не начнёт стройку без земли Иначе тест claimable_Gating_HoldsInQueue перестанет ловить эту поломку. При смене claimable_Gating_HoldsInQueue проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.WAITING_RESOURCES),
                board.statusOf("build_1"),
                "Статус WAITING_RESOURCES виден наблюдателю: задание ждёт ресурс, а не исчезает Иначе тест claimable_Gating_HoldsInQueue перестанет ловить эту поломку. При смене claimable_Gating_HoldsInQueue проверь это утверждение.");
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
                        + "без повторного вызова источника Иначе тест claimable_StockArrived_ReleasesWaiting перестанет ловить эту поломку. При смене claimable_StockArrived_ReleasesWaiting проверь это утверждение.");
    }

    @Test
    void claim_SameTicket_TakenByOther() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());
        board.submit(build);
        board.claimable(new StockView(Map.of()), 0);
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("build_1", SPIRIT_ONE, 0),
                "Первый дух получает задание Иначе тест claim_SameTicket_TakenByOther перестанет ловить эту поломку. При смене claim_SameTicket_TakenByOther проверь это утверждение.");
        assertEquals(
                BoardOutcome.TAKEN_BY_OTHER,
                board.claim("build_1", SPIRIT_TWO, 0),
                "Второй не получает чужое задание: один исполнитель, остальные прикрывают "
                        + "существующим механизмом координатора Иначе тест claim_SameTicket_TakenByOther перестанет ловить эту поломку. При смене claim_SameTicket_TakenByOther проверь это утверждение.");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("build_1", SPIRIT_ONE, 0),
                "Повторный claim того же духа идемпотентен: адаптер не теряет задание при "
                        + "перевыдаче после пересборки Иначе тест claim_SameTicket_TakenByOther перестанет ловить эту поломку. При смене claim_SameTicket_TakenByOther проверь это утверждение.");
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
                        + "остаётся WAITING_RESOURCES, доска ставит добычу недостающего Иначе тест claim_MissingResources_GateBlocked перестанет ловить эту поломку. При смене claim_MissingResources_GateBlocked проверь это утверждение.");
    }

    @Test
    void claim_UnknownId_NotFound() {
        Board board = boardAt(0);
        assertEquals(
                BoardOutcome.NOT_FOUND,
                board.claim("ghost_1", SPIRIT_ONE, 0),
                "Неизвестный id обязан возвращать NOT_FOUND: адаптер отличает опечатку "
                        + "от выдачи и не выдумывает задание Иначе тест claim_UnknownId_NotFound перестанет ловить эту поломку. При смене claim_UnknownId_NotFound проверь это утверждение.");
    }

    @Test
    void claim_NegativeNow_Rejected() {
        Board board = boardAt(0);
        BoardTicket build = ticket("build_1", BoardCase.BUILD_HOUSE, 0, List.of());
        board.submit(build);
        assertThrows(
                IllegalArgumentException.class,
                () -> board.claim("build_1", SPIRIT_ONE, -1),
                "Отрицательное время обязано отвергаться: оно сломало бы сортировку выдачи Иначе тест claim_NegativeNow_Rejected перестанет ловить эту поломку. При смене claim_NegativeNow_Rejected проверь это утверждение.");
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
        assertTrue(
                board.cancel("pen_1"),
                "Отмена pen снимает задание Иначе тест cancel_Dependants_Removed перестанет ловить эту поломку. При смене cancel_Dependants_Removed проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("pen_1"),
                "pen снят: жизненный цикл животного прерван Иначе тест cancel_Dependants_Removed перестанет ловить эту поломку. При смене cancel_Dependants_Removed проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("lure_1"),
                "Зависящее снимается вместе с предшественником: иначе «приманить» ждало "
                        + "бы отменённый загон навсегда При смене cancel_Dependants_Removed проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("dismantle_1"),
                "Каскад отмены проходит всю цепочку: задания без предшественника не могут "
                        + "появиться в выдаче Иначе тест cancel_Dependants_Removed перестанет ловить эту поломку. При смене cancel_Dependants_Removed проверь это утверждение.");
    }

    @Test
    void cancel_UnknownId_Noop() {
        Board board = boardAt(0);
        assertFalse(
                board.cancel("ghost_1"),
                "Отмена неизвестного задания не делает ничего: ложная тревога каскадом не идёт Иначе тест cancel_UnknownId_Noop перестанет ловить эту поломку. При смене cancel_UnknownId_Noop проверь это утверждение.");
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
                        + "для зависимых, а история провалов и отмен — в журнале, не в очереди Иначе тест prune_KeepsDone_RemovesFailedAndCancelled перестанет ловить эту поломку. При смене prune_KeepsDone_RemovesFailedAndCancelled проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.DONE),
                board.statusOf("done_1"),
                "Завершённое задание остаётся на доске со статусом DONE: по нему другие задания "
                        + "видят условие «постройка стоит» Иначе тест prune_KeepsDone_RemovesFailedAndCancelled перестанет ловить эту поломку. При смене prune_KeepsDone_RemovesFailedAndCancelled проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                board.find("failed_1"),
                "Проваленное задание снято из очереди: иначе повторная постановка того же id "
                        + "давала бы DUPLICATE вечно и ретрай после отката не работал бы При смене prune_KeepsDone_RemovesFailedAndCancelled проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                board.find("cancelled_1"),
                "Отменённое задание снято из очереди Иначе тест prune_KeepsDone_RemovesFailedAndCancelled перестанет ловить эту поломку. При смене prune_KeepsDone_RemovesFailedAndCancelled проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("pending_1"),
                "Ждущее задание остаётся в очереди: prune не трогает работу Иначе тест prune_KeepsDone_RemovesFailedAndCancelled перестанет ловить эту поломку. При смене prune_KeepsDone_RemovesFailedAndCancelled проверь это утверждение.");
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
                        + "поселения ограничена, иначе разрасталась бы неограниченно При смене prune_Oldest_Evicted проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("build_0"),
                "Самое старое задание вытеснено: переполнение очереди не молча теряет "
                        + "постановку, а переводит её в отменённые Иначе тест prune_Oldest_Evicted перестанет ловить эту поломку. При смене prune_Oldest_Evicted проверь это утверждение.");
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
                        + "духа не закрыл бы добычу, и она висела бы ASSIGNED до срока При смене assignedTo_Returns_TicketClaimedBySpirit проверь это утверждение.");
        assertEquals(
                List.of(),
                board.assignedTo(SPIRIT_TWO),
                "Чужое задание не числится за вторым духом: занятость считается по держателю Иначе тест assignedTo_Returns_TicketClaimedBySpirit перестанет ловить эту поломку. При смене assignedTo_Returns_TicketClaimedBySpirit проверь это утверждение.");
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
                "Держатель задания виден в счётчике занятости: по нему доска решает, свободен ли дух Иначе тест assignedCounts_Counts_ClaimedTicket перестанет ловить эту поломку. При смене assignedCounts_Counts_ClaimedTicket проверь это утверждение.");
    }

    @Test
    void holderOf_ReportsClaimHolder_ForObserver() {
        Board board = boardAt(0);
        board.submit(ticket("supply:dirt", BoardCase.SUPPLY_SITE, 0, List.of()));
        assertEquals(
                Optional.empty(),
                board.holderOf("supply:dirt"),
                "Задание без владельца и без выдачи не имеет держателя: наблюдатель видит «любой свободный» Иначе тест holderOf_ReportsClaimHolder_ForObserver перестанет ловить эту поломку. При смене holderOf_ReportsClaimHolder_ForObserver проверь это утверждение.");
        board.claim("supply:dirt", SPIRIT_ONE, 0);
        assertEquals(
                Optional.of(SPIRIT_ONE),
                board.holderOf("supply:dirt"),
                "Держатель задания — тот, кто его взял: по нему наблюдатель REST показывает assignee Иначе тест holderOf_ReportsClaimHolder_ForObserver перестанет ловить эту поломку. При смене holderOf_ReportsClaimHolder_ForObserver проверь это утверждение.");
        board.release("supply:dirt");
        assertEquals(
                Optional.empty(),
                board.holderOf("supply:dirt"),
                "Отпущенное задание снова без держателя: assignee пуст, задание ждёт любого свободного Иначе тест holderOf_ReportsClaimHolder_ForObserver перестанет ловить эту поломку. При смене holderOf_ReportsClaimHolder_ForObserver проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                board.holderOf("нет-такого"),
                "Неизвестный id не даёт держателя: наблюдатель не выдумывает духа Иначе тест holderOf_ReportsClaimHolder_ForObserver перестанет ловить эту поломку. При смене holderOf_ReportsClaimHolder_ForObserver проверь это утверждение.");
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
                "Адресное задание числится за владельцем из заявки ещё до выдачи: наблюдатель видит адресата Иначе тест holderOf_ReportsAssignee_FromTicket перестанет ловить эту поломку. При смене holderOf_ReportsAssignee_FromTicket проверь это утверждение.");
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
                        + "а не первое поступившее Иначе тест all_Sorted_ByPriority перестанет ловить эту поломку. При смене all_Sorted_ByPriority проверь это утверждение.");
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
                        + "сравнивает снимки между пересборками Иначе тест all_Is_Deterministic перестанет ловить эту поломку. При смене all_Is_Deterministic проверь это утверждение.");
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
                        + "очереди получили бы разные задания при одинаковом входе При смене claimable_Order_Deterministic проверь это утверждение.");
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
                            + "обходит цепочку порядка, даже если ресурсы уже есть Иначе тест claimable_UnfinishedDependency_NeverIssued перестанет ловить эту поломку. При смене claimable_UnfinishedDependency_NeverIssued проверь это утверждение.");
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
                        + "задания, снятие — только по актуальности причины Иначе тест prune_AfterRun_KeepsPending перестанет ловить эту поломку. При смене prune_AfterRun_KeepsPending проверь это утверждение.");
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
                "Завершённое задание читается со статусом DONE Иначе тест complete_DoneTicket_StaysVisibleInAll перестанет ловить эту поломку. При смене complete_DoneTicket_StaysVisibleInAll проверь это утверждение.");
        assertTrue(
                board.all().stream().anyMatch(ticket -> "mine_1".equals(ticket.id())),
                "Завершённое задание видно в all(): DONE — отметка выполнения на доске, "
                        + "а не удалённая строка Иначе тест complete_DoneTicket_StaysVisibleInAll перестанет ловить эту поломку. При смене complete_DoneTicket_StaysVisibleInAll проверь это утверждение.");
        assertTrue(
                board.ledger().has(BoardCase.BUILD_MINE, 1),
                "Завершение пишет отметку в ledger: условие «уровень 1 шахты готов» переживает "
                        + "даже снятие билета и перезапуск через журнал Иначе тест complete_DoneTicket_StaysVisibleInAll перестанет ловить эту поломку. При смене complete_DoneTicket_StaysVisibleInAll проверь это утверждение.");
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
                "Уровень 2 не выдаётся раньше уровня 1: уровень не прыгает Иначе тест claimable_BuildingDependency_UnlockedByLevelDone перестанет ловить эту поломку. При смене claimable_BuildingDependency_UnlockedByLevelDone проверь это утверждение.");
        board.claim("mine_1", SPIRIT_ONE, 0);
        board.complete("mine_1");
        assertEquals(
                List.of(levelTwo),
                board.claimable(new StockView(Map.of()), 0).stream()
                        .filter(ticket -> "mine_2".equals(ticket.id()))
                        .toList(),
                "Зависимое от building:BUILD_MINE:1 выдаётся после выполнения уровня 1: условие "
                        + "считается по живому DONE-заданию Иначе тест claimable_BuildingDependency_UnlockedByLevelDone перестанет ловить эту поломку. При смене claimable_BuildingDependency_UnlockedByLevelDone проверь это утверждение.");
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
                        + "«выполнено» прошлой сессии из журнала при старте Иначе тест claimable_BuildingDependency_LedgerNoteSatisfies перестанет ловить эту поломку. При смене claimable_BuildingDependency_LedgerNoteSatisfies проверь это утверждение.");
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
                "Вытеснение снимает только самое старое невыданное: DONE не участвует в подсчёте "
                        + "переполнения Иначе тест evictOverflow_DonePresent_SparesDone перестанет ловить эту поломку. При смене evictOverflow_DonePresent_SparesDone проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.DONE),
                board.statusOf("done_1"),
                "Вытеснение переполнения не трогает DONE: выполненная постройка остаётся условием Иначе тест evictOverflow_DonePresent_SparesDone перестанет ловить эту поломку. При смене evictOverflow_DonePresent_SparesDone проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("build_0"),
                "Вытеснено самое старое невыданное задание Иначе тест evictOverflow_DonePresent_SparesDone перестанет ловить эту поломку. При смене evictOverflow_DonePresent_SparesDone проверь это утверждение.");
    }

    @Test
    void evictOverflow_AlwaysRepair_IsSparedBeforeWorldCleanup() {
        Board board = boardAt(0);
        board.submit(ticket("repair:build_mine", BoardCase.REPAIR_BUILDING, 0, List.of()));
        for (int index = 0; index <= BoardTuning.MAX_PENDING; index++) {
            board.submit(new BoardTicket(
                    "world_fill_" + index,
                    BoardCase.FILL_CRATER,
                    Optional.empty(),
                    BoardRules.priorityOf(BoardCase.FILL_CRATER),
                    index + 1,
                    1,
                    "graph:event:backfill:backfill",
                    List.of(),
                    Map.of()));
        }
        board.prune(0);
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair:build_mine"),
                "Постоянный ремонт шахты не вытесняется потоком засыпок: иначе repair:build_mine снимается "
                        + "каждый пересчёт, и шахта 75→74% стоит без ремонта При смене evictOverflow_AlwaysRepair_IsSparedBeforeWorldCleanup проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("world_fill_0"),
                "Переполнение разгружают задачи мира, а не постоянный ремонт Иначе тест evictOverflow_AlwaysRepair_IsSparedBeforeWorldCleanup перестанет ловить эту поломку. При смене evictOverflow_AlwaysRepair_IsSparedBeforeWorldCleanup проверь это утверждение.");
    }

    @Property
    void all_AfterThousandOps_IsDeterministic(@ForAll long seed) {
        assertEquals(
                runOps(seed),
                runOps(seed),
                "После 1000 submit/complete/cancel очередь детерминирована: при том же входе та же "
                        + "очередь, иначе пересборки плывут и журнал невоспроизводим При смене all_AfterThousandOps_IsDeterministic проверь это утверждение.");
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
                "Каменный шаг-помощник одноразовый, как «first:»: переподача сделанного крафтила бы топор заново Иначе тест submit_StonePartDone_Duplicate перестанет ловить эту поломку. При смене submit_StonePartDone_Duplicate проверь это утверждение.");
    }

    @Test
    void resetForNewRules_StonePartWaiting_Removed() {
        Board board = new Board(() -> 0);
        BoardTicket axe = simpleTicket("stone:craft_stone_axe", BoardCase.CRAFT_STONE_AXE);
        board.submit(axe);
        assertTrue(
                board.resetForNewRules().contains(axe.id()),
                "Ожидающий каменный шаг — задание источника: при смене сборки сбрасывается (INC-39) Иначе тест resetForNewRules_StonePartWaiting_Removed перестанет ловить эту поломку. При смене resetForNewRules_StonePartWaiting_Removed проверь это утверждение.");
    }

    @Test
    void resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        BoardTicket gather = simpleTicket("gather:oak_log", BoardCase.GATHER_MATERIALS);
        BoardTicket blocked = simpleTicket("first:craft_furnace", BoardCase.CRAFT_FURNACE);
        BoardTicket done = simpleTicket("first:craft_anvil", BoardCase.CRAFT_ANVIL);
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
            GraphBlock.Need need = attempt == BoardTuning.ticketFailLimit() ? new GraphBlock.Item("dirt", 1) : null;
            board.fail(blocked.id(), new StockView(Map.of()), null, need);
            board.prune(now[0]);
            now[0] += Math.max(BoardTuning.failBackoffTicks(), board.casePauseTicks(holder, BoardCase.CRAFT_FURNACE));
        }
        assertTrue(
                board.blocked(blocked.id()),
                "Сцена: шаг первых шагов заблокирован провалами подряд При смене resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept проверь это утверждение.");
        List<String> removed = board.resetForNewRules();
        assertTrue(
                removed.contains("gather:oak_log")
                        && board.find("gather:oak_log").isEmpty(),
                "Ожидающее задание источника старых правил сброшено: источник поставит его по новым (INC-36) Иначе тест resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept перестанет ловить эту поломку. При смене resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept проверь это утверждение.");
        assertTrue(
                removed.contains("gather:oak_fence")
                        && board.find("gather:oak_fence").isEmpty(),
                "Выданное задание источника старых правил тоже сброшено: иначе живёт после выкладки (INC-39) При смене resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept проверь это утверждение.");
        assertTrue(
                board.find(done.id()).isPresent(),
                "Сделанное остаётся: журнал этапов не теряется Иначе тест resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept перестанет ловить эту поломку. При смене resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept проверь это утверждение.");
        assertTrue(
                board.find(rest.id()).isPresent(),
                "Задание REST/игрока не сбрасывается Иначе тест resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept перестанет ловить эту поломку. При смене resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept проверь это утверждение.");
        assertTrue(
                !board.blocked(blocked.id()),
                "Заблокированное по старым правилам больше не держит цепочку:  Иначе тест resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept перестанет ловить эту поломку. При смене resetForNewRules_BlockedAndSourceWaiting_RemovedDoneKept проверь это утверждение."
                        + board.statusOf(blocked.id()));
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
