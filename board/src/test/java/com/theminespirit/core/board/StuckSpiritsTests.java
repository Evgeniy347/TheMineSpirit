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
    void of_NoPathMarkExpired_SpiritGetsWorkAgain() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        stuck.drainChanges();

        assertTrue(
                stuck.isStuck(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS - 1),
                "До предела метка «пути нет» держится: выход ещё можно поставить");
        assertFalse(
                stuck.isStuck(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS),
                "«Пути нет» истекает, как и обычная метка: иначе idle-дух навсегда выпадает из раздачи "
                        + "(observe25 B1, 4 из 6 духов стояли 20 минут)");
        List<StuckSpirits.Change> changes = stuck.drainChanges();
        assertEquals(1, changes.size(), "Истечение метки «пути нет» пишется в лог: " + changes);
        assertTrue(
                changes.get(0).reason().startsWith("истекла"),
                "Причина истечения названа честно: " + changes.get(0).reason());
        stuck.see(SPIRIT, new BlockCoord(AT.x() + 7, AT.y(), AT.z()), 141 + BoardTuning.STUCK_TTL_TICKS, true);
        assertFalse(
                stuck.isStuck(SPIRIT, 141 + BoardTuning.STUCK_TTL_TICKS),
                "Выход на поверхность после истечения метки не заводит её заново");
    }

    @Test
    void mark_MovementThenNoPath_UpgradesButExpiresFromFirstTick() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.mark(SPIRIT, 160, "застрял: маршрут не найден");

        assertTrue(
                stuck.of(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS - 1)
                        .orElseThrow()
                        .noPath(),
                "Поздний провал «маршрут не найден» делает метку фактом «пути нет» для rescue");
        assertFalse(
                stuck.isStuck(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS),
                "Срок считается от первого застревания, а не продлевается каждым поздним провалом: "
                        + "иначе карусель провалов держит духа в ловушке вечно (observe25 B1)");
    }

    @Test
    void see_MovedInsidePit_UpdatesCoordinatesForWaitReason() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.drainChanges();
        BlockCoord moved = new BlockCoord(AT.x() + 2, AT.y() - 1, AT.z() + 3);

        stuck.see(SPIRIT, moved, 220);

        StuckSpirits.Stuck current = stuck.of(SPIRIT, 220).orElseThrow();
        assertEquals(
                moved,
                current.at(),
                "Метка называет текущее место духа: waitReason обязан показывать живые координаты, "
                        + "а не точку первой постановки (observe25 B15)");
        assertEquals(140, current.since(), "Тик эпизода остаётся первым: сдвиг внутри ямы не выход");
        assertTrue(stuck.drainChanges().isEmpty(), "Сдвиг внутри ямы не пишется как снятие метки");
    }

    @Test
    void restore_StaleNoPathMark_DroppedAtLoad() {
        StuckSpirits source = new StuckSpirits();
        source.see(SPIRIT, AT, 100);
        source.mark(SPIRIT, 140, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        long loaded = 140 + BoardTuning.STUCK_TTL_TICKS;

        StuckSpirits restored = new StuckSpirits();
        restored.restore(source.snapshot(), loaded);

        assertFalse(
                restored.isStuck(SPIRIT, loaded),
                "Метка «пути нет», пережившая свой срок до загрузки, снимается: иначе после рестарта дух "
                        + "снова стоит без работы (observe25 B1)");
        List<StuckSpirits.Change> changes = restored.drainChanges();
        assertTrue(
                changes.stream()
                        .anyMatch(change -> !change.marked() && change.reason().contains("до загрузки")),
                "Снятие устаревшей метки при загрузке попадает в лог: " + changes);
    }

    @Test
    void restore_FreshNoPathMark_ExpiresByOriginalTickNotLoadTick() {
        StuckSpirits source = new StuckSpirits();
        source.see(SPIRIT, AT, 100);
        source.mark(SPIRIT, 140, "маршрут не найден: цель BlockCoord[x=-171, y=71, z=-489]");
        long loaded = 140 + BoardTuning.STUCK_TTL_TICKS - 50;

        StuckSpirits restored = new StuckSpirits();
        restored.restore(source.snapshot(), loaded);

        assertTrue(
                restored.isStuck(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS - 1),
                "Свежая метка переживает загрузку: выход ещё можно поставить");
        assertFalse(
                restored.isStuck(SPIRIT, 140 + BoardTuning.STUCK_TTL_TICKS),
                "Загрузка не переносит срок метки на свой тик: иначе каждый рестарт продлевал бы запрет "
                        + "навсегда (observe25 B1)");
    }

    @Test
    void rescued_ReturnToSamePit_Relapses() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.rescued(SPIRIT, 200);

        assertTrue(
                stuck.relapsed(SPIRIT, new BlockCoord(AT.x() + 2, AT.y(), AT.z()), 400),
                "Дух снова в той же ловушке после спасения: память неудач не даёт крутить self-rescue "
                        + "по кругу (observe25 B6)");
    }

    @Test
    void rescued_FarFromPit_NoRelapse() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.rescued(SPIRIT, 200);

        assertFalse(
                stuck.relapsed(SPIRIT, new BlockCoord(AT.x() + 40, AT.y(), AT.z()), 400),
                "Издалека это новый случай, а не возврат в ту же ловушку: память не запирает духа навсегда");
    }

    @Test
    void rescued_AfterWindow_NoRelapse() {
        StuckSpirits stuck = new StuckSpirits();
        stuck.see(SPIRIT, AT, 100);
        stuck.mark(SPIRIT, 140, "провал движения");
        stuck.rescued(SPIRIT, 200);

        assertFalse(
                stuck.relapsed(SPIRIT, AT, 200 + 12_001),
                "Окно памяти конечно: спустя время попытку спасения можно повторить");
    }
}
