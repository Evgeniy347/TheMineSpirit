package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestPlansTests {
    private static final BlockCoord CHEST = new BlockCoord(10, 64, 10);

    @Test
    void demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking() {
        ContainerSnapshot chest = snapshot(new Stack("dirt", 0, 5, 64), new Stack("oak_log", 0, 3, 64));

        List<ScriptCall> calls = calls(ChestPlans.demolish(chest, List.of(CHEST)));

        List<String> scripts = calls.stream().map(ScriptCall::script).toList();
        assertEquals(
                List.of("withdraw", "withdraw", "deposit", "deposit", "clear_area"),
                scripts,
                "Непустой сундук: все предметы вынесены в склад одним заходом, и только потом сломан: " + scripts);
        assertTrue(
                scripts.indexOf("deposit") > scripts.lastIndexOf("withdraw"),
                "Сначала забирают всё из сундука, потом несут на склад: иначе лишние ходки на каждый тип: " + scripts);
        assertEquals(
                "10,64,10",
                calls.get(0).params().get("container"),
                "Забирают именно из этого сундука, а не из ближайшего");
        assertEquals("warehouse", calls.get(2).params().get("container"), "Вещи кладут в склад поселения");
    }

    @Test
    void demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse() {
        List<Stack> stacks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            stacks.add(new Stack("item_" + index, 0, 4, 64));
        }

        List<ScriptCall> calls = calls(ChestPlans.demolish(snapshot(stacks.toArray(Stack[]::new)), List.of(CHEST)));

        List<String> scripts = calls.stream().map(ScriptCall::script).toList();
        assertEquals(
                20,
                scripts.stream().filter("withdraw"::equals).count(),
                "20 типов забирают за один заход к сундуку: " + scripts);
        assertEquals("withdraw", scripts.get(19), "Все 20 заборов идут подряд, до первого похода на склад: " + scripts);
        assertEquals("deposit", scripts.get(20), "После забора — перенос на склад: " + scripts);
        assertEquals(
                41,
                scripts.size(),
                "На 20 типов — 20 заборов, 20 переносов и один слом, а не поход на каждый тип: " + scripts);
    }

    @Test
    void demolish_MoveLargerThanScriptLimit_SplitsButKeepsBothPhases() {
        ContainerSnapshot chest = snapshot(
                new Stack("dirt", 0, 64, 64),
                new Stack("dirt", 1, 64, 64),
                new Stack("dirt", 2, 64, 64),
                new Stack("dirt", 3, 64, 64),
                new Stack("dirt", 4, 44, 64));

        List<ScriptCall> calls = calls(ChestPlans.demolish(chest, List.of(CHEST)));

        List<String> scripts = calls.stream().map(ScriptCall::script).toList();
        assertEquals(
                List.of("withdraw", "withdraw", "deposit", "deposit", "clear_area"),
                scripts,
                "300 грязи не влезают в предел скрипта 256: два забора и два переноса, фазы не перемешиваются: "
                        + scripts);
    }

    @Test
    void demolish_EmptyChest_BreaksWithoutTransfers() {
        List<ScriptCall> calls = calls(ChestPlans.demolish(snapshot(), List.of(CHEST)));

        assertEquals(
                List.of("clear_area"), calls.stream().map(ScriptCall::script).toList(), "Пустой сундук ломают");
    }

    @Test
    void demolish_LargeStack_SplitsTransfersByScriptLimit() {
        ContainerSnapshot chest = snapshot(
                new Stack("dirt", 0, 64, 64),
                new Stack("dirt", 1, 64, 64),
                new Stack("dirt", 2, 64, 64),
                new Stack("dirt", 3, 64, 64));

        List<ScriptCall> calls = calls(ChestPlans.demolish(chest, List.of(CHEST)));

        long withdraws =
                calls.stream().filter(call -> "withdraw".equals(call.script())).count();
        assertEquals(1, withdraws, "256 грязи переносятся одной порцией: предел скрипта — 256");
    }

    @Test
    void placeOne_CellInWarehouse_CraftsAndPlacesChest() {
        BlockCoord cell = new BlockCoord(20, 65, 20);

        List<ScriptCall> calls = calls(ChestPlans.placeOne(cell, "oak"));

        assertEquals(
                List.of("withdraw", "wait", "craft", "place_block"),
                calls.stream().map(ScriptCall::script).toList(),
                "Новый сундук делают из досок (с ожиданием досок) и ставят в клетку склада");
        ScriptCall craft = calls.stream()
                .filter(call -> "craft".equals(call.script()))
                .findFirst()
                .orElseThrow();
        ScriptCall place = calls.stream()
                .filter(call -> "place_block".equals(call.script()))
                .findFirst()
                .orElseThrow();
        assertEquals("minecraft:chest", craft.params().get("item"), "Крафтится сундук");
        assertEquals(cell.x(), place.params().get("x"), "Ставится в клетку склада по X");
    }

    @Test
    void breakOnly_EmptyCells_BreaksEveryCell() {
        BlockCoord partner = new BlockCoord(11, 64, 10);

        List<ScriptCall> calls = calls(ChestPlans.breakOnly(List.of(CHEST, partner)));

        assertEquals(2, calls.size(), "Двойной сундук ломается обеими клетками: иначе осталась бы половина");
        assertTrue(calls.stream().allMatch(call -> "clear_area".equals(call.script())), "Ломают, а не выносят");
    }

    private static ContainerSnapshot snapshot(Stack... stacks) {
        List<Stack> slots = new ArrayList<>(27);
        for (int slot = 0; slot < 27; slot++) {
            slots.add(null);
        }
        for (int index = 0; index < stacks.length; index++) {
            slots.set(index, stacks[index]);
        }
        return new ContainerSnapshot(CHEST, slots);
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return List.copyOf(calls);
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        switch (node) {
            case ScriptCall call -> calls.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, calls));
            case FallbackNode fallback -> fallback.steps().forEach(step -> collect(step, calls));
            default ->
                throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName() + " не разбирается");
        }
    }
}
