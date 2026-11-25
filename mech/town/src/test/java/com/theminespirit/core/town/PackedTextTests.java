package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PackedTextTests {
    @Test
    void pack_RussianDrawio_RoundTrips() {
        String text = "<mxfile><diagram>план поселения · ревизия 7</diagram></mxfile>";

        String back = PackedText.unpack(PackedText.pack(text));

        assertEquals(
                text,
                back,
                "Сжатие и распаковка обязаны сохранять текст карты байт в байт: иначе после перезапуска мира "
                        + "участки и стены теряются; получено " + back
                        + " Иначе тест pack_RussianDrawio_RoundTrips перестанет ловить эту поломку. При смене "
                        + "pack_RussianDrawio_RoundTrips проверь кириллицу и XML");
    }

    @Test
    void pack_LargeText_ShrinksBytes() {
        String text = "<mxCell tmKind=\"plot\"/>".repeat(2000);

        byte[] packed = PackedText.pack(text);

        assertEquals(
                text,
                PackedText.unpack(packed),
                "Большая карта обязана распаковываться без потерь: иначе NBT теряет план после сохранения; "
                        + "получено " + packed.length + " байт"
                        + " Иначе тест pack_LargeText_ShrinksBytes перестанет ловить эту поломку. При смене "
                        + "pack_LargeText_ShrinksBytes проверь размер и содержимое");
    }

    @Test
    void unpack_EmptyBytes_Rejected() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> PackedText.unpack(new byte[0]),
                "Пустые байты обязаны отклоняться понятной ошибкой: иначе мир молча стартует без карты; "
                        + "Иначе тест unpack_EmptyBytes_Rejected перестанет ловить эту поломку. При смене "
                        + "unpack_EmptyBytes_Rejected проверь текст ошибки");

        assertEquals(
                "Сжатый текст пуст: сохранённые данные потеряны",
                failure.getMessage(),
                "Ошибка пустого архива обязана прямо говорить о потере данных: получено " + failure.getMessage()
                        + " Иначе тест unpack_EmptyBytes_Rejected перестанет ловить эту поломку. При смене "
                        + "unpack_EmptyBytes_Rejected проверь сообщение");
    }

    @Test
    void unpack_NotGzip_Rejected() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> PackedText.unpack("не gzip".getBytes(StandardCharsets.UTF_8)),
                "Мусор вместо архива обязан отклоняться, а не превращаться в пустую карту: иначе повреждённый "
                        + "NBT молча сносит план; Иначе тест unpack_NotGzip_Rejected перестанет ловить эту поломку. "
                        + "При смене unpack_NotGzip_Rejected проверь тип ошибки");

        assertTrue(
                failure.getMessage().startsWith("Сжатый текст не распакован"),
                "Ошибка обязана начинаться с причины распаковки: получено " + failure.getMessage()
                        + " Иначе тест unpack_NotGzip_Rejected перестанет ловить эту поломку. При смене "
                        + "unpack_NotGzip_Rejected проверь начало сообщения");
    }
}
