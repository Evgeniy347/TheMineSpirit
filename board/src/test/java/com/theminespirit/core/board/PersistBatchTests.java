package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersistBatchTests {
    @Test
    void begin_OutsideBatch_DefersNothing() {
        PersistBatch batch = new PersistBatch();

        assertFalse(
                batch.deferred(),
                "До пакета сохранение идёт сразу: одиночная заявка не ждёт пересбора"
                        + " Иначе тест begin_OutsideBatch_DefersNothing перестанет ловить эту поломку. При смене begin_OutsideBatch_DefersNothing проверь это утверждение.");
    }

    @Test
    void begin_InsideBatch_DefersUntilEnd() {
        PersistBatch batch = new PersistBatch();
        batch.begin();

        assertTrue(
                batch.deferred(),
                "Внутри пакета сохранение откладывается: иначе пересбор доски пишет диск " + "на каждую слитую заявку"
                        + " При смене begin_InsideBatch_DefersUntilEnd проверь это утверждение.");
        assertTrue(
                batch.end(),
                "Конец пакета — сигнал записать накопленное один раз"
                        + " Иначе тест begin_InsideBatch_DefersUntilEnd перестанет ловить эту поломку. При смене begin_InsideBatch_DefersUntilEnd проверь это утверждение.");
        assertFalse(
                batch.deferred(),
                "После конца пакета сохранение снова идёт сразу"
                        + " Иначе тест begin_InsideBatch_DefersUntilEnd перестанет ловить эту поломку. При смене begin_InsideBatch_DefersUntilEnd проверь это утверждение.");
    }

    @Test
    void nestedBatches_OnlyOuterEnd_AsksToSave() {
        PersistBatch batch = new PersistBatch();
        batch.begin();
        batch.begin();

        assertFalse(
                batch.end(),
                "Внутренний пакет не сохраняет: внешний ещё копит изменения"
                        + " Иначе тест nestedBatches_OnlyOuterEnd_AsksToSave перестанет ловить эту поломку. При смене nestedBatches_OnlyOuterEnd_AsksToSave проверь это утверждение.");
        assertTrue(
                batch.deferred(),
                "Пока внешний пакет открыт, сохранение отложено"
                        + " Иначе тест nestedBatches_OnlyOuterEnd_AsksToSave перестанет ловить эту поломку. При смене nestedBatches_OnlyOuterEnd_AsksToSave проверь это утверждение.");
        assertTrue(
                batch.end(),
                "Внешний пакет закрылся — пора сохранять"
                        + " Иначе тест nestedBatches_OnlyOuterEnd_AsksToSave перестанет ловить эту поломку. При смене nestedBatches_OnlyOuterEnd_AsksToSave проверь это утверждение.");
    }

    @Test
    void end_WithoutBegin_Fails() {
        PersistBatch batch = new PersistBatch();

        assertThrows(
                IllegalStateException.class,
                batch::end,
                "Конец пакета без начала — ошибка кода, а не повод молча сохранить: иначе пакет теряет смысл"
                        + " При смене end_WithoutBegin_Fails проверь это утверждение.");
    }
}
