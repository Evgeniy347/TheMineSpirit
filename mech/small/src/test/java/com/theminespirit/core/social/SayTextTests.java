package com.theminespirit.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SayTextTests {
    @ParameterizedTest(name = "текст {0} не меняется")
    @ValueSource(strings = {"Привет, мир!", "Стройка ждёт", "x"})
    void normalize_PlainText_StaysUnchanged(String text) {
        assertEquals(
                text,
                SayText.normalize(text, 256),
                "Обычный текст обязан дойти до чата дословно: нормализация не имеет права искажать речь духа, иначе "
                        + "игрок слышит не то. При смене SayText.normalize проверь строку управляющих ниже в тесте");
    }

    @Test
    void normalize_ControlCharacters_CollapseToSingleSpace() {
        assertEquals(
                "Привет мир",
                SayText.normalize("Привет\n\n\tмир", 256),
                "Серия управляющих символов обязана схлопываться в один пробел, иначе в чате появится лес пустых "
                        + "строк и сообщение разъедется на несколько. При смене нормализации пробелов проверь строку "
                        + "длинной речи ниже в тесте");
    }

    @Test
    void normalize_TextLongerThanLimit_TruncatedToMaxLength() {
        String result = SayText.normalize("абвгдежзийклмнопрсту", 5);

        assertEquals(
                5,
                result.length(),
                "Длинная речь обязана обрезаться ровно до лимита: больше в чат не влезает, а обрезка вместо отказа "
                        + "сохраняет начало сообщения, иначе чат обрывает речь. При смене лимита проверь строку "
                        + "начала ниже в тесте");
        assertTrue(
                result.startsWith("абвгд"),
                "Обрезка обязана сохранять начало сообщения: слушатель должен увидеть первые слова, иначе обрезка "
                        + "съедает начало. Фактическое: " + result + ". При смене обрезки проверь строку пустой ниже "
                        + "в тесте");
    }

    @ParameterizedTest(name = "текст {0} отказывает")
    @ValueSource(strings = {"", "   ", "\n\t ", "\n"})
    void normalize_BlankText_IsRejected(String text) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SayText.normalize(text, 256),
                "Пустая после нормализации речь обязана отказывать: отправка пустой строки в чат — шум, а молчание "
                        + "скрыло бы ошибку плана, иначе игрок не поймёт. При смене проверки проверь строку текста "
                        + "ниже в тесте");

        assertTrue(
                error.getMessage().contains("пуст"),
                "Отказ обязан говорить, что речь пустая, иначе игрок не поймёт отказ. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь строку лимита ниже в тесте");
    }

    @ParameterizedTest(name = "лимит {0} отказывает")
    @ValueSource(ints = {0, -1, -256})
    void normalize_MaxLengthBelowOne_IsRejected(int maxLength) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SayText.normalize("Привет", maxLength),
                "Лимит меньше 1 обязан отказывать: сообщение без единого символа не выговорить, а 0 замолчало бы "
                        + "речь молча, иначе лимит проходит незамеченным. При смене проверки проверь строку текста "
                        + "ниже в тесте");

        assertTrue(
                error.getMessage().contains(String.valueOf(maxLength)),
                "Отказ обязан называть отклонённый лимит, иначе игрок не поймёт настройку. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь строку null ниже в тесте");
    }

    @Test
    void normalize_NullText_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> SayText.normalize(null, 256),
                "SayText.normalize(null) обязан отказывать: речь обязана прийти строкой, а null означает ошибку "
                        + "маппинга плана, иначе нормализация падает без причины. При смене проверки проверь строку "
                        + "выше в тесте");
    }
}
