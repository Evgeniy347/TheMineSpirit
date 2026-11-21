package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpiritReadingTests {
    private static final RunProgress PROGRESS = new RunProgress(2, 4, "chop_tree", null, null);
    private static final Object RESULT = Map.of("chopped", 2);

    @Test
    void living_WithResult_KeepsAllFieldsAndResult() {
        SpiritReading reading = SpiritReading.living(RunStatus.SUCCEEDED, PROGRESS, null, RESULT);

        assertTrue(
                reading.present() && reading.alive(),
                "живой дух присутствует и жив"
                        + " Иначе тест living_WithResult_KeepsAllFieldsAndResult перестанет ловить эту поломку. При смене living_WithResult_KeepsAllFieldsAndResult проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                reading.status(),
                "статус обязан сохраниться"
                        + " Иначе тест living_WithResult_KeepsAllFieldsAndResult перестанет ловить эту поломку. При смене living_WithResult_KeepsAllFieldsAndResult проверь это утверждение.");
        assertEquals(
                PROGRESS,
                reading.progress(),
                "прогресс обязан сохраниться"
                        + " Иначе тест living_WithResult_KeepsAllFieldsAndResult перестанет ловить эту поломку. При смене living_WithResult_KeepsAllFieldsAndResult проверь это утверждение.");
        assertEquals(
                RESULT,
                reading.result(),
                "итог обязан дойти до поля result без искажений"
                        + " Иначе тест living_WithResult_KeepsAllFieldsAndResult перестанет ловить эту поломку. При смене living_WithResult_KeepsAllFieldsAndResult проверь это утверждение.");
    }

    @Test
    void living_WithoutResult_HasNullResult() {
        SpiritReading reading = SpiritReading.living(RunStatus.RUNNING, PROGRESS, null);

        assertNull(
                reading.result(),
                "перегрузка без итога обязана давать null: прежний вызов не должен внезапно получать мусор"
                        + " Иначе тест living_WithoutResult_HasNullResult перестанет ловить эту поломку. При смене living_WithoutResult_HasNullResult проверь это утверждение.");
    }

    @Test
    void failed_WithReason_HasNullResult() {
        SpiritReading reading = SpiritReading.failed("споткнулся", PROGRESS);

        assertNull(
                reading.result(),
                "провал не несёт итога успеха: null отличает 'итога нет' от 'итог — пустая строка'"
                        + " Иначе тест failed_WithReason_HasNullResult перестанет ловить эту поломку. При смене failed_WithReason_HasNullResult проверь это утверждение.");
        assertEquals(
                RunStatus.FAILED,
                reading.status(),
                "провал обязан нести статус FAILED"
                        + " Иначе тест failed_WithReason_HasNullResult перестанет ловить эту поломку. При смене failed_WithReason_HasNullResult проверь это утверждение.");
    }

    @Test
    void absentAndDead_SpiritReading_HasNullResult() {
        assertNull(
                SpiritReading.absent().result(),
                "отсутствующий дух не имеет итога"
                        + " Иначе тест absentAndDead_SpiritReading_HasNullResult перестанет ловить эту поломку. При смене absentAndDead_SpiritReading_HasNullResult проверь это утверждение.");
        assertNull(
                SpiritReading.dead().result(),
                "погибший дух не имеет итога"
                        + " Иначе тест absentAndDead_SpiritReading_HasNullResult перестанет ловить эту поломку. При смене absentAndDead_SpiritReading_HasNullResult проверь это утверждение.");
    }

    @Test
    void withGeneration_ReadingWithTag_KeepsResult() {
        SpiritReading reading = SpiritReading.living(RunStatus.SUCCEEDED, PROGRESS, null, RESULT);

        SpiritReading tagged = reading.withGeneration(7);

        assertEquals(
                7,
                tagged.generation(),
                "withGeneration обязан проставить поколение"
                        + " Иначе тест withGeneration_ReadingWithTag_KeepsResult перестанет ловить эту поломку. При смене withGeneration_ReadingWithTag_KeepsResult проверь это утверждение.");
        assertEquals(
                RESULT,
                tagged.result(),
                "withGeneration обязан сохранять итог: иначе reconciler потеряет его при проверке поколения"
                        + " При смене withGeneration_ReadingWithTag_KeepsResult проверь это утверждение.");
    }
}
