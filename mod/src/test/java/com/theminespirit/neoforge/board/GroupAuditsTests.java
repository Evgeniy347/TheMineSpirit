package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.audit.Access;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.SourceRef;
import com.theminespirit.core.board.audit.Wanted;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroupAuditsTests {
    private static final BlockCoord CELL = new BlockCoord(0, 64, 0);

    @Test
    void groupAudits_GroupItems_ConvertToLegacyReport() {
        Group group = Group.pending(
                Group.Kind.BUILDING,
                "hut",
                new ChunkCell(0, 0),
                List.of(
                        DiffItem.breakAt(CELL, "minecraft:oak_fence", SourceRef.building("hut"), Access.SPIRIT),
                        DiffItem.place(
                                new BlockCoord(1, 64, 0),
                                "minecraft:air",
                                Wanted.block("minecraft:stone"),
                                SourceRef.building("hut"),
                                Access.SPIRIT)),
                2);

        BuildingAuditInputs.Audit audit = GroupAudits.audit(group);
        RepairAudit.Report report = GroupAudits.report(group);

        assertEquals(
                2,
                audit.total(),
                "Знаменатель отчёта берётся из группы: иначе процент готовности"
                        + " постройки посчитается по числу дефектов. При смене адаптера перепроверь GroupAudits.audit.");
        assertEquals(
                Map.of("minecraft:stone", 1),
                audit.missing(),
                "Материал установки попадает в недостачу с точным id: иначе стратег не закажет камень,"
                        + " и дух придёт с пустыми руками. При смене адаптера перепроверь block.");
        assertEquals(
                Map.of("minecraft:oak_fence", 1),
                audit.extra(),
                "Сломанный блок попадает в лишнее с точным id: иначе старый отчёт не увидит препятствие"
                        + " и не даст инструмент. При смене адаптера перепроверь BREAK.");
        assertEquals(
                2,
                report.fixes().size(),
                "Каждый DiffItem становится Fix без повторного чтения"
                        + " мира: иначе старый потребитель снова запустит аудит. При смене адаптера перепроверь report.");
        assertEquals(
                RepairAudit.Action.CLEAR,
                report.fixes().get(0).action(),
                "BREAK остаётся CLEAR"
                        + " в legacy-контракте: иначе потребитель попытается поставить воздух. При смене маппинга"
                        + " перепроверь GroupAudits.report.");
    }

    @Test
    void groupAudits_ChunkGroups_AggregateTotalsAndFixes() {
        Group first = Group.pending(
                Group.Kind.BUILDING,
                "hut",
                new ChunkCell(0, 0),
                List.of(DiffItem.place(
                        CELL,
                        "minecraft:air",
                        Wanted.block("minecraft:stone"),
                        SourceRef.building("hut"),
                        Access.SPIRIT)),
                1);
        Group second = Group.pending(
                Group.Kind.BUILDING,
                "hut",
                new ChunkCell(1, 0),
                List.of(DiffItem.place(
                        new BlockCoord(16, 64, 0),
                        "minecraft:air",
                        Wanted.block("minecraft:stone"),
                        SourceRef.building("hut"),
                        Access.SPIRIT)),
                1);

        BuildingAuditInputs.Audit audit = GroupAudits.audit(List.of(first, second));
        RepairAudit.Report report = GroupAudits.report(List.of(first, second));

        assertEquals(
                2,
                audit.total(),
                "Сводка склада обязана суммировать все чанковые группы: иначе процент готовности обрежется"
                        + " на границе чанка. При смене дробления перепроверь GroupAudits.audit(List).");
        assertEquals(
                Map.of("minecraft:stone", 2),
                audit.missing(),
                "Материалы чанковых групп складываются один раз: иначе стратег закажет только часть кладки"
                        + " и дух остановится на NO_MATERIAL. При смене адаптера перепроверь Audit.plus.");
        assertEquals(
                2,
                report.fixes().size(),
                "Legacy-отчёт несёт все клетки группы: иначе клиент потеряет работу соседнего чанка."
                        + " При смене дробления перепроверь GroupAudits.report(List).");
    }
}
