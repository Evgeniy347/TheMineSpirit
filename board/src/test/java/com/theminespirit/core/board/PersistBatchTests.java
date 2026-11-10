package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersistBatchTests {
    @Test
    void begin_OutsideBatch_DefersNothing() {
        PersistBatch batch = new PersistBatch();

        assertFalse(batch.deferred(), "До пакета сохранение идёт сразу: одиночная заявка не ждёт пересбора");
    }

    @Test
    void begin_InsideBatch_DefersUntilEnd() {
        PersistBatch batch = new PersistBatch();
        batch.begin();

        assertTrue(
                batch.deferred(),
                "Внутри пакета сохранение откладывается: иначе пересбор доски пишет диск " + "на каждую слитую заявку");
        assertTrue(batch.end(), "Конец пакета — сигнал записать накопленное один раз");
        assertFalse(batch.deferred(), "После конца пакета сохранение снова идёт сразу");
    }

    @Test
    void nestedBatches_OnlyOuterEnd_AsksToSave() {
        PersistBatch batch = new PersistBatch();
        batch.begin();
        batch.begin();

        assertFalse(batch.end(), "Внутренний пакет не сохраняет: внешний ещё копит изменения");
        assertTrue(batch.deferred(), "Пока внешний пакет открыт, сохранение отложено");
        assertTrue(batch.end(), "Внешний пакет закрылся — пора сохранять");
    }

    @Test
    void end_WithoutBegin_Fails() {
        PersistBatch batch = new PersistBatch();

        assertThrows(
                IllegalStateException.class,
                batch::end,
                "Конец пакета без начала — ошибка кода, а не повод молча сохранить: иначе пакет теряет смысл");
    }
}
