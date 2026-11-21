package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildSupplyPickTests {
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);

    @Test
    void choose_FirstNeededChestUnreachable_TakesNextReachableWithMaterial() {
        BlockCoord far = new BlockCoord(11, 64, 10);
        BlockCoord near = new BlockCoord(12, 64, 10);

        BlockCoord chosen = BuildSupplyPick.choose(
                FEET,
                List.of(
                        new BuildSupplyPick.Candidate(new BlockCoord(2, 64, 2), near, true),
                        new BuildSupplyPick.Candidate(new BlockCoord(3, 64, 3), far, true)));

        assertEquals(
                near,
                chosen,
                "Из достижимых сундуков берут ближайший с нужным материалом: стоянка недостижимого сундука не "
                        + "становится началом плана, иначе аудит стройки пишет «стоянка плана недостижима»"
                        + " При смене choose_FirstNeededChestUnreachable_TakesNextReachableWithMaterial проверь это утверждение.");
    }

    @Test
    void choose_NoReachableChestWithMaterial_TakesNearestReachableWarehouse() {
        BlockCoord warehouse = new BlockCoord(5, 64, 4);

        BlockCoord chosen = BuildSupplyPick.choose(
                FEET,
                List.of(
                        new BuildSupplyPick.Candidate(new BlockCoord(4, 64, 4), warehouse, false),
                        new BuildSupplyPick.Candidate(new BlockCoord(6, 64, 6), new BlockCoord(7, 64, 6), false)));

        assertEquals(
                warehouse,
                chosen,
                "Если материала нет ни в одном достижимом сундуке, план начинается у ближайшего достижимого "
                        + "склада: материал поднесёт граф, а не выдуманная стоянка"
                        + " Иначе тест choose_NoReachableChestWithMaterial_TakesNearestReachableWarehouse перестанет ловить эту поломку. При смене choose_NoReachableChestWithMaterial_TakesNearestReachableWarehouse проверь это утверждение.");
    }

    @Test
    void choose_NoReachableChest_StaysAtFeet() {
        assertEquals(
                FEET,
                BuildSupplyPick.choose(
                        FEET, List.of(new BuildSupplyPick.Candidate(new BlockCoord(1, 64, 1), FEET, false))),
                "Достижимых сундуков нет — план начинается с места духа, а не с чужой стоянки без пути"
                        + " Иначе тест choose_NoReachableChest_StaysAtFeet перестанет ловить эту поломку. При смене choose_NoReachableChest_StaysAtFeet проверь это утверждение.");
    }

    @Test
    void choose_NoCandidates_StaysAtFeet() {
        assertEquals(
                FEET,
                BuildSupplyPick.choose(FEET, List.of()),
                "Сундуков поселения нет — снабжать нечего, план начинается с места духа"
                        + " Иначе тест choose_NoCandidates_StaysAtFeet перестанет ловить эту поломку. При смене choose_NoCandidates_StaysAtFeet проверь это утверждение.");
    }
}
