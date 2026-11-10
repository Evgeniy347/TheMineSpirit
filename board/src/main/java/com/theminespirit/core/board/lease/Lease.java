package com.theminespirit.core.board.lease;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record Lease(
        String id,
        @Nullable UUID spirit,
        @Nullable String ticket,
        String purpose,
        LeaseStatus status,
        long since,
        List<LeaseCell> cells) {
    public Lease {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id аренды пуст: по нему брошенную правку находит граф");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("Назначение аренды пусто: по нему граф понимает, что откатывать");
        }
        Objects.requireNonNull(status, "Статус аренды не может быть null: от него зависит, трогает ли скан клетки");
        if (since < 0) {
            throw new IllegalArgumentException("Момент открытия аренды " + since + " отрицателен: время идёт вперёд");
        }
        if (ticket != null && ticket.isBlank()) {
            throw new IllegalArgumentException("Тикет аренды задан пустой строкой: отсутствие задают null");
        }
        List<LeaseCell> copy = List.copyOf(Objects.requireNonNull(cells, "Клетки аренды не могут быть null"));
        Set<BlockCoord> seen = new HashSet<>();
        for (LeaseCell cell : copy) {
            if (!seen.add(cell.pos())) {
                throw new IllegalArgumentException(
                        "Клетка " + cell.pos() + " записана в аренде '" + id + "' дважды: откат сломал бы её два раза");
            }
        }
        cells = copy;
    }

    public static Lease opened(String id, @Nullable UUID spirit, @Nullable String ticket, String purpose, long since) {
        return new Lease(id, spirit, ticket, purpose, LeaseStatus.ACTIVE, since, List.of());
    }

    public Lease withStatus(LeaseStatus next) {
        return new Lease(id, spirit, ticket, purpose, next, since, cells);
    }

    public Lease withCells(List<LeaseCell> next) {
        return new Lease(id, spirit, ticket, purpose, status, since, next);
    }

    public List<LeaseCell> rollbackOrder() {
        List<LeaseCell> reversed = new ArrayList<>(cells);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    public boolean ownedBy(UUID owner) {
        return spirit != null && spirit.equals(owner);
    }
}
