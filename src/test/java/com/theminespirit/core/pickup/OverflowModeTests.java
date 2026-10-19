package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverflowModeTests {

    @Test
    void forSpirit_FollowsPlayer_DropsCheapest() {
        assertEquals(
                OverflowMode.BY_VALUE,
                OverflowMode.forSpirit(true),
                "Следуя за игроком, дух выбрасывает самое дешёвое и продолжает путь: уходить на базу "
                        + "из сопровождения значит бросить игрока");
    }

    @Test
    void forSpirit_WorksAtBase_ReturnsToBase() {
        assertEquals(
                OverflowMode.RETURN_TO_BASE,
                OverflowMode.forSpirit(false),
                "Вне сопровождения полный инвентарь останавливает работу и ведёт духа на склад: "
                        + "выбрасывать добычу посреди работы — потеря труда");
    }

    @Test
    void parse_ReturnToBase_ReadsMode() {
        assertEquals(
                OverflowMode.RETURN_TO_BASE,
                OverflowMode.parse("return_to_base"),
                "Режим 'return_to_base' обязан читаться из параметра плана: иначе задание доски "
                        + "не сможет поручить духу именно сдачу на склад");
    }

    @Test
    void parse_UnknownMode_ThrowsNamingAllowed() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> OverflowMode.parse("склад"),
                "Неизвестный режим обязан падать: молчаливый выбор режима потерял бы добычу");

        assertTrue(
                error.getMessage().contains("return_to_base"),
                "Сообщение обязано перечислять допустимые режимы, фактически: " + error.getMessage());
    }
}
