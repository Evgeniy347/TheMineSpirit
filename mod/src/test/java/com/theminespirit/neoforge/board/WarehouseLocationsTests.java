package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WarehouseLocationsTests {
    @Test
    void chestMatches_SingleChestInDoubleChestCell_DoesNotProtectTemporaryChest() {
        assertFalse(
                WarehouseLocations.chestMatches(
                        "chest", Map.of("facing", "east", "type", "left"), Map.of("facing", "east", "type", "single")),
                "Одиночный сундук в будущей клетке двойного складского сундука не является постройкой плана: "
                        + "иначе, если защитить его только по координате, пустой временный сундук навсегда займёт "
                        + "клетку и склад не построится. При смене WarehouseLocations.planned проверь "
                        + "ChestDemolition.removable и живые сундуки внутри storage_3.");
    }

    @Test
    void chestMatches_MatchingDoubleChestState_ProtectsPlannedChest() {
        assertTrue(
                WarehouseLocations.chestMatches(
                        "chest", Map.of("facing", "east", "type", "left"), Map.of("facing", "east", "type", "left")),
                "Сундук с состоянием схемы защищён от сноса: иначе пустой готовый складской сундук будут "
                        + "ломать как временный и склад потеряет вместимость. При смене WarehouseLocations.planned "
                        + "проверь ChestDemolition.removable, StoragePlanWatch.space и обе половины двойного сундука.");
    }
}
