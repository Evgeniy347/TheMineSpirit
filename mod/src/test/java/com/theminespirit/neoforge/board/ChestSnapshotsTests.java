package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChestSnapshotsTests {
    @Test
    void registered_InsideAndOutsideArea_KeepsOnlySettlementChests() {
        BlockCoord inside = new BlockCoord(2, 64, 3);
        BlockCoord outside = new BlockCoord(30, 64, 30);

        assertEquals(
                List.of(inside),
                ChestSnapshots.registered(List.of(inside, outside), Optional.of(ColumnMask.rect(0, 0, 10, 10))),
                "Горячий снимок запасов берёт из реестра только сундуки поселения: иначе дальние координаты "
                        + "раздувают выборку, а повторный обход всех block entity снова задержит пересборку. "
                        + "При смене registered_InsideAndOutsideArea_KeepsOnlySettlementChests проверь "
                        + "StockReader.collect, ChestSnapshots.positions и SettlementChestWatch.");
    }

    @Test
    void registered_NoArea_KeepsAllKnownChests() {
        List<BlockCoord> known = List.of(new BlockCoord(2, 64, 3), new BlockCoord(30, 64, 30));

        assertEquals(
                known,
                ChestSnapshots.registered(known, Optional.empty()),
                "Без границы поселения реестр отдаёт все известные сундуки: иначе первый запуск без плана "
                        + "останется без запасов и граф не сможет поставить работу. При смене "
                        + "registered_NoArea_KeepsAllKnownChests проверь StockReader.collect и ChestSnapshots.area.");
    }
}
