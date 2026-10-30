package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Preset;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.ClearAreaParams;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class UpgradePlanTests {
    private static final String NAMESPACE = "minecraft:";
    private static final BlockCoord ANCHOR = new BlockCoord(10, 64, 10);

    @Test
    void required_WarehouseSecondLevel_IsSchemeDifference() {
        Map<String, Integer> need = UpgradePlan.required("storage_v1", "storage_v2");

        assertEquals(
                difference("storage_v1", "storage_v2"),
                need,
                "Уровень 2 склада требует только то, чего нет в уровне 1: полный набор второй схемы "
                        + "заставил бы духа носить заново уже стоящие блоки");
    }

    @Test
    void required_HouseSecondLevel_IsStoneHouseMinusHut() {
        assertEquals(
                difference("hut_1", "stone_house"),
                UpgradePlan.required("hut_1", "stone_house"),
                "Каменный дом уровня 2 требует разницу с хижиной уровня 1: общие брёвна и доски не "
                        + "заказываются заново");
    }

    @Test
    void required_WithoutPreviousLevel_IsWholeScheme() {
        assertEquals(
                UpgradePlan.materials("storage_v2"),
                UpgradePlan.required(null, "storage_v2"),
                "Первая постройка уровня требует всю схему: вычитать нечего");
    }

    @Test
    void difference_WarehouseSecondLevel_HasNoZeroOrForeignItems() {
        Map<String, Integer> need = UpgradePlan.difference("storage_v1", "storage_v2");

        need.forEach((item, count) ->
                assertTrue(count > 0, "Разница схем не содержит нулевых остатков: '" + item + "' = " + count));
        assertTrue(
                need.keySet().stream()
                        .allMatch(item -> UpgradePlan.materials("storage_v2").containsKey(item)),
                "В разнице нет предметов, которых нет в новой схеме: " + need.keySet());
    }

    @Test
    void site_WarehouseSecondLevel_StaysAtFirstAnchor() {
        BlockCoord home = new BlockCoord(10, 64, -5);

        assertEquals(
                WarehouseRules.site(home, 1),
                WarehouseRules.site(home, 2),
                "Склад уровня 2 ставится на якорь уровня 1: улучшение на месте не занимает второй участок");
        assertEquals(
                home,
                WarehouseRules.site(home, 4),
                "Все уровни склада стоят на одном якоре: переносить и освобождать участки больше не нужно");
    }

    @Test
    void plan_WarehouseSecondLevel_BuildsAtSameAnchor() {
        for (int level = 1; level <= 2; level++) {
            ScriptCall build = first(
                    TicketPlans.plan(BoardCase.BUILD_WAREHOUSE, level, TicketTarget.at(ANCHOR)), "build_schematic");

            assertEquals(
                    ANCHOR.x(),
                    build.params().get("x"),
                    "Уровень " + level + " склада строится на том же якоре по X: второго участка нет");
            assertEquals(
                    ANCHOR.y(), build.params().get("y"), "Уровень " + level + " склада строится на том же якоре по Y");
            assertEquals(
                    ANCHOR.z(), build.params().get("z"), "Уровень " + level + " склада строится на том же якоре по Z");
        }
    }

    @Test
    void plan_HouseSecondAndThirdLevels_BuildAtSameAnchor() {
        List<String> schemes = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            ScriptCall build =
                    first(TicketPlans.plan(BoardCase.BUILD_HOUSE, level, TicketTarget.at(ANCHOR)), "build_schematic");
            schemes.add(String.valueOf(build.params().get("file")));
            assertEquals(
                    ANCHOR,
                    new BlockCoord(
                            (Integer) build.params().get("x"),
                            (Integer) build.params().get("y"),
                            (Integer) build.params().get("z")),
                    "Дом уровня " + level + " ставится на тот же якорь: улучшение не ищет второй участок");
        }
        assertNotEquals(
                schemes.get(0), schemes.get(1), "Дом уровня 2 строится не схемой хижины: уровень N+1 берёт свою схему");
    }

    @Test
    void dismantleShelter_WithBedAndWall_ClearsBedFirst() {
        BlockCoord bed = new BlockCoord(-168, 90, -514);
        BlockCoord wall = new BlockCoord(-170, 70, -514);
        StageWork work = new StageWork(
                TownFixtures.flatSite(),
                List.of(new PlannedBlock(wall, "minecraft:cobblestone"), new PlannedBlock(bed, "minecraft:white_bed")),
                List.of());
        TaskNode plan = TicketPlans.plan(
                BoardCase.DISMANTLE_SHELTER, TicketTarget.at(bed).withWork(work));

        List<BlockCoord> order = calls(plan, "clear_area").stream()
                .map(call -> {
                    ClearAreaParams params = ClearAreaParams.from(call.params());
                    return new BlockCoord(params.x1(), params.y1(), params.z1());
                })
                .toList();

        assertEquals(
                bed,
                order.get(0),
                "Кровать снимается до сноса остальных блоков: иначе она сгорит/пропадёт вместе со стенами "
                        + "и духам негде спать");
    }

    private static Map<String, Integer> difference(String previous, String next) {
        Map<String, Integer> before = presetMaterials(previous);
        Map<String, Integer> after = presetMaterials(next);
        Map<String, Integer> need = new TreeMap<>();
        after.forEach((item, count) -> {
            int rest = count - before.getOrDefault(item, 0);
            if (rest > 0) {
                need.put(item, rest);
            }
        });
        return Map.copyOf(need);
    }

    private static Map<String, Integer> presetMaterials(String scheme) {
        Preset preset = DataTables.INSTANCE
                .presets()
                .current()
                .byId(scheme)
                .orElseThrow(() -> new IllegalStateException("Нет схемы " + scheme + " в тестовой библиотеке"));
        Map<String, Integer> counts = new TreeMap<>();
        preset.materials()
                .forEach((item, count) -> counts.merge(
                        item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item, count, Integer::sum));
        return Map.copyOf(counts);
    }

    private static ScriptCall first(TaskNode node, String script) {
        return calls(node, script).stream().findFirst().orElseThrow();
    }

    private static List<ScriptCall> calls(TaskNode root, String script) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(root, script, calls);
        return List.copyOf(calls);
    }

    private static void collect(TaskNode node, String script, List<ScriptCall> calls) {
        switch (node) {
            case ScriptCall call -> {
                if (script.equals(call.script())) {
                    calls.add(call);
                }
            }
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, script, calls));
            case FallbackNode fallback -> fallback.steps().forEach(step -> collect(step, script, calls));
            default -> {}
        }
    }
}
