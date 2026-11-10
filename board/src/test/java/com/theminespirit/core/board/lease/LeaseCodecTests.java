package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseCodecTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @Test
    void saveThenLoad_WholeBook_KeepsEveryField() {
        LeaseBook book = new LeaseBook();
        Lease active = book.open(SPIRIT, "ticket-1", "scaffold", 100);
        book.record(active.id(), LeaseCell.placed(new BlockCoord(1, 65, 2), "minecraft:dirt"));
        book.record(active.id(), LeaseCell.broken(new BlockCoord(2, 65, 2), "minecraft:stone"));
        Lease orphaned = book.open(OTHER, null, "bridge", 200);
        book.record(orphaned.id(), LeaseCell.state(new BlockCoord(3, 65, 2), "open", "closed"));
        book.orphan(orphaned.id());
        Lease done = book.open(OTHER, "ticket-3", "route", 300);
        book.record(done.id(), LeaseCell.placed(new BlockCoord(4, 65, 2)));
        book.commit(done.id());

        LeaseBook loaded = LeaseCodec.decode(LeaseCodec.encode(book));

        assertEquals(
                book.all(),
                loaded.all(),
                "Кодек обязан вернуть ту же книгу: id, владельца, тикет, назначение, "
                        + "статус, срок и каждую клетку с before/after");
        assertEquals(
                book.orphanedCells(),
                loaded.orphanedCells(),
                "После загрузки скан видит те же брошенные клетки в том же порядке отката");
        assertEquals(
                book.owner(new BlockCoord(1, 65, 2)),
                loaded.owner(new BlockCoord(1, 65, 2)),
                "Владелец действующей клетки восстанавливается по id");
    }

    @Test
    void decode_DuplicateActiveCell_Rejected() {
        LeaseCodec.LeaseRow first = new LeaseCodec.LeaseRow(
                "a",
                SPIRIT,
                null,
                "scaffold",
                LeaseStatus.ACTIVE,
                1,
                List.of(new LeaseCodec.CellRow(0, 64, 0, LeaseKind.PLACED, null, null)));
        LeaseCodec.LeaseRow second = new LeaseCodec.LeaseRow(
                "b",
                OTHER,
                null,
                "bridge",
                LeaseStatus.ACTIVE,
                2,
                List.of(new LeaseCodec.CellRow(0, 64, 0, LeaseKind.PLACED, null, null)));

        assertThrows(
                IllegalArgumentException.class,
                () -> LeaseCodec.decode(List.of(first, second)),
                "Повреждённое сохранение с одной клеткой в двух арендах не загружается молча: откат сломал бы чужую правку");
    }

    @Test
    void encode_EmptyBook_IsEmptyList() {
        assertEquals(List.of(), LeaseCodec.encode(new LeaseBook()), "Пустая книга кодируется пустым списком");
        assertEquals(List.of(), LeaseCodec.decode(List.of()).all(), "Пустой список загружается пустой книгой");
    }
}
