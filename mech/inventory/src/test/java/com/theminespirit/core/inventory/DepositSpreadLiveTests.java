package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepositSpreadLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/observe11/deposit-1444.json");

    @Test
    void liveDeposit_FullChestWithPartialStack_ContinuesIntoFreeChest() throws IOException {
        JsonNode live = live();
        List<ContainerSnapshot> containers = containers(live);
        BlockCoord origin = coord(live.get("origin"));
        int requested = live.get("requested").get("count").asInt();

        DepositSpread.Plan plan =
                DepositSpread.plan(containers, origin, new Stack("minecraft:dirt", 0, 64, 64), requested);

        int planned = plan.steps().stream().mapToInt(DepositSpread.Step::count).sum();
        assertEquals(
                requested,
                planned,
                "живой [deposit] 14:44:33 положил 8 из 64 в полный -181,69,-517 и упал, хотя рядом "
                        + "-178,69,-517 свободен (54 из 54): план обязан разложить все 64 по сундукам, "
                        + "а не остановиться на первом, иначе перенос обрывается. Шаги: " + plan.steps()
                        + ". При смене plan проверь раскладку по двум сундукам");
        assertEquals(
                0,
                plan.left(),
                "после свободного сундука у духа не остаётся 'dirt', иначе перенос недобрал и задание " + "провалено: "
                        + plan.left() + ". При смене plan проверь остаток после двух сундуков");
        List<Integer> usedX = plan.steps().stream()
                .map(step -> containers.get(step.index()).pos().x())
                .toList();
        assertEquals(
                List.of(-181, -178),
                usedX,
                "первым идёт ближайший полный сундук с неполной стопкой dirt (8 штук), вторым — свободный "
                        + "-178,69,-517, иначе план обходит полный сундук; фактические шаги: " + plan.steps()
                        + ". При смене plan проверь порядок сундуков");
    }

    @Test
    void liveDeposit_FullChestWithoutPartialStack_IsSkipped() throws IOException {
        JsonNode live = live();
        List<ContainerSnapshot> containers = List.of(
                full(live.get("containers").get(0).get("pos"), 54, "minecraft:stone"),
                empty(live.get("containers").get(1).get("pos"), 54));

        DepositSpread.Plan plan =
                DepositSpread.plan(containers, coord(live.get("origin")), new Stack("minecraft:dirt", 0, 64, 64), 64);

        assertEquals(
                0,
                plan.left(),
                "в свободный сундук помещается весь dirt, иначе перенос недобрал и остаток висит у духа: " + plan
                        + ". При смене plan проверь полную раскладку в свободный сундук");
        assertEquals(
                List.of(1),
                plan.steps().stream().map(DepositSpread.Step::index).toList(),
                "сундук без неполной стопки dirt пропускается: 8 предметов туда не влезли бы, а план "
                        + "обязан сразу идти в свободный, иначе план зря идёт в полный и теряет тик. "
                        + "При смене plan проверь пропуск полного сундука");
        assertEquals(
                64,
                plan.steps().get(0).count(),
                "весь запрос уходит в свободный сундук, иначе часть груза зависнет у духа. "
                        + "При смене plan проверь размер первого шага при свободном сундуке");
    }

    @Test
    void liveDeposit_EveryChestFull_ReportsShortageInsteadOfSilentPartial() throws IOException {
        JsonNode live = live();
        List<ContainerSnapshot> containers =
                List.of(full(live.get("containers").get(0).get("pos"), 54, "minecraft:dirt"));

        DepositSpread.Plan plan =
                DepositSpread.plan(containers, coord(live.get("origin")), new Stack("minecraft:dirt", 0, 64, 64), 64);

        assertEquals(
                0,
                plan.steps().size(),
                "в полный сундук без неполной стопки dirt класть нечего, иначе план обещает невозможный "
                        + "перенос и задание зависает. При смене plan проверь отказ при полном сундуке");
        assertEquals(
                64,
                plan.left(),
                "остаток виден вызывающему коду: он решает заказать сундук, иначе нехватку не увидеть "
                        + "и задание молча провалится. При смене plan проверь остаток 64");
        assertTrue(
                plan.refusal() != null && plan.refusal().contains("-181,69,-517"),
                "отказ называет сундук, иначе причину не найти и второй контейнер не поставить: " + plan.refusal()
                        + ". При смене plan проверь текст отказа и координату сундука");
    }

    @Test
    void liveInventory_SurplusInMining_GivesTrashAndExtraTools() throws IOException {
        List<Stack> inventory = inventory(live());
        List<Slice> surplus =
                DepositSelection.surplus(inventory, KitFor.kitFor("mining", false), ValueTable.load(), -1);

        assertEquals(
                448,
                surplusCount(inventory, surplus, "minecraft:dirt"),
                "вся земля — мусор, её сдают, иначе мусор останется у духа и забьёт инвентарь. "
                        + "При смене surplus проверь сдачу dirt по всем слотам инвентаря");
        assertEquals(
                15,
                surplusCount(inventory, surplus, "minecraft:chest"),
                "лишние сундуки сдаются, иначе они висят в инвентаре и отнимают место у руды. "
                        + "При смене surplus проверь сдачу minecraft:chest целиком");
        assertEquals(
                55,
                surplusCount(inventory, surplus, "minecraft:oak_fence"),
                "частокол вне набора сдаётся, иначе забор лежит у духа и отнимает слоты. "
                        + "При смене surplus проверь сдачу minecraft:oak_fence целиком");
        assertTrue(
                surplusCount(inventory, surplus, "minecraft:stone_axe") >= 1,
                "у духа 4 stone_axe при наборе без топора: лишние инструменты обязаны уходить на склад, "
                        + "иначе инвентарь переполнится и руда не влезет. При смене surplus проверь stone_axe");
    }

    private static int surplusCount(List<Stack> inventory, List<Slice> surplus, String item) {
        int count = 0;
        for (Slice slice : surplus) {
            Stack stack = inventory.get(slice.slot());
            if (stack != null && stack.item().equals(item)) {
                count += slice.count();
            }
        }
        return count;
    }

    private static List<Stack> inventory(JsonNode live) {
        List<Stack> stacks = new ArrayList<>();
        for (JsonNode node : live.get("inventory")) {
            String item = node.get("item").asText();
            int left = node.get("count").asInt();
            int maxStack = item.endsWith("_axe") || item.endsWith("_pickaxe") || item.endsWith("_shovel") ? 1 : 64;
            while (left > 0) {
                int count = Math.min(left, maxStack);
                stacks.add(new Stack(item, 0, count, maxStack));
                left -= count;
            }
        }
        return stacks;
    }

    private static JsonNode live() throws IOException {
        return new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
    }

    private static List<ContainerSnapshot> containers(JsonNode live) {
        List<ContainerSnapshot> containers = new ArrayList<>();
        for (JsonNode node : live.get("containers")) {
            containers.add(snapshot(node));
        }
        return containers;
    }

    private static ContainerSnapshot snapshot(JsonNode node) {
        String item = node.has("item") ? node.get("item").asText() : null;
        int slots = node.get("slots").asInt();
        if (item == null) {
            return empty(node.get("pos"), slots);
        }
        List<Stack> contents = new ArrayList<>();
        int fullStacks = node.get("fullStacks").asInt();
        for (int index = 0; index < fullStacks; index++) {
            contents.add(new Stack(item, 0, 64, 64));
        }
        contents.add(new Stack(item, 0, node.get("partial").asInt(), 64));
        while (contents.size() < slots) {
            contents.add(null);
        }
        return new ContainerSnapshot(coord(node.get("pos")), contents);
    }

    private static ContainerSnapshot full(JsonNode pos, int slots, String item) {
        List<Stack> contents = new ArrayList<>();
        for (int index = 0; index < slots; index++) {
            contents.add(new Stack(item, 0, 64, 64));
        }
        return new ContainerSnapshot(coord(pos), contents);
    }

    private static ContainerSnapshot empty(JsonNode pos, int slots) {
        List<Stack> contents = new ArrayList<>();
        for (int index = 0; index < slots; index++) {
            contents.add(null);
        }
        return new ContainerSnapshot(coord(pos), contents);
    }

    private static BlockCoord coord(JsonNode value) {
        return new BlockCoord(
                value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
    }
}
