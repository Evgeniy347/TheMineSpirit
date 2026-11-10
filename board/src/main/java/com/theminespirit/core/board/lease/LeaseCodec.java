package com.theminespirit.core.board.lease;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class LeaseCodec {
    private LeaseCodec() {}

    public record CellRow(
            int x,
            int y,
            int z,
            LeaseKind kind,
            @Nullable String before,
            @Nullable String after) {
        public CellRow {
            Objects.requireNonNull(kind, "Вид клетки аренды не может быть null: откат без него невозможен");
        }

        public LeaseCell toCell() {
            return new LeaseCell(new BlockCoord(x, y, z), kind, before, after);
        }

        public static CellRow of(LeaseCell cell) {
            return new CellRow(
                    cell.pos().x(), cell.pos().y(), cell.pos().z(), cell.kind(), cell.before(), cell.after());
        }
    }

    public record LeaseRow(
            String id,
            @Nullable UUID spirit,
            @Nullable String ticket,
            String purpose,
            LeaseStatus status,
            long since,
            List<CellRow> cells) {
        public LeaseRow {
            Objects.requireNonNull(status, "Статус аренды не может быть null: от него зависит скан");
            cells = List.copyOf(Objects.requireNonNull(cells, "Клетки аренды не могут быть null"));
        }

        public Lease toLease() {
            return new Lease(
                    id,
                    spirit,
                    ticket,
                    purpose,
                    status,
                    since,
                    cells.stream().map(CellRow::toCell).toList());
        }

        public static LeaseRow of(Lease lease) {
            return new LeaseRow(
                    lease.id(),
                    lease.spirit(),
                    lease.ticket(),
                    lease.purpose(),
                    lease.status(),
                    lease.since(),
                    lease.cells().stream().map(CellRow::of).toList());
        }
    }

    public static List<LeaseRow> encode(LeaseBook book) {
        Objects.requireNonNull(book, "Книга аренд не может быть null: сохранять нечего");
        return book.all().stream().map(LeaseRow::of).toList();
    }

    public static LeaseBook decode(List<LeaseRow> rows) {
        Objects.requireNonNull(rows, "Строки аренд не могут быть null: пустую книгу задают пустым списком");
        LeaseBook book = new LeaseBook();
        for (LeaseRow row : rows) {
            Objects.requireNonNull(row, "Строка аренды не может быть null: сохранение повреждено");
            book.restore(row.toLease());
        }
        return book;
    }
}
