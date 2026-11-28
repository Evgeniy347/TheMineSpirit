package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownAuditGroupLifecycleTests {
    private static final BlockCoord CELL = new BlockCoord(0, 64, 0);
    private static final String KEY = "group:building:storage_3@0,0";

    private static GroupRegistry groups(AuditRegistry facts) {
        Map<BlockCoord, Expectation> cells = new LinkedHashMap<>();
        cells.put(
                CELL,
                new Expectation(CELL, Wanted.block("minecraft:stone"), SourceRef.building("storage_3"), Access.SPIRIT));
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(new ExpectIndex.Index(cells, 0, 0), 1L);
        groups.sync(facts, 1L, 10);
        return groups;
    }

    private static AuditRegistry facts() {
        AuditRegistry facts = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        facts.plan(1, 64);
        facts.retain(Set.of(new ChunkCell(0, 0)));
        facts.ensure(new ChunkCell(0, 0));
        facts.fill(2048, cell -> "minecraft:oak_planks", 1L);
        return facts;
    }

    @Test
    void groupRegistry_Release_RecalculatesIssuedGroup() {
        AuditRegistry facts = facts();
        GroupRegistry groups = groups(facts);
        groups.issue("ticket-1", KEY);
        assertEquals(
                "ticket-1",
                groups.ticketFor(KEY),
                "Выданная группа помнит свой билет: иначе второй"
                        + " дух выполнит чужой снимок одновременно. При смене индекса перепроверь ticketFor.");
        facts.record(new ChangeRing.Change(
                5L,
                CELL,
                "minecraft:oak_planks",
                "minecraft:stone",
                ChangeRing.Actor.SPIRIT,
                "своя работа",
                "ticket-1",
                ""));

        groups.release("ticket-1");

        assertEquals(
                Group.Status.DONE,
                groups.group(KEY).status(),
                "После освобождения билета группа"
                        + " пересчитывается по обновлённому кэшу: иначе выполненная работа останется ISSUED. При"
                        + " смене каскада перепроверь release и build.");
        assertTrue(
                facts.dirty().isEmpty(),
                "Своё действие не оставляет грязь после release: иначе граф"
                        + " снова откроет завершённую группу. При смене каскада перепроверь AuditInvalidation.record.");
    }

    @Test
    void groupRegistry_Block_ReleasesTicketAndStopsGraphNode() {
        AuditRegistry facts = facts();
        GroupRegistry groups = groups(facts);
        groups.issue("ticket-1", KEY);

        groups.block(KEY, "к клетке не подойти");

        assertEquals(
                Group.Status.BLOCKED,
                groups.group(KEY).status(),
                "Недостижимая группа блокируется:"
                        + " иначе граф будет выдавать её снова и снова. При смене правил перепроверь GroupRegistry.block.");
        assertTrue(
                facts.record(new ChangeRing.Change(
                        6L,
                        CELL,
                        "minecraft:oak_planks",
                        "minecraft:air",
                        ChangeRing.Actor.SPIRIT,
                        "после блокировки",
                        "ticket-1",
                        "")),
                "Блокировка освобождает билет: иначе старое действие духа снова станет своим и спрячет"
                        + " реальное изменение. При смене правил перепроверь block и released.");
    }
}
