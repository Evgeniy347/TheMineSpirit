package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

public final class BoardWhy {
    private BoardWhy() {}

    public static Optional<String> waitReason(Board board, BoardTicket ticket, StockView stock, long now) {
        Objects.requireNonNull(board, "board не может быть null: причина берётся из состояния доски");
        Objects.requireNonNull(ticket, "ticket не может быть null: причина считается на конкретное задание");
        Objects.requireNonNull(stock, "stock не может быть null: нехватку материалов без снимка не посчитать");
        BoardStatus status = board.statusOf(ticket.id()).orElse(BoardStatus.PENDING);
        if (status == BoardStatus.BLOCKED) {
            return Optional.of("заблокировано после " + board.ticketFailures(ticket.id())
                    + " провалов подряд: ждёт изменения условий (запасов или самого задания)");
        }
        if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
            return Optional.empty();
        }
        Optional<String> held = board.holds().reason(ticket.id());
        if (held.isPresent()) {
            return held;
        }
        if (!SettlementStage.allows(
                ticket.boardCase(), ticket.level(), board.ledger().stage())) {
            return Optional.of("этап " + board.ledger().stage() + " не закрыт целиком: в мире не готово "
                    + board.ledger().stageGate().missing());
        }
        if (!board.dependenciesMet(ticket)) {
            return Optional.of("ждёт предшественников: " + ticket.dependsOn());
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        if (!missing.isEmpty()) {
            return Optional.of(shortage(ticket.requiredItems(), missing, stock));
        }
        if (board.recentlyFailed(ticket.id(), now)) {
            return Optional.of("откат после провала: задание вернётся в очередь после паузы");
        }
        Optional<UUID> holder = ticket.assignee();
        if (holder.isPresent()
                && ticket.boardCase() != BoardCase.RESCUE_SPIRIT
                && board.stuck().of(holder.get(), now).isPresent()) {
            StuckSpirits.Stuck stuck = board.stuck().of(holder.get(), now).orElseThrow();
            return Optional.of("держатель не двигается с тика " + stuck.since()
                    + (stuck.at() == null ? "" : " в " + stuck.at())
                    + ": задание ждёт, пока дух выйдет из ловушки (граф ставит rescue)");
        }
        if (holder.isPresent() && !board.availableTo(holder.get(), ticket, now)) {
            UUID spirit = holder.get();
            return Optional.of(
                    board.caseDetached(spirit, ticket.boardCase())
                            ? "держатель снят с кейса " + ticket.boardCase() + " после серии провалов"
                            : "держатель на паузе кейса " + ticket.boardCase() + " после "
                                    + board.consecutiveFailures(spirit, ticket.boardCase()) + " провалов подряд");
        }
        return board.waitNote(ticket.id());
    }

    static String shortage(Map<String, Integer> need, Map<String, Integer> missing, StockView stock) {
        List<String> parts = new ArrayList<>();
        for (String item : new TreeSet<>(missing.keySet())) {
            int have = stock.available().getOrDefault(item, 0);
            parts.add(item + "×" + need.getOrDefault(item, 0) + " (в сундуках двора и у духов " + have
                    + ", добыть или сделать ещё " + missing.get(item) + ")");
        }
        return "не хватает материалов: " + String.join("; ", parts);
    }
}
