package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunHaltTests {
    @Test
    void needed_Running_IsFalse() {
        assertFalse(
                RunHalt.needed(RunStatus.RUNNING),
                "Идущий прогон не останавливают: иначе дух бросит работу на середине шага, а MoveControl "
                        + "перестанет вести его по маршруту. При смене RunHalt проверь живой dig_out: "
                        + "прогон продолжается, пока RUNNING");
    }

    @Test
    void needed_Succeeded_IsTrue() {
        assertTrue(
                RunHalt.needed(RunStatus.SUCCEEDED),
                "После успеха движение сбрасывают: иначе последняя точка скрипта остаётся в MoveControl "
                        + "и дух идёт к ней по инерции, наворачивая круги без задания (IDLE-спины 272–364 "
                        + "градуса, окно 15). При смене завершения прогона проверь IDLE-духа: он стоит, "
                        + "а move не задан");
    }

    @Test
    void needed_Failed_IsTrue() {
        assertTrue(
                RunHalt.needed(RunStatus.FAILED),
                "После провала движение тоже сбрасывают: иначе дух бежит к недостижимой точке упавшего "
                        + "задания и возвращается по кругу. При смене RunHalt проверь прогон у стены: "
                        + "после FAILED move обнулён");
    }

    @Test
    void needed_Cancelled_IsTrue() {
        assertTrue(
                RunHalt.needed(RunStatus.CANCELLED),
                "Отменённый прогон не продолжает путь: иначе отмена по доске не останавливает духа и он "
                        + "доходит до старой цели. При смене отмены проверь духа после снятия задания: "
                        + "шаги прекращаются сразу");
    }
}
