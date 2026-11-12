package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.mining.LeftOreRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class SupplyGate {

    public record Request(String item, int count) {
        public Request {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Заявка без предмета: снабжение не знает, что нести");
            }
            if (count <= 0) {
                throw new IllegalArgumentException(
                        "Заявка на '" + item + "' с количеством " + count + ": нехватка всегда положительна");
            }
        }
    }

    public record Decision(List<Request> supply, List<Request> gather) {
        public Decision {
            Objects.requireNonNull(supply, "Снабжение не может быть null: пустой список задают пустым");
            Objects.requireNonNull(gather, "Добыча не может быть null: пустой список задают пустым");
            supply = List.copyOf(supply);
            gather = List.copyOf(gather);
        }

        public boolean isEmpty() {
            return supply.isEmpty() && gather.isEmpty();
        }
    }

    public static final String SHORTAGE = "shortage:";

    private SupplyGate() {}

    public static List<GraphBlock> shortages(Decision decision) {
        Objects.requireNonNull(decision, "Решение снабжения не может быть null: нехватка читается из него");
        List<GraphBlock> blocks = new ArrayList<>();
        for (Request request : decision.gather()) {
            blocks.add(new GraphBlock(
                    SHORTAGE + request.item(),
                    "",
                    new GraphBlock.Item(GraphNames.graphItem(request.item()), request.count())));
        }
        return List.copyOf(blocks);
    }

    public static boolean retains(BoardTicket ticket, Set<String> liveCauses) {
        Objects.requireNonNull(ticket, "Задание не может быть null: удержание считается по причине и id");
        Objects.requireNonNull(liveCauses, "Живые причины не могут быть null: пустой набор задают пустым");
        if (isToolTierSupply(ticket.id())) {
            return false;
        }
        return BoardTicket.ALWAYS_CAUSE.equals(ticket.cause())
                || liveCauses.contains(ticket.cause())
                || liveCauses.contains(baseCause(ticket.cause()));
    }

    private static String baseCause(String cause) {
        if (!cause.startsWith("graph:")) {
            return cause;
        }
        int at = cause.indexOf('@');
        return at < 0 ? cause : cause.substring(0, at);
    }

    public static boolean isSupply(String ticketId) {
        Objects.requireNonNull(ticketId, "Id задания не может быть null: вид заявки читается по его префиксу");
        return ticketId.startsWith("supply:") || ticketId.startsWith("supply_gather:");
    }

    public static boolean hasActiveSupply(Board board, Map<String, TicketTarget> targets, String item) {
        Objects.requireNonNull(board, "Доска не может быть null: активные заявки читаются из неё");
        Objects.requireNonNull(targets, "Цели заявок не могут быть null: предмет заявки лежит в цели");
        Objects.requireNonNull(item, "Предмет заявки не может быть null: ищем заявку на него");
        for (BoardTicket ticket : board.tickets()) {
            if (!isSupply(ticket.id())
                    || !board.statusOf(ticket.id()).map(BoardStatus::open).orElse(false)) {
                continue;
            }
            TicketTarget target = targets.get(ticket.id());
            if (target != null && item.equals(target.supplyItem())) {
                return true;
            }
        }
        return false;
    }

    public static TicketTarget merge(@Nullable TicketTarget before, TicketTarget next) {
        Objects.requireNonNull(next, "Новая заявка не может быть null: сливать нечего");
        if (before == null || next.supplyItem() == null) {
            return next;
        }
        if (next.supplyCount() <= before.supplyCount()) {
            return before.deposit() == null && next.deposit() != null ? before.withDeposit(next.deposit()) : before;
        }
        TicketTarget merged = next;
        if (merged.deposit() == null && before.deposit() != null) {
            merged = merged.withDeposit(before.deposit());
        }
        return merged;
    }

    public static boolean isToolTierSupply(String ticketId) {
        Objects.requireNonNull(ticketId, "Id задания не может быть null: маркер кирки ищется в его предмете");
        int colon = ticketId.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String prefix = ticketId.substring(0, colon);
        return ("supply".equals(prefix) || "supply_gather".equals(prefix))
                && LeftOreRules.isToolTier(ticketId.substring(colon + 1));
    }

    public static boolean needsDepositPoint(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс снабжения не может быть null: от него зависит точка сдачи");
        return boardCase != BoardCase.GATHER_MATERIALS;
    }

    public static Decision decide(
            Map<String, Integer> missing,
            Map<String, Integer> stock,
            int keep,
            Map<String, Long> closedAt,
            long now,
            long backoff) {
        Objects.requireNonNull(missing, "Нехватка не может быть null: пустую задают пустой картой");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(closedAt, "Откаты закрытого снабжения не могут быть null: пустые задают пустой картой");
        if (keep < 0) {
            throw new IllegalArgumentException(
                    "Неприкосновенный минимум " + keep + " отрицателен: минимум не бывает меньше нуля");
        }
        if (backoff < 0) {
            throw new IllegalArgumentException("Откат " + backoff + " отрицателен: пауза не бывает меньше нуля");
        }
        List<Request> supply = new ArrayList<>();
        List<Request> gather = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(missing).entrySet()) {
            String item = entry.getKey();
            int count = entry.getValue();
            if (count <= 0 || LeftOreRules.isToolTier(item)) {
                continue;
            }
            int takeable = Math.max(0, stock.getOrDefault(item, 0) - keep);
            if (takeable <= 0) {
                gather.add(new Request(item, count));
                continue;
            }
            String id = "supply:" + item;
            Long closed = closedAt.get(id);
            if (closed != null && now - closed < backoff) {
                continue;
            }
            supply.add(new Request(item, count));
        }
        return new Decision(supply, gather);
    }
}
