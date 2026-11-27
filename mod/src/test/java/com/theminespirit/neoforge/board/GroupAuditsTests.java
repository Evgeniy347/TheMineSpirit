package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                null,
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
}
