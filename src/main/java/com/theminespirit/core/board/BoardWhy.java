package com.theminespirit.core.board;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Почему задание доски сейчас не выдаётся — одной строкой для наблюдателя (REST {@code waitReason}, команда доски).
 * Живой прогон 2 (INC-23): 15 минут простоя ушли на догадки, а доска эти факты знает. Причины ядра: блокировка,
 * незакрытые предшественники, нехватка материалов, откат после провала, пауза кейса у держателя. Пусто — задание
 * готово к выдаче и ждёт свободного духа.
 */
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
        if (!board.dependenciesMet(ticket)) {
            return Optional.of("ждёт предшественников: " + ticket.dependsOn());
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        if (!missing.isEmpty()) {
            return Optional.of("не хватает материалов: " + missing);
        }
        if (board.recentlyFailed(ticket.id(), now)) {
            return Optional.of("откат после провала: задание вернётся в очередь после паузы");
        }
        Optional<UUID> holder = ticket.assignee();
        if (holder.isPresent() && !board.availableTo(holder.get(), ticket.boardCase(), now)) {
            UUID spirit = holder.get();
            return Optional.of(
                    board.caseDetached(spirit, ticket.boardCase())
                            ? "держатель снят с кейса " + ticket.boardCase() + " после серии провалов"
                            : "держатель на паузе кейса " + ticket.boardCase() + " после "
                                    + board.consecutiveFailures(spirit, ticket.boardCase()) + " провалов подряд");
        }
        return board.waitNote(ticket.id());
    }
}
