package com.theminespirit.neoforge.lease;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LeaseStatesTests {
    @Test
    void state_PropertyWithoutEquals_RejectsText() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LeaseStates.state("minecraft:oak_stairs[half]"),
                "Свойство без '=' обязано падать: молчаливо потерянное свойство вернуло бы блок не так");

        assertTrue(
                error.getMessage().contains("имя=значение"),
                "Свойство без '=' обязано называться как испорченное: иначе откат STATE разберёт его как "
                        + "имя и подставит блок не так; сообщение " + error.getMessage());
    }

    @Test
    void state_TrailingComma_RejectsEmptyProperty() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LeaseStates.state("minecraft:oak_stairs[half=top,]"),
                "Пустое свойство после запятой обязано падать: молчаливый пропуск потерял бы свойство");

        assertTrue(
                error.getMessage().contains("имя=значение"),
                "Пустое свойство после запятой обязано отвергаться: молчаливый пропуск потерял бы свойство "
                        + "при откате STATE; сообщение " + error.getMessage());
    }

    @Test
    void state_BlankId_RejectsText() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LeaseStates.state(""),
                "Пустой id обязан падать: иначе откат STATE искал бы блок по пустому имени");

        assertTrue(
                error.getMessage().contains("Id блока пуст"),
                "Пустой id обязан называться причиной: иначе откат STATE искал бы блок по пустому имени; "
                        + "сообщение " + error.getMessage());
    }

    @Test
    void state_NullText_RejectsNull() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> LeaseStates.state(null),
                "null вместо строки состояния обязан падать с причиной, а не в недрах реестра");

        assertTrue(
                error.getMessage().contains("без неё блок не вернуть"),
                "null вместо строки состояния обязан отвергаться с причиной: иначе откат STATE упал бы "
                        + "в недрах реестра без следа о клетке; сообщение " + error.getMessage());
    }

    @Test
    void text_NullState_RejectsNull() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> LeaseStates.text(null),
                "null вместо состояния обязан падать с причиной: пустая строка не помнит, что возвращать");

        assertTrue(
                error.getMessage().contains("записывать нечего"),
                "null вместо состояния обязан отвергаться с причиной: иначе аренда записала бы пустую "
                        + "строку и откат STATE не знал бы, что возвращать; сообщение " + error.getMessage());
    }
}
