package com.theminespirit.core.board.lease;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class LeaseSupervisor {
    public record SpiritState(boolean suspended, @Nullable Set<String> tickets) {
        public SpiritState {
            tickets = tickets == null ? null : Set.copyOf(tickets);
        }
    }

    private LeaseSupervisor() {}

    public static List<Lease> due(
            LeaseBook book,
            Map<UUID, SpiritState> spirits,
            Map<String, Long> suspendedSince,
            long now,
            long suspendTimeoutTicks) {
        Objects.requireNonNull(book, "Книга аренд не может быть null: проверять нечего");
        Objects.requireNonNull(spirits, "Состояние духов не может быть null: владельца аренды не с чем сверить");
        Objects.requireNonNull(suspendedSince, "Память пауз не может быть null: срок брошенной аренды не отсчитать");
        if (now < 0) {
            throw new IllegalArgumentException("Тик " + now + " отрицателен: время идёт вперёд");
        }
        if (suspendTimeoutTicks < 0) {
            throw new IllegalArgumentException("Предел паузы " + suspendTimeoutTicks
                    + " отрицателен: срок брошенной аренды не может быть отрицательным");
        }
        Map<String, UUID> ticketOwners = ticketOwners(spirits);
        List<Lease> due = new ArrayList<>();
        for (Lease lease : book.active()) {
            UUID owner = lease.spirit();
            if (owner == null) {
                due.add(lease);
                continue;
            }
            boolean lost = false;
            SpiritState state = spirits.get(owner);
            if (state == null) {
                lost = true;
            } else {
                if (lease.ticket() != null && !owner.equals(ticketOwners.get(lease.ticket()))) {
                    lost = true;
                }
                if (!lost && state.suspended()) {
                    long first = suspendedSince.getOrDefault(lease.id(), now);
                    suspendedSince.putIfAbsent(lease.id(), now);
                    lost = now - first >= suspendTimeoutTicks;
                } else {
                    suspendedSince.remove(lease.id());
                }
            }
            if (lost) {
                due.add(lease);
            }
        }
        suspendedSince
                .keySet()
                .removeIf(id -> book.find(id)
                        .filter(lease -> lease.status() == LeaseStatus.ACTIVE)
                        .isEmpty());
        return List.copyOf(due);
    }

    private static Map<String, UUID> ticketOwners(Map<UUID, SpiritState> spirits) {
        Map<String, UUID> owners = new HashMap<>();
        for (Map.Entry<UUID, SpiritState> entry : spirits.entrySet()) {
            Set<String> tickets = entry.getValue().tickets();
            if (tickets == null) {
                continue;
            }
            for (String ticket : tickets) {
                UUID before = owners.put(ticket, entry.getKey());
                if (before != null && !before.equals(entry.getKey())) {
                    throw new IllegalStateException("Тикет '" + ticket + "' закреплён за двумя духами ('" + before
                            + "' и '" + entry.getKey() + "'): доска не может вести одну работу вдвоём");
                }
            }
        }
        return owners;
    }
}
