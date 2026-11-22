package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class ProtectionMemoTests {
    @Test
    void certify_SameStamp_StaysKnown() {
        ProtectionMemo memo = new ProtectionMemo();
        BlockCoord cell = new BlockCoord(1, 64, 2);
        memo.refresh(7);
        memo.certify(cell);
        memo.refresh(7);

        assertTrue(
                memo.certified(cell),
                "Клетка без защиты не проверяется повторно, пока ревизии аренды и журнала те же: иначе"
                        + " проверка защиты снова идёт по всей площади расчистки каждый такт"
                        + " Иначе тест certify_SameStamp_StaysKnown перестанет ловить эту поломку. При смене certify_SameStamp_StaysKnown проверь это утверждение.");
    }

    @Test
    void refresh_NewStamp_ForgetsKnownCells() {
        ProtectionMemo memo = new ProtectionMemo();
        BlockCoord cell = new BlockCoord(1, 64, 2);
        memo.refresh(7);
        memo.certify(cell);
        memo.refresh(8);

        assertFalse(
                memo.certified(cell),
                "Смена ревизии аренды или плана сбрасывает памятку: иначе новая аренда не защитила бы"
                        + " клетку, которую дух уже считает свободной"
                        + " Иначе тест refresh_NewStamp_ForgetsKnownCells перестанет ловить эту поломку. При смене refresh_NewStamp_ForgetsKnownCells проверь это утверждение.");
    }

    @Test
    void certify_OtherCell_NotKnown() {
        ProtectionMemo memo = new ProtectionMemo();
        memo.refresh(7);
        memo.certify(new BlockCoord(1, 64, 2));

        assertFalse(
                memo.certified(new BlockCoord(5, 64, 6)),
                "Памятка хранит только проверенные клетки: соседняя клетка проверяется заново и может быть защищена"
                        + " Иначе тест certify_OtherCell_NotKnown перестанет ловить эту поломку. При смене certify_OtherCell_NotKnown проверь это утверждение.");
    }
}
