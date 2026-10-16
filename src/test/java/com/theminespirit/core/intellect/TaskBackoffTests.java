package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskBackoffTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void backoff_FailedScript_BlockedFor200Ticks() {
        TaskBackoff backoff = new TaskBackoff();
        backoff.noteFailure(SPIRIT, "harvest_crop", 1000);

        assertTrue(backoff.blocked(SPIRIT, "harvest_crop", 1000),
                "Сразу после провала скрипт заблокирован: иначе интеллект перезапустит его на следующем пересчёте");
        assertTrue(backoff.blocked(SPIRIT, "harvest_crop", 1199),
                "За тик до конца отката скрипт ещё заблокирован: иначе окно отката короче заявленного");
        assertFalse(backoff.blocked(SPIRIT, "harvest_crop", 1200),
                "Ровно через 200 тиков скрипт снова выдаётся: иначе дух простаивает после отката зря");
    }

    @Test
    void backoff_OtherSpiritOrScript_NotBlocked() {
        TaskBackoff backoff = new TaskBackoff();
        backoff.noteFailure(SPIRIT, "harvest_crop", 1000);

        assertFalse(backoff.blocked(OTHER, "harvest_crop", 1000),
                "Провал чужого духа не блокирует: иначе один невезучий дух остановит всё поселение");
        assertFalse(backoff.blocked(SPIRIT, "chop_tree", 1000),
                "Провал одного скрипта не блокирует другой: иначе неудачная ферма отменит и рубку");
    }

    @Test
    void backoff_RepeatedFailure_ExtendsFromLatestTick() {
        TaskBackoff backoff = new TaskBackoff();
        backoff.noteFailure(SPIRIT, "harvest_crop", 1000);
        backoff.noteFailure(SPIRIT, "harvest_crop", 1150);

        assertTrue(backoff.blocked(SPIRIT, "harvest_crop", 1199),
                "Повторный провал продлевает откат от свежего тика: иначе дух долбит закрытую дверь по расписанию");
        assertFalse(backoff.blocked(SPIRIT, "harvest_crop", 1350),
                "Продлённый откат тоже истекает: иначе повторный провал банит скрипт навсегда");
    }

    @Test
    void backoff_ExpiredMarks_PrunedOnNote() {
        TaskBackoff backoff = new TaskBackoff();
        backoff.noteFailure(SPIRIT, "harvest_crop", 0);
        backoff.noteFailure(OTHER, "chop_tree", 10000);

        assertEquals(1, backoff.size(),
                "Протухшие откаты чистятся при новой записи: иначе карта растёт с каждым провалом вечно");
    }

    @Test
    void backoff_BadArguments_Throw() {
        TaskBackoff backoff = new TaskBackoff();

        assertThrows(IllegalArgumentException.class, () -> backoff.noteFailure(null, "harvest_crop", 0),
                "Откат без духа не записать: провал принадлежит конкретному духу");
        assertThrows(IllegalArgumentException.class, () -> backoff.noteFailure(SPIRIT, "  ", 0),
                "Откат без скрипта не записать: проваливается конкретная задача");
        assertThrows(IllegalArgumentException.class, () -> backoff.noteFailure(SPIRIT, "harvest_crop", -1),
                "Откат в отрицательном тике не записать: время идёт только вперёд");
        assertThrows(IllegalArgumentException.class, () -> backoff.blocked(null, "harvest_crop", 0),
                "Откат некого спросить: дух обязан быть указан");
        assertThrows(IllegalArgumentException.class, () -> backoff.blocked(SPIRIT, null, 0),
                "Откат нечего спросить: проверяется конкретный скрипт");
    }
}
