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
                        + "к ещё не построенному входу в яме ложно блокирует поле. При смене "
                        + "reachTarget_LevelingSchematic_UsesOriginBeforeEntrance проверь StagePlans.placed "
                        + "и живую выдачу graph:building:wheat_field");
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
