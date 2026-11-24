package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConstructionMarksTests {
    private static final Set<ConstructionBatches.ChunkCell> HOME = Set.of(new ConstructionBatches.ChunkCell(0, 0));

    private static ConstructionMarks.Marks marks(RepairAudit.Report report) {
        return ConstructionMarks.in(report, HOME);
    }

    @Test
    void marks_ClearFix_GoesToDemolish() {
        BlockCoord cell = new BlockCoord(2, 64, 2);
        RepairAudit.Report report = new RepairAudit.Report(
                3,
                1,
                false,
                List.of(new RepairAudit.Fix(cell, RepairAudit.Action.CLEAR, "minecraft:air", "minecraft:dirt")),
                0);

        ConstructionMarks.Marks marks = marks(report);

        assertEquals(
                Set.of(cell),
                marks.demolish(),
                "Правка CLEAR означает лишний блок в мире: она обязана попасть в набор на снос, иначе красный контур не покажет мусор; при смене классификации правок в ConstructionMarks проверь, что CLEAR остаётся в demolish");
        assertTrue(
                marks.place().isEmpty(),
                "Правка CLEAR не может попасть в набор к постановке: иначе оранжевый контур соврёт про установку и дух поставит блок в занятую клетку; при смене классификации правок в ConstructionMarks проверь, что place остаётся пустым");
    }

    @Test
    void marks_PlaceOverForeignBlock_GoesToDemolish() {
        BlockCoord cell = new BlockCoord(1, 64, 1);
        RepairAudit.Report report = new RepairAudit.Report(
                3,
                1,
                false,
                List.of(new RepairAudit.Fix(cell, RepairAudit.Action.PLACE, "minecraft:stone", "minecraft:dirt")),
                0);

        ConstructionMarks.Marks marks = marks(report);

        assertEquals(
                Set.of(cell),
                marks.demolish(),
                "PLACE поверх чужого блока — это замена, а не установка в воздух: клетка обязана попасть в набор на снос, иначе красный контур не покажет неверный блок; при смене классификации правок в ConstructionMarks проверь ветку PLACE с непустым found");
        assertTrue(
                marks.place().isEmpty(),
                "PLACE поверх чужого блока не может считаться отсутствующим блоком: иначе оранжевый контур соврёт и дух поставит блок поверх чужого; при смене классификации правок в ConstructionMarks проверь ветку PLACE с непустым found");
    }

    @Test
    void marks_PlaceIntoAir_GoesToPlace() {
        BlockCoord cell = new BlockCoord(1, 65, 1);
        RepairAudit.Report report = new RepairAudit.Report(
                3,
                1,
                false,
                List.of(new RepairAudit.Fix(cell, RepairAudit.Action.PLACE, "minecraft:oak_planks", "minecraft:air")),
                0);

        ConstructionMarks.Marks marks = marks(report);

        assertEquals(
                Set.of(cell),
                marks.place(),
                "PLACE в воздух — это отсутствующий блок схемы: он обязан попасть в набор к постановке, иначе оранжевый контур не покажет недостающее; при смене классификации правок в ConstructionMarks проверь ветку PLACE с air в found");
        assertTrue(
                marks.demolish().isEmpty(),
                "PLACE в воздух не может считаться блоком на снос: иначе красный контур покажет пустую клетку и дух снесёт воздух; при смене классификации правок в ConstructionMarks проверь ветку PLACE с air в found");
    }

    @Test
    void marks_FixOutsideBatchChunks_IsSkipped() {
        BlockCoord inside = new BlockCoord(15, 64, 0);
        BlockCoord outside = new BlockCoord(16, 64, 0);
        RepairAudit.Report report = new RepairAudit.Report(
                4,
                2,
                false,
                List.of(
                        new RepairAudit.Fix(inside, RepairAudit.Action.PLACE, "minecraft:stone", "minecraft:air"),
                        new RepairAudit.Fix(outside, RepairAudit.Action.PLACE, "minecraft:stone", "minecraft:air")),
                0);

        ConstructionMarks.Marks marks = marks(report);

        assertEquals(
                Set.of(inside),
                marks.place(),
                "Клетка из чанка пачки должна остаться, а клетка соседнего чанка — отсечься: иначе пачки наложатся, и одна и та же правка придёт дважды; при смене фильтра чанков в ConstructionMarks проверь границу 16 блоков");
    }

    @Test
    void marks_RebuildReport_HasNoMarks() {
        BlockCoord cell = new BlockCoord(2, 64, 2);
        RepairAudit.Report report = new RepairAudit.Report(
                5,
                5,
                true,
                List.of(new RepairAudit.Fix(cell, RepairAudit.Action.CLEAR, "minecraft:air", "minecraft:dirt")),
                0);

        ConstructionMarks.Marks marks = marks(report);

        assertTrue(
                marks.demolish().isEmpty() && marks.place().isEmpty(),
                "Отчёт с rebuild означает полную перестройку: адресные правки по клеткам не имеют смысла, иначе контуры покажут ложные цели; при смене обработки rebuild в ConstructionMarks проверь, что оба набора пустые");
    }
}
