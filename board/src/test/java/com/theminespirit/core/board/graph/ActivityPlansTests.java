package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityPlansTests {
    private static final BlockCoord AT = new BlockCoord(10, 64, -5);

    @Test
    void harvestCrop_Plan_DoesNotReplantOutsideSchematic() {
        ScriptCall harvest = calls(ActivityPlans.harvestCrop(AT).task()).get(1);

        assertEquals(
                "harvest_crop",
                harvest.script(),
                "Урожай собирают своим скриптом Иначе тест harvestCrop_Plan_DoesNotReplantOutsideSchematic перестанет ловить эту поломку. При смене harvestCrop_Plan_DoesNotReplantOutsideSchematic проверь это утверждение.");
        assertEquals(
                false,
                harvest.params().get("replant"),
                "После уборки пшеницу засевает только ремонт схемы: иначе появляется второй, ручной путь посадки "
                        + "в обход wheat_field_1. При смене harvestCrop_Plan_DoesNotReplantOutsideSchematic проверь "
                        + "farm.json field_empty и RepairAudit.");
    }

    @Test
    void milk_Plan_UsesHerdRadius() {
        ScriptCall milk = calls(ActivityPlans.milk(AT, 2).task()).get(1);

        assertEquals(
                "milk",
                milk.script(),
                "Дойка — отдельный скрипт Иначе тест milk_Plan_UsesHerdRadius перестанет ловить эту поломку. При смене milk_Plan_UsesHerdRadius проверь это утверждение.");
        assertEquals(
                2,
                milk.params().get("count"),
                "Число коров передаётся плану Иначе тест milk_Plan_UsesHerdRadius перестанет ловить эту поломку. При смене milk_Plan_UsesHerdRadius проверь это утверждение.");
    }

    @Test
    void feed_Plan_NamesSpecies() {
        ScriptCall feed = calls(ActivityPlans.feed(AT, Species.COW, 4).task()).get(1);

        assertEquals(
                "feed",
                feed.script(),
                "Кормление — отдельный скрипт Иначе тест feed_Plan_NamesSpecies перестанет ловить эту поломку. При смене feed_Plan_NamesSpecies проверь это утверждение.");
        assertEquals(
                "cow",
                feed.params().get("species"),
                "Вид стада называет план Иначе тест feed_Plan_NamesSpecies перестанет ловить эту поломку. При смене feed_Plan_NamesSpecies проверь это утверждение.");
    }

    @Test
    void craft_Plan_NamesItem() {
        ScriptCall craft =
                calls(ActivityPlans.craft(AT, "iron_pickaxe", 1).task()).get(1);

        assertEquals(
                "craft",
                craft.script(),
                "Крафт идёт скриптом craft Иначе тест craft_Plan_NamesItem перестанет ловить эту поломку. При смене craft_Plan_NamesItem проверь это утверждение.");
        assertEquals(
                "iron_pickaxe",
                craft.params().get("item"),
                "Предмет крафта назван Иначе тест craft_Plan_NamesItem перестанет ловить эту поломку. При смене craft_Plan_NamesItem проверь это утверждение.");
    }

    @Test
    void equip_Plan_WithdrawsFromChestThenEquips() {
        List<ScriptCall> calls =
                calls(ActivityPlans.equip(AT, AT, "minecraft:iron_pickaxe").task());

        assertEquals(
                "withdraw",
                calls.get(1).script(),
                "Вещь сначала берут из сундука Иначе тест equip_Plan_WithdrawsFromChestThenEquips перестанет ловить эту поломку. При смене equip_Plan_WithdrawsFromChestThenEquips проверь это утверждение.");
        assertEquals(
                "10,64,-5",
                calls.get(1).params().get("container"),
                "Сундук назван координатами Иначе тест equip_Plan_WithdrawsFromChestThenEquips перестанет ловить эту поломку. При смене equip_Plan_WithdrawsFromChestThenEquips проверь это утверждение.");
        assertEquals(
                "equip",
                calls.get(2).script(),
                "Затем надевают Иначе тест equip_Plan_WithdrawsFromChestThenEquips перестанет ловить эту поломку. При смене equip_Plan_WithdrawsFromChestThenEquips проверь это утверждение.");
        assertEquals(
                "minecraft:iron_pickaxe",
                calls.get(2).params().get("item"),
                "Надевают ту же вещь Иначе тест equip_Plan_WithdrawsFromChestThenEquips перестанет ловить эту поломку. При смене equip_Plan_WithdrawsFromChestThenEquips проверь это утверждение.");
    }

    @Test
    void unload_Plan_ForcesDeposit() {
        List<ScriptCall> calls = calls(ActivityPlans.unload(AT).task());
        ScriptCall unload = calls.get(0);

        assertEquals(
                1,
                calls.size(),
                "Выгрузка не ходит отдельным goto в занятую клетку сундука:  Иначе тест unload_Plan_ForcesDeposit перестанет ловить эту поломку. При смене unload_Plan_ForcesDeposit проверь это утверждение."
                        + calls);
        assertEquals(
                "unload",
                unload.script(),
                "Разгрузка идёт скриптом unload Иначе тест unload_Plan_ForcesDeposit перестанет ловить эту поломку. При смене unload_Plan_ForcesDeposit проверь это утверждение.");
        assertEquals(
                "10,64,-5",
                unload.params().get("container"),
                "Сундук назван координатами цели Иначе тест unload_Plan_ForcesDeposit перестанет ловить эту поломку. При смене unload_Plan_ForcesDeposit проверь это утверждение.");
        assertEquals(
                true,
                unload.params().get("force"),
                "Несмое сдают даже ниже порога Иначе тест unload_Plan_ForcesDeposit перестанет ловить эту поломку. При смене unload_Plan_ForcesDeposit проверь это утверждение.");
    }

    @Test
    void sortChest_Plan_UsesChestCoordinates() {
        ScriptCall sort = calls(ActivityPlans.sortChest(AT).task()).get(1);

        assertEquals(
                "sort_chest",
                sort.script(),
                "Сортировка идёт скриптом sort_chest Иначе тест sortChest_Plan_UsesChestCoordinates перестанет ловить эту поломку. При смене sortChest_Plan_UsesChestCoordinates проверь это утверждение.");
        assertEquals(
                "10,64,-5",
                sort.params().get("container"),
                "Сундук назван координатами Иначе тест sortChest_Plan_UsesChestCoordinates перестанет ловить эту поломку. При смене sortChest_Plan_UsesChestCoordinates проверь это утверждение.");
    }

    @Test
    void moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther() {
        List<ScriptCall> calls =
                calls(ActivityPlans.moveItem(AT, new BlockCoord(20, 64, 0), AT, "minecraft:oak_log", 16)
                        .task());

        assertEquals(
                "withdraw",
                calls.get(1).script(),
                "Из полного сундука берут Иначе тест moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther перестанет ловить эту поломку. При смене moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther проверь это утверждение.");
        assertEquals(
                "10,64,-5",
                calls.get(1).params().get("container"),
                "Источник — первый сундук Иначе тест moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther перестанет ловить эту поломку. При смене moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther проверь это утверждение.");
        assertEquals(
                "deposit",
                calls.get(3).script(),
                "В свободный сундук кладут Иначе тест moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther перестанет ловить эту поломку. При смене moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther проверь это утверждение.");
        assertEquals(
                "20,64,0",
                calls.get(3).params().get("container"),
                "Приёмник — второй сундук Иначе тест moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther перестанет ловить эту поломку. При смене moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther проверь это утверждение.");
    }

    @Test
    void bridge_Plan_ClampsLength() {
        ScriptCall bridge = calls(
                        ActivityPlans.bridge(AT, 1000, "minecraft:oak_planks").task())
                .get(0);

        assertEquals(
                "bridge",
                bridge.script(),
                "Мост ставится скриптом bridge Иначе тест bridge_Plan_ClampsLength перестанет ловить эту поломку. При смене bridge_Plan_ClampsLength проверь это утверждение.");
        assertEquals(
                64,
                bridge.params().get("length"),
                "Длина моста ограничена каталогом Иначе тест bridge_Plan_ClampsLength перестанет ловить эту поломку. При смене bridge_Plan_ClampsLength проверь это утверждение.");
        assertEquals(
                "minecraft:oak_planks",
                bridge.params().get("block"),
                "Материал моста назван Иначе тест bridge_Plan_ClampsLength перестанет ловить эту поломку. При смене bridge_Plan_ClampsLength проверь это утверждение.");
    }

    @Test
    void pillar_Plan_NeverDigsDown() {
        ScriptCall pillar = calls(ActivityPlans.pillar(3, null).task()).get(0);

        assertEquals(
                "pillar_up",
                pillar.script(),
                "Подъём идёт скриптом pillar_up Иначе тест pillar_Plan_NeverDigsDown перестанет ловить эту поломку. При смене pillar_Plan_NeverDigsDown проверь это утверждение.");
        assertEquals(
                false,
                pillar.params().get("down"),
                "Столб строят вверх, а не вниз Иначе тест pillar_Plan_NeverDigsDown перестанет ловить эту поломку. При смене pillar_Plan_NeverDigsDown проверь это утверждение.");
    }

    @Test
    void smelt_Plan_NamesProduct() {
        ScriptCall smelt =
                calls(ActivityPlans.smelt(AT, "minecraft:charcoal", 4).task()).get(1);

        assertEquals(
                "smelt",
                smelt.script(),
                "Плавка идёт скриптом smelt Иначе тест smelt_Plan_NamesProduct перестанет ловить эту поломку. При смене smelt_Plan_NamesProduct проверь это утверждение.");
        assertEquals(
                "minecraft:charcoal",
                smelt.params().get("item"),
                "Продукт плавки назван Иначе тест smelt_Plan_NamesProduct перестанет ловить эту поломку. При смене smelt_Plan_NamesProduct проверь это утверждение.");
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return calls;
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
        } else if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
        }
    }

    @Test
    void plans_MapParams_AreImmutableCopies() {
        ActivityPlans.Plan plan = ActivityPlans.craft(AT, "iron_pickaxe", 1);
        Map<String, Object> params =
                ((ScriptCall) ((SequenceNode) plan.task()).steps().get(1)).params();

        assertTrue(
                params.containsKey("item"),
                "Параметры плана доступны и не теряются Иначе тест plans_MapParams_AreImmutableCopies перестанет ловить эту поломку. При смене plans_MapParams_AreImmutableCopies проверь это утверждение.");
    }
}
