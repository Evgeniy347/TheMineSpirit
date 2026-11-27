package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record Group(
        Kind kind,
        String subject,
        @Nullable ChunkCell chunk,
        List<DiffItem> items,
        int total,
        Status status,
        int revision,
        int unknown,
        String reason) {
    public enum Kind {
        BUILDING,
        STREET,
        WALL,
        GROUND,
        MINE
    }

    public enum Status {
        PENDING,
        ISSUED,
        DONE,
        QUARANTINED,
        BLOCKED
    }

    private static final List<Kind> CHUNK_KINDS = List.of(Kind.STREET, Kind.WALL, Kind.GROUND);

    public Group {
        Objects.requireNonNull(kind, "Вид группы не может быть null: по нему выбирают маршрут и вес");
        Objects.requireNonNull(subject, "Предмет группы не может быть null: пустой — пустая строка");
        items = List.copyOf(
                Objects.requireNonNull(items, "Предметы группы не могут быть null: пустая — пустой список"));
        Objects.requireNonNull(status, "Статус группы не может быть null: иначе билет уйдёт в никуда");
        Objects.requireNonNull(reason, "Причина статуса не может быть null: отсутствие — пустая строка");
        if (revision < 0 || unknown < 0 || total < 0 || total < items.size()) {
            throw new IllegalArgumentException("Группа " + subject + ": предметов " + items.size() + " из плана "
                    + total + ", неизвестных " + unknown + ", ревизия " + revision + " — чисел меньше нуля не бывает, "
                    + "выданного больше плана тоже, иначе сводка стратегу врёт");
        }
        if (CHUNK_KINDS.contains(kind) && chunk == null) {
            throw new IllegalArgumentException("Группа " + kind + " без чанка: улицы, стены и землю дробят по чанкам, "
                    + "иначе один дух пойдёт через весь регион и группа не будет готова никогда");
        }
        if (!CHUNK_KINDS.contains(kind) && chunk != null) {
            throw new IllegalArgumentException("Группа " + kind + " привязана к чанку " + chunk
                    + ": постройку и шахту правят по участку, привязка к чанку разорвала бы группу на части");
        }
    }

    public static Group pending(Kind kind, String subject, @Nullable ChunkCell chunk, List<DiffItem> items, int total) {
        return new Group(kind, subject, chunk, items, total, Status.PENDING, 0, 0, "");
    }

    public String key() {
        String name = kind.name().toLowerCase(Locale.ROOT);
        String base = subject.isBlank() ? "group:" + name : "group:" + name + ":" + subject;
        return chunk == null ? base : base + "@" + chunk.x() + "," + chunk.z();
    }

    public int pending() {
        return items.size();
    }

    public boolean complete() {
        return items.isEmpty() && unknown == 0;
    }

    public int pendingPercent() {
        return total <= 0 ? 0 : (int) Math.round(items.size() * 100.0 / total);
    }

    public List<DiffItem> ordered() {
        return items.stream().sorted(DiffItem.WORK_ORDER).toList();
    }

    public Group withStatus(Status next, String why) {
        return new Group(kind, subject, chunk, items, total, next, revision, unknown, why);
    }

    public Group issued() {
        return withStatus(Status.ISSUED, "");
    }

    public Group quarantined() {
        return new Group(kind, subject, chunk, items, total, Status.QUARANTINED, revision + 1, unknown, "промах кэша");
    }

    public Group rebuilt(List<DiffItem> next, int plan, int unknownCells) {
        Status nextStatus = next.isEmpty() && unknownCells == 0 ? Status.DONE : Status.PENDING;
        return new Group(kind, subject, chunk, next, plan, nextStatus, revision, unknownCells, "");
    }
}
