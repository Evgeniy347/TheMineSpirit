package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import org.junit.jupiter.api.Test;

class LocalCraftPlansTests {
    @Test
    void craft_Anvil_UsesNearbyStationWithoutGoto() {
        TaskNode plan = TicketPlans.craft("minecraft:anvil", 1);

        assertTrue(
                plan instanceof ScriptCall,
                "Обычный крафт — один локальный script call: иначе лишний goto заставляет духа идти к общему двору и терять доступную станцию. При смене craft_Anvil_UsesNearbyStationWithoutGoto проверь TicketPlans.craft.");
        assertEquals(
                "craft",
                ((ScriptCall) plan).script(),
                "Крафт наковальни использует существующий скрипт craft: иначе задание не выполняется. При смене craft_Anvil_UsesNearbyStationWithoutGoto проверь TicketPlans.craft.");
    }

    @Test
    void toolCraft_StoneShovel_AcquiresThenCraftsWithoutGoto() {
        TaskNode plan = ToolCraftPlan.ofItem("minecraft:stone_shovel", TicketTarget.at(new BlockCoord(10, 64, -5)));

        assertTrue(
                plan instanceof SequenceNode,
                "План инструмента объединяет материалы и крафт: иначе теряется порядок снабжения. При смене toolCraft_StoneShovel_AcquiresThenCraftsWithoutGoto проверь ToolCraftPlan.ofItem.");
        SequenceNode sequence = (SequenceNode) plan;
        assertTrue(
                sequence.steps().get(0) instanceof SequenceNode,
                "Снабжение и крафт собраны во внутреннюю последовательность: иначе проверка порядка больше не видит реальный план инструмента. При смене toolCraft_StoneShovel_AcquiresThenCraftsWithoutGoto проверь Leftovers.with.");
        SequenceNode work = (SequenceNode) sequence.steps().get(0);
        assertFalse(
                work.steps().stream().anyMatch(step -> step instanceof ScriptCall call && "goto".equals(call.script())),
                "Инструмент не ходит к общему craftPoint: иначе дальний двор блокирует замену лопаты, хотя acquire внутри поселения дистанционный, а верстак ищется рядом. При смене toolCraft_StoneShovel_AcquiresThenCraftsWithoutGoto проверь ToolCraftPlan и DispatchPick.localCraft.");
        assertTrue(
                work.steps().get(work.steps().size() - 1) instanceof ScriptCall last && "craft".equals(last.script()),
                "Последний рабочий шаг крафтит лопату после acquire: иначе задание закрывается без инструмента. При смене toolCraft_StoneShovel_AcquiresThenCraftsWithoutGoto проверь ToolCraftPlan.ofItem.");
    }
}
