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
                List.of("withdraw", "withdraw", "deposit", "deposit", "unload", "clear_area", "collect_drops"),
                scripts,
                "Непустой сундук: все предметы вынесены в склад одним заходом, и только потом сломан: " + scripts
                        + " Иначе тест demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking проверь это утверждение.");
        assertTrue(
                scripts.indexOf("deposit") > scripts.lastIndexOf("withdraw"),
                "Сначала забирают всё из сундука, потом несут на склад: иначе лишние ходки на каждый тип: " + scripts
                        + " При смене demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking проверь это утверждение.");
        assertEquals(
                "10,64,10",
                calls.get(0).params().get("container"),
                "Забирают именно из этого сундука, а не из ближайшего"
                        + " Иначе тест demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking проверь это утверждение.");
        assertEquals(
                "warehouse",
                calls.get(2).params().get("container"),
                "Вещи кладут в склад поселения"
                        + " Иначе тест demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyChest_WithdrawsEveryItemBeforeBreaking проверь это утверждение.");
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
                "20 типов забирают за один заход к сундуку: " + scripts
                        + " Иначе тест demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse перестанет ловить эту поломку. При смене demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse проверь это утверждение.");
        assertEquals(
                "withdraw",
                scripts.get(19),
                "Все 20 заборов идут подряд, до первого похода на склад: " + scripts
                        + " Иначе тест demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse перестанет ловить эту поломку. При смене demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse проверь это утверждение.");
        assertEquals(
                "deposit",
                scripts.get(20),
                "После забора — перенос на склад: " + scripts
                        + " Иначе тест demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse перестанет ловить эту поломку. При смене demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse проверь это утверждение.");
        assertEquals(
                43,
                scripts.size(),
                "На 20 типов — 20 заборов, 20 переносов, подготовка инвентаря, слом и подбор сундука: " + scripts
                        + " Иначе тест demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse перестанет ловить эту поломку. При смене demolish_ManyItemTypes_PacksOneChestVisitBeforeWarehouse проверь это утверждение.");
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
                List.of("withdraw", "withdraw", "deposit", "deposit", "unload", "clear_area", "collect_drops"),
                scripts,
                "300 грязи не влезают в предел скрипта 256: два забора и два переноса, фазы не перемешиваются: "
                        + scripts
                        + " Иначе тест demolish_MoveLargerThanScriptLimit_SplitsButKeepsBothPhases перестанет ловить эту поломку. При смене demolish_MoveLargerThanScriptLimit_SplitsButKeepsBothPhases проверь это утверждение.");
    }

    @Test
    void demolish_CoverAboveChest_BreaksCoverBeforeWithdraw() {
        BlockCoord cover = new BlockCoord(CHEST.x(), CHEST.y() + 1, CHEST.z());

        List<ScriptCall> calls =
                calls(ChestPlans.demolish(snapshot(new Stack("dirt", 0, 5, 64)), List.of(CHEST), null, List.of(cover)));

        List<String> scripts = calls.stream().map(ScriptCall::script).toList();
        assertEquals(
                List.of("clear_area", "collect_drops", "withdraw", "deposit", "unload", "clear_area", "collect_drops"),
                scripts,
                "Блок над сундуком снимается до открытия: с закрытой крышкой вещи не забрать: " + scripts
                        + " Иначе тест demolish_CoverAboveChest_BreaksCoverBeforeWithdraw перестанет ловить эту поломку. При смене demolish_CoverAboveChest_BreaksCoverBeforeWithdraw проверь это утверждение.");
        assertEquals(
                65,
                calls.get(0).params().get("y1"),
                "Снимают именно клетку над сундуком"
                        + " Иначе тест demolish_CoverAboveChest_BreaksCoverBeforeWithdraw перестанет ловить эту поломку. При смене demolish_CoverAboveChest_BreaksCoverBeforeWithdraw проверь это утверждение.");
        assertTrue(
                scripts.indexOf("collect_drops") < scripts.indexOf("withdraw"),
                "Дроп с крышки подбирают до похода в сундук: " + scripts
                        + " Иначе тест demolish_CoverAboveChest_BreaksCoverBeforeWithdraw перестанет ловить эту поломку. При смене demolish_CoverAboveChest_BreaksCoverBeforeWithdraw проверь это утверждение.");
    }

    @Test
    void demolish_EmptyChest_BreaksWithoutTransfers() {
        List<ScriptCall> calls = calls(ChestPlans.demolish(snapshot(), List.of(CHEST)));

        assertEquals(
                List.of("unload", "clear_area", "collect_drops"),
                calls.stream().map(ScriptCall::script).toList(),
                "До слома освобождают переполненный инвентарь, затем ломают сундук и сразу подбирают его предмет"
                        + " Иначе тест demolish_EmptyChest_BreaksWithoutTransfers перестанет ловить эту поломку. При смене demolish_EmptyChest_BreaksWithoutTransfers проверь это утверждение.");
    }

    @Test
    void demolish_ExplicitDestination_PreparesInventoryAtSameChest() {
        BlockCoord destination = new BlockCoord(20, 64, 20);

        List<ScriptCall> calls = calls(ChestPlans.demolish(snapshot(), List.of(CHEST), destination));

        assertEquals(
                "unload",
                calls.get(0).script(),
                "Разгрузка предшествует слому"
                        + " Иначе тест demolish_ExplicitDestination_PreparesInventoryAtSameChest перестанет ловить эту поломку. При смене demolish_ExplicitDestination_PreparesInventoryAtSameChest проверь это утверждение.");
        assertEquals(
                "20,64,20",
                calls.get(0).params().get("container"),
                "Явно заданный склад используется и для подготовки инвентаря"
                        + " Иначе тест demolish_ExplicitDestination_PreparesInventoryAtSameChest перестанет ловить эту поломку. При смене demolish_ExplicitDestination_PreparesInventoryAtSameChest проверь это утверждение.");
        assertEquals(
                false,
                calls.get(0).params().get("force"),
                "Свободный инвентарь не требует разгрузки"
                        + " Иначе тест demolish_ExplicitDestination_PreparesInventoryAtSameChest перестанет ловить эту поломку. При смене demolish_ExplicitDestination_PreparesInventoryAtSameChest проверь это утверждение.");
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
        assertEquals(
                1,
                withdraws,
                "256 грязи переносятся одной порцией: предел скрипта — 256"
                        + " Иначе тест demolish_LargeStack_SplitsTransfersByScriptLimit перестанет ловить эту поломку. При смене demolish_LargeStack_SplitsTransfersByScriptLimit проверь это утверждение.");
    }

    @Test
    void placeOne_CellInWarehouse_CraftsAndPlacesChest() {
        BlockCoord cell = new BlockCoord(20, 65, 20);

        List<ScriptCall> calls = calls(ChestPlans.placeOne(cell));

        assertEquals(
                List.of("withdraw", "craft", "acquire", "place_block"),
                calls.stream().map(ScriptCall::script).toList(),
                "Новый сундук берёт доски любой породы, крафтит его, при нехватке — acquire, и ставит в клетку"
                        + " Иначе тест placeOne_CellInWarehouse_CraftsAndPlacesChest перестанет ловить эту поломку. При смене placeOne_CellInWarehouse_CraftsAndPlacesChest проверь это утверждение.");
        ScriptCall withdraw = calls.stream()
                .filter(call -> "withdraw".equals(call.script()))
                .findFirst()
                .orElseThrow();
        ScriptCall craft = calls.stream()
                .filter(call -> "craft".equals(call.script()))
                .findFirst()
                .orElseThrow();
        ScriptCall place = calls.stream()
                .filter(call -> "place_block".equals(call.script()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "*_planks",
                withdraw.params().get("item"),
                "Доски забирают шаблоном любой породы, а не конкретной"
                        + " Иначе тест placeOne_CellInWarehouse_CraftsAndPlacesChest перестанет ловить эту поломку. При смене placeOne_CellInWarehouse_CraftsAndPlacesChest проверь это утверждение.");
        assertEquals(
                "minecraft:chest",
                craft.params().get("item"),
                "Крафтится сундук"
                        + " Иначе тест placeOne_CellInWarehouse_CraftsAndPlacesChest перестанет ловить эту поломку. При смене placeOne_CellInWarehouse_CraftsAndPlacesChest проверь это утверждение.");
        assertEquals(
                cell.x(),
                place.params().get("x"),
                "Ставится в клетку склада по X"
                        + " Иначе тест placeOne_CellInWarehouse_CraftsAndPlacesChest перестанет ловить эту поломку. При смене placeOne_CellInWarehouse_CraftsAndPlacesChest проверь это утверждение.");
        assertTrue(
                calls.stream()
                        .flatMap(call -> call.params().values().stream())
                        .filter(String.class::isInstance)
                        .noneMatch(value -> ((String) value).matches("[a-z_]+_planks")),
                "В плане нет доски конкретной породы: иначе смешанный запас (4 oak + 4 jungle) считался бы готовым "
                        + "к сундуку, а план ждал бы отсутствующую породу и задание повисло бы в WAITING_RESOURCES. "
                        + "При смене шаблона забора досок в ChestPlans.placeOne проверь ChestPlansTests и живую "
                        + "постановку сундука: " + calls);
    }

    @Test
    void breakOnly_EmptyCells_BreaksEveryCell() {
        BlockCoord partner = new BlockCoord(11, 64, 10);

        List<ScriptCall> calls = calls(ChestPlans.breakOnly(List.of(CHEST, partner)));

        assertEquals(
                List.of("unload", "clear_area", "collect_drops", "unload", "clear_area", "collect_drops"),
                calls.stream().map(ScriptCall::script).toList(),
                "Перед каждой половиной готовят инвентарь, после неё сразу подбирают содержимое"
                        + " Иначе тест breakOnly_EmptyCells_BreaksEveryCell перестанет ловить эту поломку. При смене breakOnly_EmptyCells_BreaksEveryCell проверь это утверждение.");
        for (int index : List.of(0, 3)) {
            assertEquals(
                    "warehouse",
                    calls.get(index).params().get("container"),
                    "Излишек сдаётся на склад"
                            + " Иначе тест breakOnly_EmptyCells_BreaksEveryCell перестанет ловить эту поломку. При смене breakOnly_EmptyCells_BreaksEveryCell проверь это утверждение.");
            assertEquals(
                    false,
                    calls.get(index).params().get("force"),
                    "Свободный инвентарь не требует разгрузки"
                            + " Иначе тест breakOnly_EmptyCells_BreaksEveryCell перестанет ловить эту поломку. При смене breakOnly_EmptyCells_BreaksEveryCell проверь это утверждение.");
        }
        for (int index : List.of(2, 5)) {
            assertEquals(
                    "return_to_base",
                    calls.get(index).params().get("overflow"),
                    "При переполнении вещи относят на склад, а не выбрасывают ради более дорогих"
                            + " Иначе тест breakOnly_EmptyCells_BreaksEveryCell перестанет ловить эту поломку. При смене breakOnly_EmptyCells_BreaksEveryCell проверь это утверждение.");
            assertTrue(
                    ((Number) calls.get(index).params().get("radius")).intValue() >= 5,
                    "Подбор охватывает сундук, сломанный с расстояния рук, и разлёт содержимого"
                            + " Иначе тест breakOnly_EmptyCells_BreaksEveryCell перестанет ловить эту поломку. При смене breakOnly_EmptyCells_BreaksEveryCell проверь это утверждение.");
        }
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
