package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HiveSourceResolveTests {
    @Test
    void resolve_HiveProduct_UsesHarvestHiveNotGather() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of(), Map.of(), Map.of())
                .withSourceScript("honeycomb", "harvest_hive", Map.of("radius", 16));

        TaskNode plan = CorePlanner.resolve("honeycomb", 2, knowledge);

        assertEquals(
                new ScriptCall("harvest_hive", Map.of("radius", 16, "count", 2)),
                plan,
                "Мёд-соты берутся из улья, а не из блока: gather по 'honeycomb' искал бы несуществующий блок и "
                        + "проваливал LIGHTING и другие цепочки «не удалось добыть honeycomb»"
                        + " Иначе тест resolve_HiveProduct_UsesHarvestHiveNotGather перестанет ловить эту поломку. При смене resolve_HiveProduct_UsesHarvestHiveNotGather проверь это утверждение.");
    }

    @Test
    void resolve_HiveProductBatched_KeepsHarvestParams() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of(), Map.of(), Map.of())
                .withSourceScript("honeycomb", "harvest_hive", Map.of("radius", 16), 2);

        TaskNode plan = CorePlanner.resolve("honeycomb", 3, knowledge);

        SequenceNode sequence = assertInstanceOf(
                SequenceNode.class,
                plan,
                "Три улья при пределе два — две порции"
                        + " Иначе тест resolve_HiveProductBatched_KeepsHarvestParams перестанет ловить эту поломку. При смене resolve_HiveProductBatched_KeepsHarvestParams проверь это утверждение.");
        assertEquals(
                2,
                sequence.steps().size(),
                "Три улья при пределе два идут двумя порциями: " + sequence.steps()
                        + " Иначе тест resolve_HiveProductBatched_KeepsHarvestParams перестанет ловить эту поломку. При смене resolve_HiveProductBatched_KeepsHarvestParams проверь это утверждение.");
        assertTrue(
                sequence.steps().get(0) instanceof ScriptCall first
                        && first.params().get("radius").equals(16)
                        && first.params().get("count").equals(2),
                "Порция сохраняет радиус улья и свой предел: " + sequence.steps()
                        + " Иначе тест resolve_HiveProductBatched_KeepsHarvestParams перестанет ловить эту поломку. При смене resolve_HiveProductBatched_KeepsHarvestParams проверь это утверждение.");
    }
}
