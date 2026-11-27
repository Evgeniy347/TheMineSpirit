package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.audit.Access;
import com.theminespirit.core.board.audit.AuditGraphKeys;
import com.theminespirit.core.board.audit.AuditGraphMapping;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.SourceRef;
import com.theminespirit.core.board.audit.Wanted;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditGraphFactsTests {
    private static final BlockCoord CELL = new BlockCoord(0, 64, 0);

    private static Group group(Group.Kind kind, String subject, Group.Status status, List<DiffItem> items, int total) {
        ChunkCell chunk = kind == Group.Kind.BUILDING || kind == Group.Kind.MINE ? null : new ChunkCell(0, 0);
        return Group.pending(kind, subject, chunk, items, total).withStatus(status, "");
    }

    private static GraphState state(Group... groups) {
        GraphFacts facts = GraphFacts.empty().withAuditGroups(List.of(groups));
        return new GraphState(
                Map.of(),
                1,
                Map.of(),
                Map.of(),
                Map.of(),
                100,
                Set.of("audit"),
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                facts);
    }

    @Test
    void auditGraphFacts_GroupKeyMaterialsAndShare_StayAddressable() {
        Group group = group(
                Group.Kind.STREET,
                "s-7",
                Group.Status.PENDING,
                List.of(
                        DiffItem.breakAt(CELL, "minecraft:oak_fence", SourceRef.street("s-7"), Access.SPIRIT),
                        DiffItem.place(
                                CELL,
                                "minecraft:air",
                                Wanted.block("minecraft:stone"),
                                SourceRef.street("s-7"),
                                Access.SPIRIT),
                        DiffItem.fixState(
                                CELL,
                                "minecraft:oak_stairs[half=bottom]",
                                Wanted.of("minecraft:oak_stairs[half=top]"),
                                SourceRef.street("s-7"),
                                Access.SPIRIT)),
                3);

        assertEquals(
                "audit:street:s-7@0,0",
                AuditGraphKeys.key(group),
                "Ключ группы несёт вид, id и чанк:"
                        + " иначе две улицы в соседних чанках сольются в один узел. При смене формата перепроверь AuditGraphKeys.");
        assertEquals(
                group,
                AuditGraphFacts.group(state(group), "street:s-7@0,0"),
                "Граф находит группу по"
                        + " адресному ключу: иначе билет не сможет получить снимок. При смене поиска перепроверь AuditGraphFacts.group.");
        assertEquals(
                Map.of("stone", 1, "oak_stairs", 1),
                AuditGraphFacts.materials(group),
                "Материалы считаются"
                        + " только для установки и правки состояния: иначе BREAK создаст ложный заказ. При смене правил"
                        + " перепроверь AuditGraphFacts.materials.");
        Group partial = group.rebuilt(List.of(group.items().get(0)), group.total(), 0);
        assertTrue(
                AuditGraphFacts.weight(group, 68) > AuditGraphFacts.weight(partial, 68),
                "Вес растёт с"
                        + " долей невыполненного: иначе почти законченная улица обгонит нетронутый склад. При смене"
                        + " формулы перепроверь weight и порядок задач в графе.");
    }

    @Test
    void auditGraphFacts_Dependencies_WaitForCompletedGroups() {
        Group pit = group(Group.Kind.GROUND, "pit", Group.Status.PENDING, List.of(), 1);
        GraphEvent.Task task = new GraphEvent.Task(
                "building:storage",
                BoardCase.REPAIR_BUILDING,
                68,
                Map.of(),
                null,
                null,
                null,
                null,
                "audit:ground:pit",
                List.of(),
                true);

        assertFalse(
                AuditGraphFacts.ready(state(pit), task),
                "Незавершённый предшественник блокирует задачу:"
                        + " иначе склад начнёт строиться до расчистки ямы. При смене зависимостей перепроверь completed.");
        Group done = pit.withStatus(Group.Status.DONE, "");
        assertTrue(
                AuditGraphFacts.ready(state(done), task),
                "Завершённый предшественник открывает задачу:"
                        + " иначе лестница зависнет навсегда. При смене зависимостей перепроверь completed.");
        assertFalse(
                AuditGraphFacts.ready(state(done, pit), task),
                "Активная обязательная группа не считается"
                        + " завершённой: иначе requires обходится молча. При смене правил перепроверь ready.");
    }

    @Test
    void auditGraphFacts_Mapping_CoversEveryGroupKind() {
        assertEquals(
                List.of(
                        BoardCase.REPAIR_BUILDING,
                        BoardCase.BUILD_STREETS,
                        BoardCase.BUILD_FENCE,
                        BoardCase.CLEAR_GROUNDS,
                        BoardCase.BUILD_MINE),
                List.of(
                        AuditGraphMapping.caseOf(Group.Kind.BUILDING),
                        AuditGraphMapping.caseOf(Group.Kind.STREET),
                        AuditGraphMapping.caseOf(Group.Kind.WALL),
                        AuditGraphMapping.caseOf(Group.Kind.GROUND),
                        AuditGraphMapping.caseOf(Group.Kind.MINE)),
                "Каждый вид группы маппится в свой кейс: иначе предмет попадёт в граф без действия, а"
                        + " внешний стратег не поймёт, что делать. При смене видов перепроверь AuditGraphMapping.");
    }
}
