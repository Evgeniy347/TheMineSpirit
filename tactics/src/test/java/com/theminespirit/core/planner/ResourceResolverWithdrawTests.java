package com.theminespirit.core.planner;

import static com.theminespirit.core.planner.ResolverWood.wood;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceResolverWithdrawTests {
    private static final ResourceKnowledge KNOWLEDGE =
            new StubKnowledge(Set.of("oak_log"), Map.of("stick", List.of(new Ingredient("oak_log", 2))), Map.of());

    @Test
    void resolve_IngredientInInventoryAndChest_TakesThemBeforeGathering() {
        ScriptCall withdraw = new ScriptCall(
                "withdraw",
                Map.of("container", "1,2,3", "item", "oak_log", "count", 3, "radius", 16, "leftover", false));
        AcquireSupplies supplies = new AcquireSupplies() {
            @Override
            public int inInventory(String item) {
                return "oak_log".equals(item) ? 2 : 0;
            }

            @Override
            public List<ScriptCall> withdraw(String item, int maxCount) {
                return "oak_log".equals(item) ? List.of(withdraw) : List.of();
            }

            @Override
            public Map<String, Integer> inventory() {
                return Map.of("minecraft:oak_log", 2);
            }

            @Override
            public Set<String> nearbyStations() {
                return Set.of();
            }
        };

        TaskNode plan = ResourceResolver.resolve(
                "stick", 3, KNOWLEDGE, CoreScripts.defaultCatalog(), ConditionCatalog.empty(), supplies);
        ScriptCall craft = new ScriptCall("craft", Map.of("item", "stick", "count", 3, "radius", 16));

        assertEquals(
                new SequenceNode(List.of(new SequenceNode(List.of(withdraw, wood("oak_log", 1))), craft)),
                plan,
                "Ингредиент берётся из инвентаря (2) и сундука (3), добывается лишь остаток (1): acquire забора с "
                        + "брёвнами на складе рубил деревья в 16 и падал (приёмка «джунгли»). Каталог добавляет "
                        + "leftover=false, поэтому ожидание обязано содержать тот же нормализованный ScriptCall"
                        + " Иначе тест resolve_IngredientInInventoryAndChest_TakesThemBeforeGathering перестанет ловить эту поломку. "
                        + "При смене resolve_IngredientInInventoryAndChest_TakesThemBeforeGathering проверь ResourceResolver и HandsScriptSpecs.");
    }
}
