package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class TownAuditExecutionTests {
    private static final BlockCoord CELL = new BlockCoord(0, 64, 0);

    @Test
    void auditFactCheck_WantedAlreadyStands_ReportsAlreadyDone() {
        DiffItem item = DiffItem.place(
                CELL, "minecraft:air", Wanted.block("minecraft:stone"), SourceRef.building("hut"), Access.SPIRIT);

        assertEquals(
                AuditFactCheck.Decision.ALREADY_DONE,
                AuditFactCheck.of(item, "minecraft:stone"),
                "Уже стоящий нужный блок — инцидент ALREADY_DONE: иначе дух получит задание на"
                        + " несуществующую работу. При смене правила перепроверь AuditFactCheck.of.");
    }

    @Test
    void auditFactCheck_ForeignFact_ReportsForeignBlock() {
        DiffItem item = DiffItem.place(
                CELL, "minecraft:air", Wanted.block("minecraft:stone"), SourceRef.building("hut"), Access.SPIRIT);

        assertEquals(
                AuditFactCheck.Decision.FOREIGN_BLOCK,
                AuditFactCheck.of(item, "minecraft:oak_planks"),
                "Чужой факт вместо снимка — FOREIGN_BLOCK: иначе дух сломает то, что успел изменить"
                        + " другой игрок. При смене правила перепроверь AuditFactCheck.of.");
    }

    @Test
    void auditFactCheck_SnapshotMatches_AllowsExecution() {
        DiffItem item = DiffItem.breakAt(CELL, "minecraft:oak_fence", SourceRef.street("s-7"), Access.SPIRIT);

        assertEquals(
                AuditFactCheck.Decision.EXECUTE,
                AuditFactCheck.of(item, "minecraft:oak_fence"),
                "Совпавший снимок факт разрешает действие: иначе группа никогда не выполнится. При"
                        + " смене правила перепроверь AuditFactCheck.of.");
    }

    @Test
    void auditFactCheck_WrongStateSnapshot_AllowsFixState() {
        DiffItem item = DiffItem.fixState(
                CELL,
                "minecraft:oak_stairs[facing=north,half=bottom]",
                Wanted.of("minecraft:oak_stairs[facing=north,half=top]"),
                SourceRef.building("hut"),
                Access.SPIRIT);

        assertEquals(
                AuditFactCheck.Decision.EXECUTE,
                AuditFactCheck.of(item, "minecraft:oak_stairs[facing=north,half=bottom]"),
                "Неверная половина из снимка разрешает FIX_STATE: иначе тактика примет её за чужой"
                        + " блок и остановит ремонт. При смене правила перепроверь BlockEquivalence.stateOk.");
    }
}
