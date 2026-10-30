package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardActivitiesTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static int lowestQueuePriority() {
        int lowest = Integer.MAX_VALUE;
        for (BoardCase boardCase : BoardCase.values()) {
            if (!BoardActivities.isActivity(boardCase)) {
                lowest = Math.min(lowest, BoardRules.priorityOf(boardCase));
            }
        }
        return lowest;
    }

    @Test
    void occupy_Activity_ShownAsAssignedTicketOfSpirit() {
        Board board = new Board(() -> 100);

        board.occupy(SPIRIT, BoardCase.ACTIVITY_CHOP, 100);

        String id = BoardActivities.idOf(SPIRIT);
        assertEquals(
                Optional.of(BoardStatus.ASSIGNED),
                board.statusOf(id),
                "Занятие духа видно на доске активным заданием: иначе игрок не знает, чем занят дух");
        assertEquals(Optional.of(SPIRIT), board.holderOf(id), "Держатель занятия — сам дух");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_CHOP), board.occupation(SPIRIT), "Занятие читается кейсом ACTIVITY_*");
    }

    @Test
    void occupy_OtherActivity_ReplacesSingleTicket() {
        Board board = new Board(() -> 100);
        board.occupy(SPIRIT, BoardCase.ACTIVITY_CHOP, 100);

        board.occupy(SPIRIT, BoardCase.ACTIVITY_REST, 100);

        assertEquals(1, board.all().size(), "У духа одно задание-занятие: смена занятия заменяет его, а не копит");
        assertEquals(Optional.of(BoardCase.ACTIVITY_REST), board.occupation(SPIRIT), "Показано новое занятие");
    }

    @Test
    void occupy_Activity_NotCountedAsBusyNorClaimableNorStored() {
        Board board = new Board(() -> 100);
        board.occupy(SPIRIT, BoardCase.ACTIVITY_CRAFT, 100);

        assertEquals(
                Map.of(),
                board.assignedCounts(),
                "Занятие не держит духа для доски: иначе задание доски не вытеснило бы фоновое занятие");
        assertEquals(List.of(), board.assignedTo(SPIRIT), "Занятие не закрывается как задание доски");
        assertEquals(List.of(), board.claimable(new StockView(Map.of()), 100), "Занятие не выдаётся из очереди");
        assertEquals(
                0,
                board.snapshot().entries().size(),
                "Занятие — живой факт, на диск не пишется и не восстанавливается");
    }

    @Test
    void vacate_Occupied_RemovesTicket() {
        Board board = new Board(() -> 100);
        board.occupy(SPIRIT, BoardCase.ACTIVITY_SMELT, 100);

        assertTrue(board.vacate(SPIRIT), "Снятие существующего занятия сообщает успех");
        assertFalse(board.vacate(SPIRIT), "Повторное снятие — нечего снимать");
        assertEquals(List.of(), board.all(), "Снятое занятие с доски исчезает");
    }

    @Test
    void occupy_QueueCase_Rejected() {
        Board board = new Board(() -> 100);

        assertThrows(
                IllegalArgumentException.class,
                () -> board.occupy(SPIRIT, BoardCase.BUILD_HOUSE, 100),
                "Занятием показывается только ACTIVITY_*: кейс очереди так подменил бы выдачу");
    }

    @Test
    void occupy_ActivityWithTarget_CardCarriesScriptAndTarget() {
        Board board = new Board(() -> 100);

        board.occupy(SPIRIT, BoardCase.ACTIVITY_CHOP, "chop_tree", "-203,68,-560", 100);

        BoardTicket card = board.all().get(0);
        assertEquals(
                List.of("script=chop_tree", "target=-203,68,-560"),
                card.modifiers(),
                "Карточка занятия обязана нести скрипт и цель: по ним владелец видит, что дух делает и где");
    }

    @Test
    void caseOf_Script_NamesWhatTheSpiritDoes() {
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_CHOP),
                BoardActivities.caseOfScript("chop_tree"),
                "Рубка вне задания доски (REST, мозг, план) обязана быть карточкой ACTIVITY_CHOP, а не "
                        + "безымянным ACTIVITY_EXTERNAL: владелец по карточке решает, чем дух занят");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_ESCAPE),
                BoardActivities.caseOfScript("dig_out"),
                "dig_out — спасение из-под земли, карточка ACTIVITY_ESCAPE с целью, а не внешнее действие");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_MOVE),
                BoardActivities.caseOfScript("goto"),
                "Перемещение — карточка ACTIVITY_MOVE: ходьба без задания тоже видна на доске");
        assertEquals(
                Optional.empty(),
                BoardActivities.caseOfScript("no_such_script"),
                "Неизвестный скрипт не получает выдуманный кейс: карточку ему даёт ACTIVITY_EXTERNAL");
    }

    @Test
    void targetOf_Plan_SaysWhereOrWhat() {
        TaskNode call = new ScriptCall("dig_out", Map.of("x", 10, "y", 61, "z", -5));

        assertEquals(
                "10,61,-5",
                BoardActivities.targetOf(call),
                "Цель карточки — координаты плана: по ней владелец видит, куда дух идёт, а не «внешнее действие»");
        assertEquals(
                List.of("script=dig_out", "target=10,61,-5"),
                BoardActivities.details("dig_out", BoardActivities.targetOf(call)),
                "Цель попадает в подробности карточки рядом со скриптом: иначе она не дойдёт до экрана доски");
    }

    @Test
    void currentCard_AssignedRunning_ShowsWhatTheSpiritDoesNow() {
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_MOVE),
                new BoardActivities.CardFacts(false, true, false, null, false, null, true, false, "goto").card(),
                "Дух с заданием доски, идущий к цели, обязан иметь карточку текущего действия ACTIVITY_MOVE: "
                        + "иначе на /v1/board 0 карточек ACTIVITY_*, и владелец не видит, чем занят дух");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_FORAGE),
                new BoardActivities.CardFacts(false, true, false, null, false, null, true, false, "gather").card(),
                "Сбор с назначенным заданием доски — карточка ACTIVITY_FORAGE, а не устаревшее activity из плана");
    }

    @Test
    void currentCard_RetryBetweenAttempts_KeepsActivityCard() {
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_SMELT),
                new BoardActivities.CardFacts(
                                false, false, false, null, true, BoardCase.ACTIVITY_SMELT, false, false, "smelt")
                        .card(),
                "Между попытками занятия дух свободен, но карточка обязана остаться: иначе доска мерцает "
                        + "и в паузах показывает «свободен», хотя цикл продолжается");
    }

    @Test
    void cardScript_GotoThenWork_NamesWorkNotApproach() {
        TaskNode plan = new SequenceNode(List.of(new ScriptCall("goto", Map.of()), new ScriptCall("smelt", Map.of())));

        assertEquals(
                "smelt",
                BoardActivities.cardScript("goto", plan),
                "Карточка занятия с планом «подход, потом плавка» обязана называть плавку: на плитке виден goto, "
                        + "владелец не понимает, чем дух занят");
        assertEquals(
                "goto",
                BoardActivities.cardScript("goto", null),
                "Без плана карточка называет текущий скрипт: команда «goto» остаётся подходом");
    }

    @Test
    void heldBy_Activity_ShowsTicketEvenThoughNotBoardTask() {
        Board board = new Board(() -> 100);
        board.occupy(SPIRIT, BoardCase.ACTIVITY_SMELT, "smelt", "-171,71,-489", 100);

        assertEquals(List.of(), board.assignedTo(SPIRIT), "Занятие не задание доски: очередь его не закрывает");
        BoardTicket held = BoardHeld.of(board, SPIRIT)
                .orElseThrow(() -> new AssertionError(
                        "У духа с карточкой занятия обязан быть держатель: иначе REST отдаёт board_task=null"));
        assertEquals(BoardActivities.idOf(SPIRIT), held.id(), "heldBy называет id карточки занятия");
        assertEquals(BoardCase.ACTIVITY_SMELT, held.boardCase(), "heldBy называет кейс занятия");
    }

    @Test
    void caseOf_EveryIntellectActivity_HasActivityCaseBelowQueue() {
        for (ActivityId activity : ActivityId.values()) {
            BoardCase boardCase = BoardActivities.caseOf(activity);

            assertTrue(
                    BoardActivities.isActivity(boardCase), "Занятие " + activity + " показывается кейсом ACTIVITY_*");
            assertTrue(
                    BoardRules.priorityOf(boardCase) < lowestQueuePriority(),
                    "Фоновое занятие " + activity + " ниже любого задания доски: одна шкала приоритета, "
                            + "задание доски вытесняет занятие");
        }
    }
}
