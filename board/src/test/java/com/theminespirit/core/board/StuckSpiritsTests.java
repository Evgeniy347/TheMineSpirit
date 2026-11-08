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
    void see_WanderingInsideDeepPit_KeepsMark() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.drainChanges();

        stuck.see(SPIRIT, new BlockCoord(AT.x() + 7, AT.y() - 2, AT.z() + 6), 220);
        stuck.see(SPIRIT, new BlockCoord(AT.x() - 9, AT.y() - 4, AT.z() - 5), 240);

        assertTrue(
                stuck.isStuck(SPIRIT, 240),
                "Блуждание по яме глубже порога — не выход: метка держится, иначе rescue закрывается без спасения "
                        + "(observe24 B1)");
        assertTrue(
                stuck.drainChanges().isEmpty(),
                "Сдвиг внутри ловушки не пишется как снятие метки: лог обязан показывать реальный выход");
    }

    @Test
    void see_ReachedSurface_ReleasesWithEscapeReason() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.drainChanges();

        stuck.see(SPIRIT, new BlockCoord(AT.x() + 7, AT.y(), AT.z()), 220, true);

        assertFalse(stuck.isStuck(SPIRIT, 220), "Выход на поверхность снимает метку: дух больше не в ловушке");
        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Снятие метки пишется в лог: " + changes);
        assertFalse(changes.get(0).marked(), "Запись говорит, что метка снята");
        assertEquals(220, changes.get(0).tick(), "Запись называет тик снятия");
        assertTrue(
                changes.get(0).reason().startsWith("вышел на поверхность"),
                "Причина снятия — выход на поверхность, а не сдвиг: "
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
    void of_NoPathMark_ExpiresByTtl() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        long before = 140 + BoardTuning.STUCK_TTL_TICKS - 1;
        long after = 140 + BoardTuning.STUCK_TTL_TICKS;

        assertTrue(stuck.isStuck(SPIRIT, before), "До предела метка «пути нет» держится: дух не выпадает из графа");
        assertFalse(
                stuck.isStuck(SPIRIT, after),
                "«Пути нет» истекает по времени, как и любая метка: иначе дух навсегда исключён из работы "
                        + "(observe25 B1)");
        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(2, changes.size(), "Постановка и истечение пишутся в лог: " + changes);
        assertTrue(
                changes.get(1).reason().contains("пути нет"),
                "Причина истечения называет, что метка была про отсутствие пути: "
                        + changes.get(1).reason());
    }

    @Test
    void mark_MovementThenNoPath_UpgradesAndExpires() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.mark(SPIRIT, 160, "застрял: маршрут не найден");
        long after = 140 + BoardTuning.STUCK_TTL_TICKS;

        assertTrue(stuck.of(SPIRIT, 159).orElseThrow().noPath(), "Позднее «маршрут не найден» делает метку фактом");
        assertFalse(
                stuck.isStuck(SPIRIT, after),
                "Но и факт «пути нет» не вечен: по пределу метка снимается (observe25 B1)");
    }

    @Test
    void see_NoPathCellMoved_ReleasesMark() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "маршрут не найден");
        stuck.drainChanges();

        stuck.see(SPIRIT, new BlockCoord(AT.x() + 1, AT.y(), AT.z()), 200);

        assertFalse(stuck.isStuck(SPIRIT, 200), "Дух сдвинулся с места метки — «пути нет» перепроверяют");
        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Снятие по смене позиции пишется в лог: " + changes);
        assertTrue(
                changes.get(0).reason().contains("сдвинулся"),
                "Причина называет смену позиции: " + changes.get(0).reason());
    }

    @Test
    void see_NoPathCellSame_KeepsMark() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        stuck.drainChanges();

        stuck.see(SPIRIT, AT, 240, true);

        assertTrue(
                stuck.isStuck(SPIRIT, 240),
                "Проверка «на поверхности» не снимает «пути нет»: иначе метка ставится и снимается в том же "
                        + "тике, а дух каждые 15 секунд получает новую задачу (observe25 B5)");
        assertTrue(stuck.drainChanges().isEmpty(), "Снятия не было — лог не пополняется");
    }

    @Test
    void restore_NoPathMark_DoesNotSurviveRestart() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "маршрут не найден");
        List<BoardState.Stuck> snapshot = stuck.snapshot();
        stuck.drainChanges();

        StuckSpirits restarted = new StuckSpirits();
        restarted.restore(snapshot, 20_000);

        assertFalse(
                restarted.isStuck(SPIRIT, 20_001),
                "Метка не переживает рестарт: после загрузки дух снова получает работу, а не ждёт спасения "
                        + "навсегда (observe25 B1)");
        List<StuckSpirits.Change> changes = restarted.drainChanges();
        assertEquals(1, changes.size(), "Сброс метки при загрузке виден в логе: " + changes);
        assertTrue(
                changes.get(0).reason().contains("не переживает рестарт"),
                "Причина сброса названа честно: " + changes.get(0).reason());
    }
}
