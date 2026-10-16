package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EatParamsTests {
    @Test
    void from_NoItem_EmptyOptional() {
        assertEquals(Optional.empty(), EatParams.from(Map.of()).item(),
                "Без item дух ест лучшее из инвентаря: пустой параметр — это «что найдёшь», а не ошибка");
    }

    @Test
    void from_BlankItem_EmptyOptional() {
        assertEquals(Optional.empty(), EatParams.from(Map.of("item", "  ")).item(),
                "Пустое имя еды равнозначно отсутствию: планировщик может прислать пустую строку");
    }

    @Test
    void from_NamedItem_KeepsId() {
        assertEquals(Optional.of("minecraft:bread"), EatParams.from(Map.of("item", "minecraft:bread")).item(),
                "Названный предмет сохраняется: дух ест именно его, а не лучшее");
    }

    @Test
    void from_NonTextItem_Throws() {
        assertThrows(IllegalArgumentException.class, () -> EatParams.from(Map.of("item", 5)),
                "Число вместо id еды отвергается: валидатор плана обязан поймать тип до запуска");
    }

    @Test
    void constructor_BlankPresent_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new EatParams(Optional.of(" ")),
                "Пустая строка как заданная еда отвергается: иначе дух искал бы предмет с пустым именем");

        assertTrue(error.getMessage().contains("еды"), "Сообщение называет еду: " + error.getMessage());
    }
}
