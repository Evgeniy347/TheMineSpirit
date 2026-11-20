package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.reload.DataTables;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MineDescentSyncTests {
    @Test
    void currentData_SchemeAndDescentTable_Agree() {
        assertEquals(
                Optional.empty(),
                DescentSync.mismatch(scheme(), DescentData.loadDefault()),
                "Схема mine_descent_v1 и mining/descent.json обязаны совпадать по цели и шагу факелов: иначе спуск "
                        + "и safe_mine копают до разных глубин, а ствол выходит темнее схемы; при смене "
                        + "repeat.until/torch или descent.json проверь Optional.empty() здесь");
    }

    @Test
    void targetY_Diverged_IsNamed() {
        DescentData data = DescentData.loadDefault();
        String text = DescentSync.mismatch(scheme(), new DescentData(data.targetY() + 8, data.torchInterval()))
                .orElseThrow();

        assertTrue(
                text.contains("targetY"),
                "Расхождение глубины обязано называть targetY: иначе живой лог не покажет, что чинить; при смене "
                        + "текста DescentSync проверь слово targetY");
        assertTrue(
                text.contains("mine_descent_v1.txt"),
                "Текст обязан называть схему mine_descent_v1.txt: без этого непонятно, какой источник править, и правку начнут не с той стороны; при смене текста проверь имя схемы");
        assertTrue(
                text.contains("descent.json"),
                "Текст обязан называть таблицу descent.json: без этого правка уйдёт не туда, а живой лог не покажет второй источник; при смене текста проверь имя таблицы");
    }

    @Test
    void torchInterval_Diverged_IsNamed() {
        DescentData data = DescentData.loadDefault();
        DescentData spaced = new DescentData(data.targetY(), data.torchInterval() + 1);

        String text = DescentSync.mismatch(scheme(), spaced).orElseThrow();

        assertTrue(
                text.contains("интервал факелов"),
                "Расхождение шага факелов обязано называть причину: иначе в логе только «разъехалось» и правку "
                        + "не с чего начать; при смене текста проверь «интервал факелов»");
        assertTrue(
                text.contains(String.valueOf(spaced.torchInterval())),
                "Текст называет значение таблицы: без этого не видно, какое число пришло из descent.json, и сверку не с чем сопоставить; при смене формата проверь подстановку torchInterval");
    }

    private static Schematic scheme() {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(TicketPlans.MINE_DESCENT_SCHEME)
                .orElseThrow(() -> new AssertionError("Нет схемы спуска " + TicketPlans.MINE_DESCENT_SCHEME))
                .schematic();
    }
}
