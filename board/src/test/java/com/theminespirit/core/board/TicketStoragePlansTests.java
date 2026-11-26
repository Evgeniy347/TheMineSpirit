package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TicketStoragePlansTests {
    private static final BlockCoord SITE = new BlockCoord(-179, 68, -515);
    private static final BlockCoord CHEST = new BlockCoord(-181, 68, -524);

    @Test
    void supplySite_WithDepositPoint_GoesToThatContainer() {
        TicketTarget target = TicketTarget.at(SITE).withSupply("oak_planks", 48).withDeposit(CHEST);

        SequenceNode plan = assertInstanceOf(
                SequenceNode.class,
                TicketPlans.plan(BoardCase.SUPPLY_SITE, target),
                "план сдачи обязан быть последовательностью: иначе виден только первый шаг"
                        + " При смене supplySite_WithDepositPoint_GoesToThatContainer проверь это утверждение.");
        List<ScriptCall> calls =
                plan.steps().stream().map(ScriptCall.class::cast).toList();

        assertEquals(
                List.of("goto", "acquire", "goto", "deposit"),
                calls.stream().map(ScriptCall::script).toList(),
                "после добычи дух идёт к выбранному сундуку и только там сдаёт: иначе падает «рядом нет "
                        + "допустимого контейнера» (аномалия 3)"
                        + " При смене supplySite_WithDepositPoint_GoesToThatContainer проверь это утверждение.");
        assertEquals(
                Map.of("x", CHEST.x(), "y", CHEST.y(), "z", CHEST.z()),
                calls.get(2).params(),
                "второй переход ведёт в точку с допустимым контейнером, а не в пустое место сдачи"
                        + " Иначе тест supplySite_WithDepositPoint_GoesToThatContainer перестанет ловить эту поломку. При смене supplySite_WithDepositPoint_GoesToThatContainer проверь это утверждение.");
        assertEquals(
                "warehouse",
                calls.get(3).params().get("container"),
                "deposit выбирает свободный сундук склада в момент сдачи: закреплённый при постановке сундук "
                        + "к этому времени уже полон, и перенос идёт «0 из N»"
                        + " Иначе тест supplySite_WithDepositPoint_GoesToThatContainer перестанет ловить эту поломку. При смене supplySite_WithDepositPoint_GoesToThatContainer проверь это утверждение.");
    }

    @Test
    void supplySite_NoDepositPoint_KeepsWarehouseAtSite() {
        TaskNode plan =
                TicketPlans.plan(BoardCase.SUPPLY_SITE, TicketTarget.at(SITE).withSupply("dirt", 4));

        ScriptCall deposit = find(plan, "deposit");

        assertEquals(
                "warehouse",
                deposit.params().get("container"),
                "без выбранного контейнера сдача всё равно идёт на склад поселения — туда, где есть место"
                        + " Иначе тест supplySite_NoDepositPoint_KeepsWarehouseAtSite перестанет ловить эту поломку. При смене supplySite_NoDepositPoint_KeepsWarehouseAtSite проверь это утверждение.");
        assertEquals(
                Map.of("x", SITE.x(), "y", SITE.y(), "z", SITE.z()),
                find(plan, "goto").params(),
                "первый переход ведёт к месту задания"
                        + " Иначе тест supplySite_NoDepositPoint_KeepsWarehouseAtSite перестанет ловить эту поломку. При смене supplySite_NoDepositPoint_KeepsWarehouseAtSite проверь это утверждение.");
    }

    @Test
    void restockFood_WithDepositPoint_WalksToContainerFirst() {
        TicketTarget target = TicketTarget.at(SITE).withSupply("bread", 8).withDeposit(CHEST);

        SequenceNode plan = assertInstanceOf(
                SequenceNode.class,
                TicketPlans.plan(BoardCase.RESTOCK_FOOD, target),
                "план еды обязан быть последовательностью «дойти — взять»: иначе дух берёт из пустоты"
                        + " При смене restockFood_WithDepositPoint_WalksToContainerFirst проверь это утверждение.");
        List<ScriptCall> calls =
                plan.steps().stream().map(ScriptCall.class::cast).toList();

        assertEquals(
                List.of("goto", "withdraw"),
                calls.stream().map(ScriptCall::script).toList(),
                "еда берётся у сундука, до которого дух сначала доходит (аномалия 5: withdraw «nearest» "
                        + "падал при голоде 0.85)"
                        + " Иначе тест restockFood_WithDepositPoint_WalksToContainerFirst перестанет ловить эту поломку. При смене restockFood_WithDepositPoint_WalksToContainerFirst проверь это утверждение.");
        assertEquals(
                CHEST.x() + "," + CHEST.y() + "," + CHEST.z(),
                calls.get(1).params().get("container"),
                "withdraw берёт еду из выбранного контейнера, а не из первого попавшегося рядом"
                        + " Иначе тест restockFood_WithDepositPoint_WalksToContainerFirst перестанет ловить эту поломку. При смене restockFood_WithDepositPoint_WalksToContainerFirst проверь это утверждение.");
    }

    @Test
    void restockFood_NoDepositPoint_WithdrawsNearest() {
        ScriptCall withdraw = find(
                TicketPlans.plan(BoardCase.RESTOCK_FOOD, TicketTarget.at(SITE).withSupply("bread", 4)), "withdraw");

        assertEquals(
                "nearest",
                withdraw.params().get("container"),
                "без выбранной точки еды поиск идёт рядом"
                        + " Иначе тест restockFood_NoDepositPoint_WithdrawsNearest перестанет ловить эту поломку. При смене restockFood_NoDepositPoint_WithdrawsNearest проверь это утверждение.");
        assertTrue(
                withdraw.params().get("radius") instanceof Integer,
                "radius остаётся в запросе withdraw"
                        + " Иначе тест restockFood_NoDepositPoint_WithdrawsNearest перестанет ловить эту поломку. При смене restockFood_NoDepositPoint_WithdrawsNearest проверь это утверждение.");
    }

    @Test
    void gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS, TicketTarget.at(SITE).withSupply("farmland", 5));
        List<ScriptCall> calls = calls(plan);

        assertEquals(
                List.of("goto", "withdraw", "withdraw", "till"),
                calls.stream().map(ScriptCall::script).toList(),
                "Пашню делают тяпкой на месте, а не добывают предмет farmland: иначе дух ищет в мире "
                        + "несуществующий блок и проваливает graph:till_soil"
                        + " При смене gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem проверь это утверждение.");
        assertTrue(
                calls.stream()
                        .filter(call -> "withdraw".equals(call.script()))
                        .allMatch(call -> "*_hoe".equals(call.params().get("item"))),
                "Тяпку берут из сундука, а не крафтят в обход графа: " + calls
                        + " Иначе тест gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem перестанет ловить эту поломку. При смене gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem проверь это утверждение.");
        assertEquals(
                5,
                calls.get(3).params().get("count"),
                "Вспахать нужно ровно столько клеток, сколько просят"
                        + " Иначе тест gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem перестанет ловить эту поломку. При смене gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem проверь это утверждение.");
        assertTrue(
                calls.stream().noneMatch(call -> "acquire".equals(call.script())),
                "Ни одного acquire farmland: предмет пашни не существует: " + calls
                        + " Иначе тест gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem перестанет ловить эту поломку. При смене gatherMaterials_Farmland_TillsSoilInsteadOfGatheringItem проверь это утверждение.");
    }

    @Test
    void gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS, TicketTarget.at(SITE).withSupply("dirt_path", 20));
        List<ScriptCall> calls = calls(plan);

        assertEquals(
                List.of("goto", "withdraw", "withdraw", "gather"),
                calls.stream().map(ScriptCall::script).toList(),
                "Тропу топчут лопатой по земле, а не добывают предмет dirt_path: иначе дух идёт за "
                        + "несуществующим предметом и проваливает сад/улицы"
                        + " При смене gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem проверь это утверждение.");
        assertTrue(
                calls.stream()
                        .filter(call -> "withdraw".equals(call.script()))
                        .allMatch(call -> "*_shovel".equals(call.params().get("item"))),
                "Лопату берут из сундука: " + calls
                        + " Иначе тест gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem перестанет ловить эту поломку. При смене gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem проверь это утверждение.");
        assertEquals(
                "dirt_path",
                calls.get(3).params().get("block"),
                "Топчут именно тропинку"
                        + " Иначе тест gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem перестанет ловить эту поломку. При смене gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem проверь это утверждение.");
        assertEquals(
                20,
                calls.get(3).params().get("count"),
                "Топчут столько клеток, сколько просят"
                        + " Иначе тест gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem перестанет ловить эту поломку. При смене gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem проверь это утверждение.");
        assertTrue(
                calls.stream().noneMatch(call -> "acquire".equals(call.script())),
                "Дорожку не добывают предметом dirt_path: " + calls
                        + " Иначе тест gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem перестанет ловить эту поломку. При смене gatherMaterials_DirtPath_TramplesGroundInsteadOfGatheringItem проверь это утверждение.");
    }

    @Test
    void gatherMaterials_BreadCraft_AcquiresWheatAndCraftsBread() {
        TicketTarget target =
                TicketTarget.at(SITE).withCraft("minecraft:bread", 2).withCount(2);

        List<ScriptCall> calls = calls(TicketPlans.plan(BoardCase.GATHER_MATERIALS, target));
        ScriptCall acquire = calls.stream()
                .filter(call -> "acquire".equals(call.script()))
                .findFirst()
                .orElseThrow();
        ScriptCall craft = calls.stream()
                .filter(call -> "craft".equals(call.script()))
                .findFirst()
                .orElseThrow();

        assertEquals(
                "wheat",
                acquire.params().get("item"),
                "Перед хлебом план берёт пшеницу: иначе CraftScript начнёт без сырья и билет снова упадёт. "
                        + "При смене gatherMaterials_BreadCraft_AcquiresWheatAndCraftsBread проверь "
                        + "ToolCraftPlan.materials, graph.json craft_bread и GraphCrafting.way.");
        assertEquals(
                6,
                acquire.params().get("count"),
                "Два хлеба расходуют шесть пшеницы: иначе одна партия зерна будет использована дважды и "
                        + "второй крафт останется без материалов. При смене "
                        + "gatherMaterials_BreadCraft_AcquiresWheatAndCraftsBread проверь BoardReserve и yield craft_bread.");
        assertEquals(
                "minecraft:bread",
                craft.params().get("item"),
                "Последний шаг крафтит хлеб, а не добывает абстрактный food: иначе граф зациклится на "
                        + "невозможном acquire bread. При смене gatherMaterials_BreadCraft_AcquiresWheatAndCraftsBread "
                        + "проверь GraphCrafting.item и TicketStoragePlans.gatherMaterials.");
        assertEquals(
                2,
                craft.params().get("count"),
                "Крафт выполняет ровно заказанные два хлеба: иначе складский минимум не закроется. При смене "
                        + "gatherMaterials_BreadCraft_AcquiresWheatAndCraftsBread проверь ticket.count и StoreMinimums.");
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return calls;
    }

    private static ScriptCall find(TaskNode node, String script) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return calls.stream()
                .filter(call -> call.script().equals(script))
                .findFirst()
                .orElseThrow(() -> new AssertionError("в плане нет шага '" + script + "': " + calls));
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }
}
