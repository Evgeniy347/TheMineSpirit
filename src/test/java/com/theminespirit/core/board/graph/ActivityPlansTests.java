package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
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
    void till_Plan_GoesToFieldThenHoes() {
        ActivityPlans.Plan plan = ActivityPlans.till(AT);

        assertEquals("till", plan.script(), "Занятие называет скрипт мотыги");
        List<ScriptCall> calls = calls(plan.task());
        assertEquals("goto", calls.get(0).script(), "Сначала дух идёт к грядке");
        assertEquals("till", calls.get(1).script(), "Затем пашет");
    }

    @Test
    void plantCrop_Plan_NamesWheat() {
        List<ScriptCall> calls = calls(ActivityPlans.plantCrop(AT, "wheat", 8).task());

        assertEquals("plant_crop", calls.get(1).script(), "Посев идёт скриптом plant_crop");
        assertEquals("wheat", calls.get(1).params().get("crop"), "Сажают пшеницу");
        assertEquals(8, calls.get(1).params().get("count"), "Число клеток передаётся плану");
    }

    @Test
    void harvestCrop_Plan_ReplantsByDefault() {
        ScriptCall harvest = calls(ActivityPlans.harvestCrop(AT).task()).get(1);

        assertEquals("harvest_crop", harvest.script(), "Урожай собирают своим скриптом");
        assertEquals(true, harvest.params().get("replant"), "После уборки грядку засевают снова");
    }

    @Test
    void milk_Plan_UsesHerdRadius() {
        ScriptCall milk = calls(ActivityPlans.milk(AT, 2).task()).get(1);

        assertEquals("milk", milk.script(), "Дойка — отдельный скрипт");
        assertEquals(2, milk.params().get("count"), "Число коров передаётся плану");
    }

    @Test
    void feed_Plan_NamesSpecies() {
        ScriptCall feed = calls(ActivityPlans.feed(AT, Species.COW, 4).task()).get(1);

        assertEquals("feed", feed.script(), "Кормление — отдельный скрипт");
        assertEquals("cow", feed.params().get("species"), "Вид стада называет план");
    }

    @Test
    void craft_Plan_NamesItem() {
        ScriptCall craft =
                calls(ActivityPlans.craft(AT, "iron_pickaxe", 1).task()).get(1);

        assertEquals("craft", craft.script(), "Крафт идёт скриптом craft");
        assertEquals("iron_pickaxe", craft.params().get("item"), "Предмет крафта назван");
    }

    @Test
    void equip_Plan_WithdrawsFromChestThenEquips() {
        List<ScriptCall> calls =
                calls(ActivityPlans.equip(AT, AT, "minecraft:iron_pickaxe").task());

        assertEquals("withdraw", calls.get(1).script(), "Вещь сначала берут из сундука");
        assertEquals("10,64,-5", calls.get(1).params().get("container"), "Сундук назван координатами");
        assertEquals("equip", calls.get(2).script(), "Затем надевают");
        assertEquals("minecraft:iron_pickaxe", calls.get(2).params().get("item"), "Надевают ту же вещь");
    }

    @Test
    void unload_Plan_ForcesDeposit() {
        ScriptCall unload = calls(ActivityPlans.unload(AT).task()).get(1);

        assertEquals("unload", unload.script(), "Разгрузка идёт скриптом unload");
        assertEquals(true, unload.params().get("force"), "Несмое сдают даже ниже порога");
    }

    @Test
    void sortChest_Plan_UsesChestCoordinates() {
        ScriptCall sort = calls(ActivityPlans.sortChest(AT).task()).get(1);

        assertEquals("sort_chest", sort.script(), "Сортировка идёт скриптом sort_chest");
        assertEquals("10,64,-5", sort.params().get("container"), "Сундук назван координатами");
    }

    @Test
    void moveItem_Plan_WithdrawsFromOneAndDepositsIntoOther() {
        List<ScriptCall> calls =
                calls(ActivityPlans.moveItem(AT, new BlockCoord(20, 64, 0), AT, "minecraft:oak_log", 16)
                        .task());

        assertEquals("withdraw", calls.get(1).script(), "Из полного сундука берут");
        assertEquals("10,64,-5", calls.get(1).params().get("container"), "Источник — первый сундук");
        assertEquals("deposit", calls.get(3).script(), "В свободный сундук кладут");
        assertEquals("20,64,0", calls.get(3).params().get("container"), "Приёмник — второй сундук");
    }

    @Test
    void bridge_Plan_ClampsLength() {
        ScriptCall bridge = calls(
                        ActivityPlans.bridge(AT, 1000, "minecraft:oak_planks").task())
                .get(0);

        assertEquals("bridge", bridge.script(), "Мост ставится скриптом bridge");
        assertEquals(64, bridge.params().get("length"), "Длина моста ограничена каталогом");
        assertEquals("minecraft:oak_planks", bridge.params().get("block"), "Материал моста назван");
    }

    @Test
    void pillar_Plan_NeverDigsDown() {
        ScriptCall pillar = calls(ActivityPlans.pillar(3, null).task()).get(0);

        assertEquals("pillar_up", pillar.script(), "Подъём идёт скриптом pillar_up");
        assertEquals(false, pillar.params().get("down"), "Столб строят вверх, а не вниз");
    }

    @Test
    void smelt_Plan_NamesProduct() {
        ScriptCall smelt =
                calls(ActivityPlans.smelt(AT, "minecraft:charcoal", 4).task()).get(1);

        assertEquals("smelt", smelt.script(), "Плавка идёт скриптом smelt");
        assertEquals("minecraft:charcoal", smelt.params().get("item"), "Продукт плавки назван");
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

        assertTrue(params.containsKey("item"), "Параметры плана доступны и не теряются");
    }
}
