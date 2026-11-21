package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BreachAreas;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BreachPostTests {
    private static BreachPost.Facts facts(boolean occupied, boolean open, boolean reachable, boolean known) {
        return new BreachPost.Facts(occupied, open, reachable, known);
    }

    @Test
    void decide_CellOnWaterPocketCeiling_RememberedAsUnreachable() {
        assertEquals(
                BreachPost.Act.REMEMBER_UNREACHABLE,
                BreachPost.decide(facts(false, false, false, false)),
                "Клетка на потолке водяного кармана не даёт стоянки (живой случай -103,75,-411): задание "
                        + "«к клетке не подойти» падало каждый цикл. Как надо: запомнить точку как недостижимую "
                        + "для графа и не ставить задание"
                        + " Иначе тест decide_CellOnWaterPocketCeiling_RememberedAsUnreachable перестанет ловить эту поломку. При смене decide_CellOnWaterPocketCeiling_RememberedAsUnreachable проверь это утверждение.");
    }

    @Test
    void decide_UnreachableAlreadyKnown_IsNotRepeated() {
        assertEquals(
                BreachPost.Act.SKIP,
                BreachPost.decide(facts(false, false, false, true)),
                "Известную недостижимую точку не записываем снова каждый круг: иначе память провалов "
                        + "забивается одной клеткой до отказа и граф теряет другие"
                        + " При смене decide_UnreachableAlreadyKnown_IsNotRepeated проверь это утверждение.");
    }

    @Test
    void decide_ReachableCell_PostsRepair() {
        assertEquals(
                BreachPost.Act.POST,
                BreachPost.decide(facts(false, false, true, false)),
                "У клетки со стоянкой заделка ставится как раньше: проверка достижимости не запрещает ремонт"
                        + " Иначе тест decide_ReachableCell_PostsRepair перестанет ловить эту поломку. При смене decide_ReachableCell_PostsRepair проверь это утверждение.");
        assertEquals(
                BreachPost.Act.POST,
                BreachPost.decide(facts(false, false, true, true)),
                "Старая память о недостижимости не мешает: мир изменился, клетка достижима, ремонт нужен"
                        + " Иначе тест decide_ReachableCell_PostsRepair перестанет ловить эту поломку. При смене decide_ReachableCell_PostsRepair проверь это утверждение.");
    }

    @Test
    void decide_OpenOrFilledCell_IsNotPostedTwice() {
        assertEquals(
                BreachPost.Act.SKIP,
                BreachPost.decide(facts(false, true, false, false)),
                "Открытое задание не дублируется: два духа на одну клетку — лишняя работа и драка за место"
                        + " Иначе тест decide_OpenOrFilledCell_IsNotPostedTwice перестанет ловить эту поломку. При смене decide_OpenOrFilledCell_IsNotPostedTwice проверь это утверждение.");
        assertEquals(
                BreachPost.Act.CLEAR,
                BreachPost.decide(facts(true, false, false, false)),
                "Блок вернулся в клетку сам — пролом закрывается без задания"
                        + " Иначе тест decide_OpenOrFilledCell_IsNotPostedTwice перестанет ловить эту поломку. При смене decide_OpenOrFilledCell_IsNotPostedTwice проверь это утверждение.");
    }

    @Test
    void batch_MoreBreachesThanPortion_TakesOnlySmallestCells() {
        List<PlanBreachPost.Repair> ready = List.of(
                repair("world:plan_breach:3,64,0:9"),
                repair("world:plan_breach:1,64,0:9"),
                repair("world:plan_breach:2,64,0:9"));

        assertEquals(
                List.of("world:plan_breach:1,64,0:9", "world:plan_breach:2,64,0:9"),
                ids(PlanBreachPost.batch(ready, 2)),
                "Проломов на доску идёт порция, а не все разом: сотни world:plan_breach вытесняли снабжение "
                        + "с доски. Клетки берутся по порядку id, чтобы порция не зависела от порядка карты"
                        + " Иначе тест batch_MoreBreachesThanPortion_TakesOnlySmallestCells перестанет ловить эту поломку. При смене batch_MoreBreachesThanPortion_TakesOnlySmallestCells проверь это утверждение.");
    }

    @Test
    void batch_NoRoomLeft_IsEmpty() {
        assertEquals(
                List.of(),
                PlanBreachPost.batch(List.of(repair("world:plan_breach:1,64,0:9")), 0),
                "Порция уже занята открытыми заданиями — новых проломов не ставим: иначе пересбор снова "
                        + "добавит сотни заданий сверх общего предела доски"
                        + " При смене batch_NoRoomLeft_IsEmpty проверь это утверждение.");
    }

    @Test
    void openPrefixes_PlanBreach_WatchedForPortion() {
        assertTrue(
                WorldPost.OPEN_PREFIXES.contains(PlanBreachPost.PREFIX),
                "Префикс проломов обязан считаться в OpenPrefixes: без него openCount всегда 0, порция не "
                        + "кончается и сотни ремонтов снова забивают доску"
                        + " Иначе тест openPrefixes_PlanBreach_WatchedForPortion перестанет ловить эту поломку. При смене openPrefixes_PlanBreach_WatchedForPortion проверь это утверждение.");
    }

    @Test
    void target_Area_IsOneFillOfWholeBox() {
        BreachAreas.Area area = new BreachAreas.Area("dirt", 1, 64, 2, 3, 64, 4);

        TicketTarget target = PlanBreachPost.target(area);
        ScriptCall plan = (ScriptCall) target.plan();

        assertEquals(
                "fill",
                plan.script(),
                "Группа клеток заделывается одним fill, а не поштучными place_block"
                        + " Иначе тест target_Area_IsOneFillOfWholeBox перестанет ловить эту поломку. При смене target_Area_IsOneFillOfWholeBox проверь это утверждение.");
        assertEquals(
                Map.of(
                        "block", "dirt",
                        "x1", 1,
                        "y1", 64,
                        "z1", 2,
                        "x2", 3,
                        "y2", 64,
                        "z2", 4),
                plan.params(),
                "Границы fill совпадают с площадью пролома клетка в клетку: лишняя клетка засыпала бы воздух"
                        + " Иначе тест target_Area_IsOneFillOfWholeBox перестанет ловить эту поломку. При смене target_Area_IsOneFillOfWholeBox проверь это утверждение.");
        assertEquals(
                new BlockCoord(1, 64, 2),
                target.target(),
                "Точка задания — меньшая клетка площади: по ней стабилен id и работает память недостижимости"
                        + " Иначе тест target_Area_IsOneFillOfWholeBox перестанет ловить эту поломку. При смене target_Area_IsOneFillOfWholeBox проверь это утверждение.");
    }

    @Test
    void target_GrassArea_RefilledWithDirt() {
        BreachAreas.Area area = new BreachAreas.Area("minecraft:grass_block", 1, 64, 2, 1, 64, 2);

        ScriptCall plan = (ScriptCall) PlanBreachPost.target(area).plan();

        assertEquals(
                "minecraft:dirt",
                plan.params().get("block"),
                "Пролом дёрна заделывается землёй: дёрн лопатой не добыть, а схемы принимают землю за тот же грунт"
                        + " Иначе тест target_GrassArea_RefilledWithDirt перестанет ловить эту поломку. При смене target_GrassArea_RefilledWithDirt проверь это утверждение.");
    }

    private static PlanBreachPost.Repair repair(String id) {
        return new PlanBreachPost.Repair(
                new BoardTicket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        100,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(new BlockCoord(0, 64, 0)));
    }

    private static List<String> ids(List<PlanBreachPost.Repair> repairs) {
        return repairs.stream().map(repair -> repair.ticket().id()).toList();
    }
}
