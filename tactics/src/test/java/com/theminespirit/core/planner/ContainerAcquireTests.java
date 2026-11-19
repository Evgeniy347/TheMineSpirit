package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContainerAcquireTests {
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();
    private static final ConditionRegistry NO_CONDITIONS = ConditionRegistry.of(Map.of());

    @Test
    void chestItem_ChestLiesInChest_PlannedByCraft() {
        AcquireScript script = new AcquireScript(
                "minecraft:chest",
                1,
                chestKnowledge(),
                new NoContainerSupplies(),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));

        List<String> planned = scriptsOf(script.buildSubplan().orElseThrow());

        assertTrue(
                planned.stream().anyMatch("craft:minecraft:chest"::equals),
                "Сундук для стройки обязан планироваться крафтом из досок, а не выниматься из склада: " + planned);
        assertTrue(
                planned.stream().anyMatch("gather:oak_planks"::equals), "Доски на сундук добывают заново: " + planned);
    }

    @Test
    void storageItems_ContainerAndPlank_Classified() {
        assertTrue(
                StorageItems.container("minecraft:barrel") && StorageItems.container("white_shulker_box"),
                "Бочка и шалкер тоже места хранения: вынимать их из сундуков нельзя");
        assertTrue(
                !StorageItems.container("minecraft:oak_planks"),
                "Доски — расходный материал: их по-прежнему берут из сундуков");
    }

    private static ResourceKnowledge chestKnowledge() {
        return new ResourceKnowledge() {
            @Override
            public boolean isGatherable(String item) {
                return "oak_planks".equals(ItemName.path(item));
            }

            @Override
            public boolean isCraftable(String item) {
                return "chest".equals(ItemName.path(item));
            }

            @Override
            public boolean isCraftedProduct(String item) {
                return false;
            }

            @Override
            public int outputCount(String item) {
                return 1;
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return List.of(new Ingredient("oak_planks", 8));
            }

            @Override
            public boolean isSmeltable(String item) {
                return false;
            }

            @Override
            public String smeltInputFor(String item) {
                throw new AssertionError("Сундук не плавят: резолвер не должен спрашивать вход плавки");
            }
        };
    }

    private static List<String> scriptsOf(TaskNode node) {
        List<String> scripts = new ArrayList<>();
        collect(node, scripts);
        return scripts;
    }

    private static void collect(TaskNode node, List<String> scripts) {
        switch (node) {
            case ScriptCall call ->
                scripts.add(call.script() + ":"
                        + call.params().getOrDefault("block", call.params().getOrDefault("item", "?")));
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, scripts));
            default ->
                throw new IllegalArgumentException(
                        "Узел " + node.getClass().getSimpleName() + " не разбирается: тест ждёт sequence и script");
        }
    }

    private static final class NoContainerSupplies implements AcquireSupplies {
        @Override
        public int inInventory(String item) {
            return 0;
        }

        @Override
        public List<ScriptCall> withdraw(String item, int maxCount) {
            if (StorageItems.container(item)) {
                throw new AssertionError("Сундук нельзя вынимать из сундука: это ломает склад");
            }
            return List.of();
        }

        @Override
        public Map<String, Integer> inventory() {
            return Map.of();
        }

        @Override
        public Set<String> nearbyStations() {
            return Set.of();
        }
    }
}
