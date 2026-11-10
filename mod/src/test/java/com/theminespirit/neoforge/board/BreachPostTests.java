package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                        + "для графа и не ставить задание");
    }

    @Test
    void decide_UnreachableAlreadyKnown_IsNotRepeated() {
        assertEquals(
                BreachPost.Act.SKIP,
                BreachPost.decide(facts(false, false, false, true)),
                "Известную недостижимую точку не записываем снова каждый круг: иначе память провалов "
                        + "забивается одной клеткой до отказа и граф теряет другие");
    }

    @Test
    void decide_ReachableCell_PostsRepair() {
        assertEquals(
                BreachPost.Act.POST,
                BreachPost.decide(facts(false, false, true, false)),
                "У клетки со стоянкой заделка ставится как раньше: проверка достижимости не запрещает ремонт");
        assertEquals(
                BreachPost.Act.POST,
                BreachPost.decide(facts(false, false, true, true)),
                "Старая память о недостижимости не мешает: мир изменился, клетка достижима, ремонт нужен");
    }

    @Test
    void decide_OpenOrFilledCell_IsNotPostedTwice() {
        assertEquals(
                BreachPost.Act.SKIP,
                BreachPost.decide(facts(false, true, false, false)),
                "Открытое задание не дублируется: два духа на одну клетку — лишняя работа и драка за место");
        assertEquals(
                BreachPost.Act.CLEAR,
                BreachPost.decide(facts(true, false, false, false)),
                "Блок вернулся в клетку сам — пролом закрывается без задания");
    }
}
