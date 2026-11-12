package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverloadPostTests {
    @Test
    void overloaded_SlotsAtLimit_RaisesUnload() {
        assertTrue(
                OverloadPost.overloaded(29, 36, 20),
                "свободных слотов меньше storage.minFreePercent (20 % от 36 — 7)  — граф ставит выгрузку уровнем vital, выше"
                        + " обычной работы (сервер 12.11: spirit-1 носил 487 земли, выгрузка с базой 14 не выигрывала"
                        + " у расчистки). Сломается: духи бегают с полным инвентарём, дроп пропадает. Проверить при"
                        + " смене: graph.d/store.json inventory_overloaded и SettlementEvents");
    }

    @Test
    void overloaded_FewSlots_NoEvent() {
        assertFalse(
                OverloadPost.overloaded(28, 36, 20),
                "свободно ровно 20 % (8 из 36) — ещё не перегруз: выгрузка не вытесняет работу раньше времени. Сломается: духи"
                        + " бросают задания и ходят к сундуку каждые несколько блоков. Проверить при смене: порог");
    }
}
