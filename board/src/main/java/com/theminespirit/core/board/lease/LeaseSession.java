package com.theminespirit.core.board.lease;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class LeaseSession {
    private final LeaseBook book;
    private final String id;

    private LeaseSession(LeaseBook book, String id) {
        this.book = book;
        this.id = id;
    }

    public static LeaseSession open(
            LeaseBook book, @Nullable UUID spirit, @Nullable String ticket, String purpose, long since) {
        Objects.requireNonNull(book, "Книга аренд не может быть null: открывать аренду негде");
        return new LeaseSession(book, book.open(spirit, ticket, purpose, since).id());
    }

    public static LeaseSession attach(LeaseBook book, String id) {
        Objects.requireNonNull(book, "Книга аренд не может быть null: продолжать аренду негде");
        book.require(id);
        return new LeaseSession(book, id);
    }

    public String id() {
        return id;
    }

    public String purpose() {
        return book.require(id).purpose();
    }

    public LeaseStatus status() {
        return book.require(id).status();
    }

    public List<LeaseCell> cells() {
        return book.require(id).cells();
    }

    public boolean contains(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: ищем её в аренде");
        for (LeaseCell recorded : cells()) {
            if (recorded.pos().equals(cell)) {
                return true;
            }
        }
        return false;
    }

    public boolean placed(BlockCoord cell) {
        return record(LeaseCell.placed(cell));
    }

    public boolean placed(BlockCoord cell, String block) {
        return record(LeaseCell.placed(cell, block));
    }

    public boolean broken(BlockCoord cell, String before) {
        return record(LeaseCell.broken(cell, before));
    }

    public boolean state(BlockCoord cell, String before, String after) {
        return record(LeaseCell.state(cell, before, after));
    }

    public void forget(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: забывать нечего");
        if (!contains(cell)) {
            throw new IllegalArgumentException("Клетка " + cell + " не записана в аренде '" + id
                    + "': забывают только то, что аренда помнит, иначе откат потеряет правку");
        }
        book.forget(id, cell);
    }

    public void forget(Collection<BlockCoord> cells) {
        Objects.requireNonNull(cells, "Клетки не могут быть null: забывать нечего");
        cells.forEach(this::forget);
    }

    public void commit() {
        if (book.require(id).status() == LeaseStatus.ACTIVE) {
            book.commit(id);
        }
    }

    public void orphan() {
        if (book.require(id).status() != LeaseStatus.ACTIVE) {
            return;
        }
        if (book.require(id).cells().isEmpty()) {
            book.commit(id);
            return;
        }
        book.orphan(id);
    }

    private boolean record(LeaseCell cell) {
        Objects.requireNonNull(cell, "Клетка аренды не может быть null: записывать нечего");
        if (contains(cell.pos())) {
            return false;
        }
        if (book.owner(cell.pos()).isPresent()) {
            return false;
        }
        book.record(id, cell);
        return true;
    }
}
