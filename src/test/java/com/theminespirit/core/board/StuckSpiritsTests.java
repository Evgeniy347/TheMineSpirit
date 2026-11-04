package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StuckSpiritsTests {
    private static final UUID SPIRIT = UUID.fromString("c48663b4-da59-43d9-9804-81a680580300");
    private static final BlockCoord AT = new BlockCoord(-188, 67, -516);

    @Test
    void mark_WithReason_RecordsChangeAndTick() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);

        stuck.mark(SPIRIT, 140, "застрял (-188.5, 67.0, -516) у двора");

        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Постановка метки пишется в лог изменений ровно один раз: " + changes);
        assertEquals(SPIRIT, changes.get(0).spirit(), "Запись метки принадлежит застрявшему духу");
        assertEquals(140, changes.get(0).tick(), "Запись называет тик постановки");
        assertEquals(AT, changes.get(0).at(), "Запись называет место застревания");
        assertTrue(changes.get(0).marked(), "Запись говорит, что метка поставлена");
        assertTrue(
                changes.get(0).reason().contains("двор"),
                "Причина постановки сохраняется целиком: " + changes.get(0).reason());
    }

    @Test
    void mark_Repeated_DoesNotDuplicateLog() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);

        stuck.mark(SPIRIT, 140, "первый провал");
        stuck.mark(SPIRIT, 300, "второй провал");

        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Повторный провал не переставляет метку и не заливает лог: " + changes);
        assertEquals(140, stuck.of(SPIRIT, 300).orElseThrow().since(), "Тик застревания остаётся первым");
    }

    @Test
    void see_MovingBeyondArea_ReleasesWithMovementReason() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.drainChanges();

        stuck.see(SPIRIT, new BlockCoord(AT.x() + StuckSpirits.AREA_RADIUS + 1, AT.y(), AT.z()), 220);

        assertFalse(stuck.isStuck(SPIRIT, 220), "Дух вышел из области колебания — метка снимается по факту движения");
        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Снятие метки пишется в лог: " + changes);
        assertFalse(changes.get(0).marked(), "Запись говорит, что метка снята");
        assertEquals(220, changes.get(0).tick(), "Запись называет тик снятия");
        assertTrue(
                changes.get(0).reason().startsWith("сдвинулся"),
                "Причина снятия — движение духа, а не тайм-аут: "
                        + changes.get(0).reason());
    }

    @Test
    void release_WithReason_RecordsChange() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.drainChanges();

        stuck.release(SPIRIT, 400, "выход выполнен заданием rescue:" + SPIRIT);

        assertFalse(stuck.isStuck(SPIRIT, 400), "Явное освобождение снимает метку");
        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Явное освобождение пишется в лог: " + changes);
        assertTrue(
                changes.get(0).reason().contains("rescue:" + SPIRIT),
                "Причина снятия называет выполненный выход: " + changes.get(0).reason());
    }

    @Test
    void of_OldMark_ExpiresInsteadOfBlockingForever() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.drainChanges();

        assertTrue(
                stuck.isStuck(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS - 1),
                "До предела метка держится: выход ещё можно поставить");
        assertFalse(
                stuck.isStuck(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS),
                "По пределу метка снимается: застрявший дух не выпадает из графа навсегда (observe22)");
        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Истечение метки пишется в лог: " + changes);
        assertTrue(
                changes.get(0).reason().startsWith("истекла"),
                "Причина истечения названа честно: " + changes.get(0).reason());
    }
}
