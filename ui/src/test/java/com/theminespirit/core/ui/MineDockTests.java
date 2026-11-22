package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MineDockTests {
    @Test
    void counts_MixedCells_GroupsCellsByKind() {
        MineSnapshot snapshot = new MineSnapshot(
                new TownMapView.Bounds(0, 0, 29, 19),
                12,
                75,
                68,
                List.of(
                        new MineSnapshot.Cell(0, 0, MineSnapshot.Kind.PASSAGE),
                        new MineSnapshot.Cell(1, 0, MineSnapshot.Kind.PASSAGE),
                        new MineSnapshot.Cell(2, 0, MineSnapshot.Kind.ORE),
                        new MineSnapshot.Cell(3, 0, MineSnapshot.Kind.LAVA)),
                List.of(),
                List.of());
        var counts = MineDock.counts(snapshot);
        assertEquals(
                2,
                counts.get(MineSnapshot.Kind.PASSAGE),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене counts_MixedCells_GroupsCellsByKind проверь это утверждение.");
        assertEquals(
                1,
                counts.get(MineSnapshot.Kind.ORE),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене counts_MixedCells_GroupsCellsByKind проверь это утверждение.");
        assertEquals(
                1,
                counts.get(MineSnapshot.Kind.LAVA),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене counts_MixedCells_GroupsCellsByKind проверь это утверждение.");
        assertEquals(
                0,
                counts.get(MineSnapshot.Kind.WATER),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене counts_MixedCells_GroupsCellsByKind проверь это утверждение.");
    }
}
