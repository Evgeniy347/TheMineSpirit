package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchPickTests {
    @Test
    void reachTarget_LevelingSchematic_UsesOriginBeforeEntrance() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5))
                .withScheme("wheat_field_1")
                .withLeveling(new PlotLeveling(
                        List.of(new PlotLeveling.Box(10, 60, -5, 10, 60, -5)), List.of(), PlotLeveling.DEFAULT_BLOCK));

        assertEquals(
                new BlockCoord(10, 60, -5),
                DispatchPick.reachTarget(target),
                "До выравнивания проверяется origin, а не вход схемы: первый шаг — clear_area, иначе путь "
                        + "к ещё не построенному входу в яме ложно блокирует поле; от origin доска затем ищет "
                        + "ближайшую безопасную стоянку. При смене reachTarget_LevelingSchematic_UsesOriginBeforeEntrance "
                        + "проверь StagePlans.placed, DispatchPick.standNear и живую выдачу graph:building:wheat_field");
    }

    @Test
    void reachHeight_LevelingSchematic_UsesTopOfOriginColumn() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5))
                .withScheme("wheat_field_1")
                .withLeveling(new PlotLeveling(
                        List.of(
                                new PlotLeveling.Box(10, 60, -5, 10, 70, -5),
                                new PlotLeveling.Box(20, 60, -5, 20, 90, -5)),
                        List.of(),
                        PlotLeveling.DEFAULT_BLOCK));

        assertEquals(
                70,
                DispatchPick.reachHeight(target),
                "Путь к выравниваемой схеме проверяется от верха колонны origin: иначе низкая стоянка "
                        + "снова не пускает духа к верхнему слою холма. При смене "
                        + "reachHeight_LevelingSchematic_UsesTopOfOriginColumn проверь DispatchPick.standNear.");
    }

    @Test
    void reachHeight_LevelingWithoutOriginCut_KeepsOriginHeight() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5))
                .withLeveling(new PlotLeveling(
                        List.of(new PlotLeveling.Box(20, 60, -5, 20, 70, -5)), List.of(), PlotLeveling.DEFAULT_BLOCK));

        assertEquals(
                60,
                DispatchPick.reachHeight(target),
                "Без срезки в колонне origin высота проверки остаётся у origin: иначе чужой холм перемещает "
                        + "цель задания. При смене reachHeight_LevelingWithoutOriginCut_KeepsOriginHeight "
                        + "проверь PlotLeveling.Box и живой wheat_field.");
    }

    @Test
    void reachTarget_SchematicBuilding_UsesEntranceInsteadOfOrigin() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 60, -5)).withScheme("wheat_field_1");

        assertEquals(
                new BlockCoord(17, 61, -3),
                DispatchPick.reachTarget(target),
                "Проверка пути ведёт к входу схемы: иначе угол постройки в рельефе ложно объявляется "
                        + "недостижимым и грядка остаётся без исполнителя. При смене "
                        + "reachTarget_SchematicBuilding_UsesEntranceInsteadOfOrigin проверь StagePlans.placed, "
                        + "Preset.entry и живой wheat_field.");
    }
}
