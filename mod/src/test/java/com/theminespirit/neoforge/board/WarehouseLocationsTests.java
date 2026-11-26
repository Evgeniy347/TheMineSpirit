package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WarehouseLocationsTests {
    @Test
    void containerMatches_SingleChestInDoubleChestCell_DoesNotProtectTemporaryChest() {
        assertFalse(
                WarehouseLocations.containerMatches(
                        "chest", Map.of("facing", "east", "type", "left"), Map.of("facing", "east", "type", "single")),
                "Одиночный сундук в будущей клетке двойного складского сундука не является постройкой плана: "
                        + "иначе, если защитить его только по координате, пустой временный сундук навсегда займёт "
                        + "клетку и склад не построится. При смене WarehouseLocations.planned проверь "
                        + "ChestDemolition.removable и живые сундуки внутри storage_3.");
    }

    @Test
    void containerMatches_MatchingHopperState_ProtectsPlannedContainer() {
        assertTrue(
                WarehouseLocations.containerMatches("hopper", Map.of("facing", "north"), Map.of("facing", "north")),
                "Воронка со состоянием схемы защищена от сноса: иначе запланированная логистика будет "
                        + "разобрана как временный контейнер. При смене containerMatches_MatchingHopperState_ProtectsPlannedContainer "
                        + "проверь WarehouseLocations.planned и ChestDemolition.removable.");
    }

    @Test
    void containerMatches_NonContainerBlock_IsNotProtectedAsContainer() {
        assertFalse(
                WarehouseLocations.containerMatches("stone", Map.of(), Map.of()),
                "Обычный блок не получает защиту контейнера: иначе снос лишнего камня перестанет работать. "
                        + "При смене containerMatches_NonContainerBlock_IsNotProtectedAsContainer проверь "
                        + "FloatingBlocks.containerPath.");
    }

    @Test
    void containerMatches_MatchingDoubleChestState_ProtectsPlannedChest() {
        assertTrue(
                WarehouseLocations.containerMatches(
                        "chest", Map.of("facing", "east", "type", "left"), Map.of("facing", "east", "type", "left")),
                "Сундук с состоянием схемы защищён от сноса: иначе пустой готовый складской сундук будут "
                        + "ломать как временный и склад потеряет вместимость. При смене WarehouseLocations.planned "
                        + "проверь ChestDemolition.removable, StoragePlanWatch.space и обе половины двойного сундука.");
    }
}
