package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                "Резервный список без плана фильтрует реестр по границам поселения: иначе дальние координаты "
                        + "раздуют выборку, а повторный обход всех block entity снова задержит пересборку. "
                        + "При смене registered_InsideAndOutsideArea_KeepsOnlySettlementChests проверь "
                        + "StockReader.collect, ChestSnapshots.positions и SettlementChestWatch.");
    }

    @Test
    void positions_WithArea_ReadsLoadedChunksWithoutStaleRegistry() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/ChestSnapshots.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("returnarea.isPresent()?inArea(level,area.orElseThrow()):registered(level);"),
                "С планом поселения горячий снимок обязан брать только загруженные block entity: иначе сотни "
                        + "старых адресов реестра снова съедают секунды в countable. При смене "
                        + "positions_WithArea_ReadsLoadedChunksWithoutStaleRegistry проверь StockReader.collect "
                        + "и холодный board.stock.countable.");
        assertFalse(
                compact.contains("newLinkedHashSet<>(registered(level))"),
                "Реестр не должен добавляться к загруженным контейнерам при наличии плана: иначе мёртвые "
                        + "координаты возвращают холодный фриз. При смене "
                        + "positions_WithArea_ReadsLoadedChunksWithoutStaleRegistry проверь ChestSnapshots.positions.");
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
