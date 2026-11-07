package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.Pair;
import static com.theminespirit.core.board.graph.AllocationCases.community;
import static com.theminespirit.core.board.graph.AllocationCases.dispatch;
import static com.theminespirit.core.board.graph.AllocationCases.id;
import static com.theminespirit.core.board.graph.AllocationCases.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ActivityTickets;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardWhy;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityBoardScenarioTests {
    @Test
    void expeditionTicket_WithCrew_LivesOnBoardWithWaitReason() {
        Board board = new Board(() -> 0);
        UUID leader = id("spirit-1");
        List<UUID> crew = List.of(leader, id("spirit-2"), id("spirit-3"));
        String id = "graph:event:expedition_ready:expedition";
        BoardTicket expedition =
                activity(id, BoardCase.ACTIVITY_EXPEDITION, Optional.of(leader), crew, 100, "mine_vein", "mine", 6000);

        assertEquals(BoardOutcome.ACCEPTED, board.submit(expedition), "Поход ставится на доску как задание графа");
        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(expedition),
                "Повторный пересчёт графа не плодит второй поход: у задания один id узла");
        assertEquals(1, board.all().size(), "На доске ровно один поход, а не копия на каждого духа: " + board.all());
        board.holds().hold(id, "нет команды: нужно 2 духа, рядом 0");

        assertTrue(board.claimable(new StockView(Map.of()), 0).isEmpty(), "Поход без команды не выдаётся духу");
        assertEquals(
                BoardOutcome.GATE_BLOCKED,
                board.claim(id, leader, 0),
                "Ручная выдача тоже уважает причину ожидания, а не тащит одного духа в шахту");
        assertTrue(
                BoardWhy.waitReason(board, expedition, new StockView(Map.of()), 0)
                        .orElse("")
                        .contains("нет команды"),
                "Владелец видит причину, почему поход не начался");

        board.holds().release(id);
        assertEquals(BoardOutcome.CLAIMED, board.claim(id, leader, 0), "Готовая команда получает поход");
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(id).orElseThrow(),
                "Поход виден на доске выданным, а не только в координаторе");
        assertTrue(
                board.assignedTo(id("spirit-2")).stream()
                        .anyMatch(ticket -> ticket.id().equals(id)),
                "Все участники команды заняты походом: доска не отдаст им другую работу");
        assertEquals(
                1,
                board.assignedCounts().getOrDefault(id("spirit-3"), 0),
                "Третий участник тоже занят один раз, без двойного счёта лидера и команды");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_EXPEDITION),
                board.occupation(id("spirit-2")),
                "REST и экран доски видят занятие участника как обычное задание");

        assertTrue(board.complete(id), "Возврат команды закрывает поход успехом");
        assertTrue(board.assignedTo(leader).isEmpty(), "После возврата команда свободна для новой работы");
        assertTrue(board.assignedTo(id("spirit-2")).isEmpty(), "Второй участник тоже освобождён");

        String next = id + ":2";
        board.submit(activity(
                next, BoardCase.ACTIVITY_EXPEDITION, Optional.of(leader), crew, 100, "mine_vein", "mine", 6000));
        assertEquals(BoardOutcome.CLAIMED, board.claim(next, leader, 0), "Новый поход выдаётся команде");
        assertTrue(board.cancel(next), "Отмена закрывает поход");
        assertEquals(
                BoardStatus.CANCELLED,
                board.statusOf(next).orElseThrow(),
                "Отменённое занятие не остаётся выданным и не блокирует духов");
        assertTrue(board.assignedTo(leader).isEmpty(), "После отмены лидер свободен");
    }

    @Test
    void nightGuard_FromGraph_ClosesByOutcome() {
        GraphWorld world = community(2).event("night");
        GraphTickets.Ticket guard = tickets(world).post().stream()
                .filter(ticket -> ticket.id().endsWith(":more4_guard_settlement"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ночью граф обязан поставить охрану поселения"));
        BoardTicket ticket = activity(
                guard.id(),
                guard.task().boardCase(),
                Optional.empty(),
                List.of(),
                guard.task().priority(),
                "patrol",
                "post",
                guard.task().ticks());
        Board board = new Board(() -> 0);
        board.submit(ticket);

        List<Pair> pairs = dispatch(board, world);

        assertEquals(1, pairs.size(), "Ночью охрану получает ровно один дух: " + pairs);
        assertEquals(guard.id(), pairs.get(0).ticket(), "Охрана на доске названа узлом графа, а не карточкой");
        assertTrue(
                board.occupation(id(pairs.get(0).spirit()))
                        .filter(BoardCase.ACTIVITY_GUARD::equals)
                        .isPresent(),
                "Занятие охраны видно у духа на доске");
        assertTrue(board.fail(guard.id(), new StockView(world.stock), "ночь кончилась"), "Провал закрывает охрану");
        assertEquals(
                BoardStatus.FAILED,
                board.statusOf(guard.id()).orElseThrow(),
                "Закрытое занятие не висит на доске ждущим");
        assertEquals(
                List.of("тик 0: ночь кончилась"),
                board.failureHistory(guard.id()),
                "Причина провала сохраняется: владелец видит, почему охрана не удалась");
        assertTrue(BoardActivities.isActivity(ticket.boardCase()), "Охрана остаётся занятием графа");
    }

    @Test
    void pendingActivity_UpdatedCrew_KeepsWaitingTicket() {
        Board board = new Board(() -> 0);
        String id = "graph:event:expedition_ready:expedition";
        UUID leader = id("spirit-1");
        List<UUID> crew = List.of(leader, id("spirit-2"));
        board.submit(activity(
                id, BoardCase.ACTIVITY_EXPEDITION, Optional.empty(), List.of(), 100, "mine_vein", "mine", 6000));
        board.holds().hold(id, "нет команды: нужно 2 духа, рядом 0");

        assertTrue(
                board.updatePending(activity(
                        id, BoardCase.ACTIVITY_EXPEDITION, Optional.of(leader), crew, 100, "mine_vein", "mine", 6000)),
                "Пересчёт графа обновляет ещё не выданное занятие, а не оставляет пустую команду");
        assertEquals(crew, board.find(id).orElseThrow().executors(), "Доска хранит новую команду похода до выдачи");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(id).orElseThrow(),
                "Обновление ждущего занятия не сбрасывает и не выдаёт его само");
        board.holds().release(id);
        assertEquals(BoardOutcome.CLAIMED, board.claim(id, leader, 0), "Обновлённая команда получает поход");
    }

    private static BoardTicket activity(
            String id,
            BoardCase activity,
            Optional<UUID> leader,
            List<UUID> crew,
            int priority,
            String script,
            String target,
            int ticks) {
        return ActivityTickets.ticket(new ActivityTickets.Request(
                new ActivityTickets.Identity(id, activity, id),
                new ActivityTickets.Crew(leader, crew),
                priority,
                0,
                new ActivityTickets.Plan(script, target, ticks, new ScriptCall(script, Map.of()))));
    }
}
