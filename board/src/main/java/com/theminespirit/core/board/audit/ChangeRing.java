package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ChangeRing {
    public static final int CAPACITY = 64;

    public record Change(
            long tick,
            BlockCoord cell,
            String before,
            String after,
            Actor actor,
            String basis,
            String ticket,
            String group) {
        public Change {
            Objects.requireNonNull(cell, "Клетка изменения не может быть null: без неё журнал слепой");
            Objects.requireNonNull(before, "Прошлый блок не может быть null: пустота — air");
            Objects.requireNonNull(after, "Новый блок не может быть null: пустота — air");
            Objects.requireNonNull(actor, "Виновник не может быть null: иначе инцидент не разобрать");
            Objects.requireNonNull(basis, "Основание не может быть null: отсутствие — пустая строка");
            Objects.requireNonNull(ticket, "Билет не может быть null: без билета — пустая строка");
            Objects.requireNonNull(group, "Группа не может быть null: без группы — пустая строка");
        }

        public String text() {
            return "тик " + tick + ": " + cell + " " + before + "→" + after + ", " + actor
                    + (basis.isBlank() ? "" : ", основание " + basis)
                    + (ticket.isBlank() ? "" : ", билет " + ticket)
                    + (group.isBlank() ? "" : ", группа " + group);
        }
    }

    public enum Actor {
        SPIRIT,
        PLAYER,
        EXPLOSION,
        NATURAL,
        UNKNOWN
    }

    private final Change[] ring;
    private int next;
    private int size;

    public ChangeRing() {
        this(CAPACITY);
    }

    public ChangeRing(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Журнал чанка размером " + capacity + ": без истории промах кэша " + "не классифицировать");
        }
        ring = new Change[capacity];
    }

    public void add(Change change) {
        ring[next] = Objects.requireNonNull(change, "Изменение не может быть null: пустая запись собьёт разбор");
        next = (next + 1) % ring.length;
        size = Math.min(size + 1, ring.length);
    }

    public @Nullable Change last() {
        if (size == 0) {
            return null;
        }
        return ring[(next - 1 + ring.length) % ring.length];
    }

    public @Nullable Change lastAt(BlockCoord cell) {
        Change found = null;
        for (Change change : all()) {
            if (change.cell().equals(cell)) {
                found = change;
            }
        }
        return found;
    }

    public boolean seenSince(BlockCoord cell, long tick) {
        Change change = lastAt(cell);
        return change != null && change.tick() >= tick;
    }

    public int size() {
        return size;
    }

    public List<Change> all() {
        List<Change> out = new ArrayList<>(size);
        for (int offset = 0; offset < size; offset++) {
            out.add(ring[(next - size + offset + ring.length * 2) % ring.length]);
        }
        return List.copyOf(out);
    }
}
