package com.theminespirit.neoforge.crater;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CraterWithdrawTests {
    @Test
    void restore_InterruptedWithdraw_NeedsStartAgain() {
        CraterWithdraw withdraw = new CraterWithdraw();
        withdraw.started();
        Map<String, Object> saved = withdraw.save(Map.of("placed", 0));

        CraterWithdraw restored = new CraterWithdraw();
        restored.restore(saved);

        assertTrue(
                restored.needsStart(),
                "Прерванный заход за материалом после перезапуска обязан начаться заново: иначе засыпка идёт без "
                        + "земли, падает по NO_MATERIAL, и воронка остаётся открытой; сохранение несёт только "
                        + "признак завершения захода");
    }

    @Test
    void restore_FinishedWithdraw_DoesNotRepeat() {
        CraterWithdraw withdraw = new CraterWithdraw();
        withdraw.started();
        withdraw.finished();
        Map<String, Object> saved = withdraw.save(Map.of("placed", 3));

        CraterWithdraw restored = new CraterWithdraw();
        restored.restore(saved);

        assertFalse(
                restored.needsStart(),
                "Завершённый заход не повторяют: повторное withdraw после перезапуска дёргает склад без нужды и "
                        + "задерживает засыпку");
    }
}
