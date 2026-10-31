package com.theminespirit.core.board;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TransferCarriers {
    private static final String CAUSE_PREFIX = "transfer:";

    private TransferCarriers() {}

    public static String cause(String craftId) {
        return CAUSE_PREFIX + craftId;
    }

    public static void dropUnplanned(Board board, DispatchRound round, BoardTicket transfer) {
        if (transfer.boardCase() != BoardCase.TRANSFER_ITEM) {
            throw new IllegalArgumentException("Задание " + transfer.id() + " не передача: снимать по несобранному "
                    + "плану можно только передачу, остальные задания проваливаются своим порядком");
        }
        board.cancel(transfer.id());
        transfer.assignee().ifPresent(round::release);
    }

    public static Set<UUID> carrying(Board board, String craftId, Set<UUID> running) {
        Objects.requireNonNull(board, "Доска не может быть null: передачи берутся с неё");
        Objects.requireNonNull(running, "Набор духов с задачей не может быть null: без него каждый донор «несёт»");
        Set<UUID> carriers = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM || !cause(craftId).equals(ticket.cause())) {
                continue;
            }
            Optional<UUID> holder = ticket.assignee();
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.ASSIGNED && holder.isPresent() && running.contains(holder.get())) {
                carriers.add(holder.get());
            }
        }
        return carriers;
    }

    public static boolean awaitsDelivery(Board board, String craftId) {
        Objects.requireNonNull(board, "Доска не может быть null: ожидание передачи считается по ней");
        for (BoardTicket ticket : board.all()) {
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM || !cause(craftId).equals(ticket.cause())) {
                continue;
            }
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                return true;
            }
        }
        return false;
    }

    public static Set<UUID> planned(Board board, String craftId) {
        Objects.requireNonNull(board, "Доска не может быть null: передачи берутся с неё");
        Set<UUID> holders = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM || !cause(craftId).equals(ticket.cause())) {
                continue;
            }
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                continue;
            }
            ticket.assignee().ifPresent(holders::add);
        }
        return holders;
    }
}
