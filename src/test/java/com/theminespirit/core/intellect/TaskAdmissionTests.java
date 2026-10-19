package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.task.SpiritReading;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskAdmissionTests {
    private static final String FARM = "harvest_crop";
    private static final String GOTO = "goto";
    private static final long OWN_GENERATION = 7;

    @Test
    void decide_RunningTaskWithoutNamedScript_Skips() {
        // Дух только что получил задачу (статус RUNNING), но тело скрипта ещё не создано, и currentScript
        // пуст. Интеллект в том же серверном тике выдаёт занятие: без запрета оно перебивает задачу, и
        // наблюдатель видит чужой провал. Запущенная задача обязана оставаться неприкосновенной.
        assertEquals(
                TaskAdmission.Verdict.SKIP_BUSY,
                TaskAdmission.decide(
                        Optional.empty(),
                        RunStatus.RUNNING,
                        null,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        FARM),
                "запущенная задача без имени скрипта не перебивается чужим занятием");
    }

    @Test
    void decide_ForeignNamedTask_Skips() {
        assertEquals(
                TaskAdmission.Verdict.SKIP_FOREIGN,
                TaskAdmission.decide(
                        Optional.of(GOTO),
                        RunStatus.RUNNING,
                        null,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        FARM),
                "чужая (не выданная интеллектом) задача не перебивается: её итог принадлежит владельцу");
    }

    @Test
    void decide_SameScript_Skips() {
        assertEquals(
                TaskAdmission.Verdict.SKIP_SAME,
                TaskAdmission.decide(Optional.of(FARM), RunStatus.RUNNING, FARM, OWN_GENERATION, OWN_GENERATION, FARM),
                "то же занятие повторно не выдаётся: дух уже им занят");
    }

    @Test
    void decide_OwnIssuedTask_CancelsAndStartsNew() {
        assertEquals(
                TaskAdmission.Verdict.CANCEL_AND_START,
                TaskAdmission.decide(Optional.of(FARM), RunStatus.RUNNING, FARM, OWN_GENERATION, OWN_GENERATION, GOTO),
                "своё прежнее занятие интеллект вправе сменить: он его же и выдал");
    }

    @Test
    void decide_SameScriptButForeignGeneration_Skips() {
        // Скрипт/игрок/тест запустил задачу с тем же именем, что планировщик выдавал раньше.
        // Поколение задачи другое: это не наша задача, и отменять её нельзя — иначе чужой итог
        // (провал, причина, прогресс) пропадает, а wait-задача теста снимается.
        assertEquals(
                TaskAdmission.Verdict.SKIP_FOREIGN,
                TaskAdmission.decide(
                        Optional.of(FARM), RunStatus.RUNNING, FARM, OWN_GENERATION, OWN_GENERATION + 1, GOTO),
                "совпало только имя скрипта, но задачу поставил не планировщик: её не отменяем");
    }

    @Test
    void decide_OwnScriptWithUnknownGeneration_Skips() {
        assertEquals(
                TaskAdmission.Verdict.SKIP_FOREIGN,
                TaskAdmission.decide(
                        Optional.of(FARM),
                        RunStatus.RUNNING,
                        FARM,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        GOTO),
                "поколение неизвестно — владельца задачи не доказать, чужую задачу не отменяем");
    }

    @Test
    void decide_IdleOrTerminalTask_Starts() {
        assertEquals(
                TaskAdmission.Verdict.START,
                TaskAdmission.decide(
                        Optional.empty(),
                        RunStatus.SUCCEEDED,
                        null,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        FARM),
                "успешно завершившегося духа можно занять");
        assertEquals(
                TaskAdmission.Verdict.START,
                TaskAdmission.decide(
                        Optional.empty(),
                        RunStatus.FAILED,
                        null,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        FARM),
                "после провала духа можно занять (откат решает TaskBackoff)");
    }

    @Test
    void decide_NoTaskAtAll_Starts() {
        assertEquals(
                TaskAdmission.Verdict.START,
                TaskAdmission.decide(
                        Optional.empty(),
                        null,
                        null,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        FARM),
                "дух без задачи (статуса нет) занимается: null-статус — это «нет задачи», а не ошибка");
    }

    @Test
    void decide_NullArguments_AreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> TaskAdmission.decide(
                        null,
                        RunStatus.RUNNING,
                        null,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        FARM),
                "currentScript null отклоняется: отсутствие — это Optional.empty()");
        assertThrows(
                NullPointerException.class,
                () -> TaskAdmission.decide(
                        Optional.empty(),
                        RunStatus.RUNNING,
                        null,
                        SpiritReading.UNKNOWN_GENERATION,
                        SpiritReading.UNKNOWN_GENERATION,
                        null),
                "запрошенный скрипт null отклоняется: неясно, что выдаём");
    }
}
