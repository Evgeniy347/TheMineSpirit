package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class SseFormatTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void frame_GivenIdEventData_IsThreeLinesEndingWithBlankLine() {
        String frame = SseFormat.frame(7, "progress", "{\"step\":2}");

        assertEquals("id: 7\nevent: progress\ndata: {\"step\":2}\n\n", frame,
                "Кадр SSE — контракт: id:, event:, data: и пустая строка в конце");
    }

    @Test
    void keepalive_CommentLine_EndsWithBlankLine() {
        assertEquals(": keepalive\n\n", SseFormat.keepalive(),
                "Keepalive — комментарий-строка с пустой строкой в конце, без id/event/data");
    }

    @Test
    void frame_DataWithNewline_ThrowsInsteadOfBreakingStream() {
        assertThrows(IllegalArgumentException.class, () -> SseFormat.frame(1, "progress", "a\nb"),
                "Перевод строки внутри data разорвёт поток SSE: форматтер обязан отвергать такие данные");
    }

    @Property(tries = 200)
    void frame_AnyJsonPayload_ExactlyOneDataLineAndBlankLineTerminator(@ForAll("payloads") Map<String, String> payload)
            throws Exception {
        String json = MAPPER.writeValueAsString(payload);

        String frame = SseFormat.frame(1, "progress", json);

        String[] lines = frame.split("\n", -1);
        assertEquals(5, lines.length,
                "Кадр из трёх полей + пустая строка даёт 5 сегментов при split: " + frame);
        assertTrue(lines[0].startsWith("id: "), "Первая строка — id: " + frame);
        assertTrue(lines[1].startsWith("event: "), "Вторая строка — event: " + frame);
        assertTrue(lines[2].startsWith("data: "), "Третья строка — data: " + frame);
        assertEquals("", lines[3], "После data обязана идти пустая строка-разделитель: " + frame);
        assertEquals("", lines[4], "Кадр заканчивается переводом строки: " + frame);
    }

    @Provide
    Arbitrary<Map<String, String>> payloads() {
        return Arbitraries.maps(
                Arbitraries.strings().withChars('a', 'б', 'к').ofMinLength(1).ofMaxLength(8),
                Arbitraries.strings().withChars('a', 'б', '\n', '\t', '"', '\\', ' ').ofMinLength(0).ofMaxLength(16))
                .ofMinSize(0).ofMaxSize(4);
    }
}
