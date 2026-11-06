package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AbsentOwners {
    public static final long GRACE_TICKS = 6000;

    private final Map<UUID, Long> missingSince = new HashMap<>();

    public List<String> sweep(Board board, Set<UUID> live, long now) {
        Objects.requireNonNull(board, "Доска не может быть null: задания отсутствующих духов снимаются с неё");
        Objects.requireNonNull(live, "Живые духи не могут быть null: пустой набор — пустое множество");
        Set<UUID> absent = new HashSet<>();
        List<BoardTicket> orphaned = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                continue;
            }
            Optional<UUID> owner = ticket.assignee().or(() -> TransferTicket.recipient(ticket));
            if (owner.isPresent() && !live.contains(owner.orElseThrow())) {
                absent.add(owner.orElseThrow());
                orphaned.add(ticket);
            }
        }
        for (BoardState.Stuck mark : board.stuck().snapshot()) {
            if (!live.contains(mark.spirit())) {
                absent.add(mark.spirit());
            }
        }
        missingSince.keySet().retainAll(absent);
        absent.forEach(owner -> missingSince.putIfAbsent(owner, now));
        for (UUID owner : absent) {
            if (now - missingSince.getOrDefault(owner, now) >= GRACE_TICKS) {
                board.stuck().release(owner, now, "метка снята: дух удалён или не появлялся " + GRACE_TICKS + " тиков");
            }
        }
        List<String> cancelled = new ArrayList<>();
        for (BoardTicket ticket : orphaned) {
            UUID owner =
                    ticket.assignee().or(() -> TransferTicket.recipient(ticket)).orElseThrow();
            if (now - missingSince.getOrDefault(owner, now) >= GRACE_TICKS
                    && board.statusOf(ticket.id())
                            .filter(s -> s != BoardStatus.CANCELLED)
                            .isPresent()
                    && board.cancel(ticket.id())) {
                cancelled.add(ticket.id());
            }
        }
        return List.copyOf(cancelled);
    }
}
