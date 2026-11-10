package com.theminespirit.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LogRecordTests {
    @Test
    void of_EveryLogType_RequiredFieldsAreAllowedAndCommonFieldsPresent() {
        for (LogType type : LogType.values()) {
            LogSchema.Spec spec = LogSchema.of(type);
            assertTrue(
                    spec.allowed().containsAll(spec.required()),
                    "Каждое обязательное поле обязано быть разрешённым: иначе запись типа " + type.json()
                            + " невозможно собрать без нарушения схемы");
            assertTrue(
                    spec.allowed().containsAll(Set.of("ts", "tick", "build", "source")),
                    "Общие колонки ts, tick, build и source обязаны быть в схеме каждого типа: " + type.json());
        }
    }

    @Test
    void builder_MissingRequiredField_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LogRecord.builder(LogType.BLOCK_CHANGE, 1, 1, "1.0", "live")
                        .put("actor_type", "spirit")
                        .put("actor", "дух-1")
                        .put("action", "break")
                        .put("block_before", "minecraft:stone")
                        .at(1, 2, 1)
                        .build(),
                "Запись блока без block_after обязана отвергаться схемой: без состояния после изменения нельзя понять, что получилось");

        assertTrue(
                error.getMessage().contains("блок")
                        || error.getMessage().contains("block_after")
                        || error.getMessage().contains("block_before"),
                "Запись блока без координаты или состояния не должна собираться: без них загрузчик не ответит, "
                        + "какая клетка изменилась. Сообщение: " + error.getMessage());
    }

    @Test
    void builder_UnknownField_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LogRecord.builder(LogType.MESSAGE, 1, 1, "1.0", "live")
                        .put("level", "INFO")
                        .put("text", "строка")
                        .put("secret", "лишнее")
                        .build(),
                "Лишнее поле обязано отвергаться схемой: иначе оно молча пропадёт при загрузке в SQLite");

        assertTrue(
                error.getMessage().contains("secret"),
                "Лишнее поле должно отвергаться схемой: иначе оно молча пропадёт при загрузке в SQLite. "
                        + "Сообщение: " + error.getMessage());
    }

    @Test
    void fromJson_UnknownType_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> typeOf("no_such_type"),
                "Неизвестный type в JSONL не должен попадать в базу: загрузчик обязан назвать его в ошибке");

        assertTrue(
                error.getMessage().contains("no_such_type"),
                "Неизвестный type в JSONL не должен попадать в базу: загрузчик обязан назвать его в ошибке. "
                        + "Сообщение: " + error.getMessage());
    }

    @Test
    void record_ValidFields_SerializesTypeAndCommonColumns() {
        LogRecord record = LogRecord.builder(LogType.ROUTE, 1_700_000_000_000L, 77, "1.0+build", "live")
                .spirit("дух-1", "id-1")
                .put("purpose", "dig_out")
                .put("from_x", 0)
                .put("from_y", 1)
                .put("from_z", 2)
                .put("to_x", 3)
                .put("to_y", 4)
                .put("to_z", 5)
                .put("status", "FOUND")
                .put("nodes", 12)
                .put("ms", 30)
                .cause(LogCause.CHOSEN)
                .build();

        Map<String, Object> parsed = TestJson.read(record.toJson());

        assertEquals("route", parsed.get("type"), "Тип записи обязан быть в JSONL: загрузчик по нему выбирает таблицу");
        assertEquals(77, ((Number) parsed.get("tick")).intValue(), "Тик обязан сохраниться числом");
        assertEquals("дух-1", parsed.get("spirit"), "Имя духа обязано сохраниться в JSONL");
        assertEquals("chosen", parsed.get("cause"), "Причина обязана писаться кодом закрытого набора");
    }

    @Test
    void blockChange_AllFormats_HaveRequiredColumns() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("actor_type", "player");
        fields.put("actor", "TestPlayer");
        fields.put("action", "break");
        fields.put("block_before", "Block{minecraft:oak_log}[axis=y]");
        fields.put("block_after", "Block{minecraft:air}");
        fields.put("x", -181);
        fields.put("y", 68);
        fields.put("z", -518);

        LogRecord record = LogRecord.builder(LogType.BLOCK_CHANGE, 1, 0, "1.0", "import")
                .put("actor_type", fields.get("actor_type"))
                .put("actor", fields.get("actor"))
                .put("action", fields.get("action"))
                .put("block_before", fields.get("block_before"))
                .put("block_after", fields.get("block_after"))
                .at(-181, 68, -518)
                .build();

        assertTrue(
                record.toJson().contains("minecraft:oak_log"),
                "Состояние блока до изменения обязано попасть в JSONL: иначе по логу не понять, что сломали");
    }

    private static LogType typeOf(String json) {
        for (LogType type : LogType.values()) {
            if (type.json().equals(json)) {
                return type;
            }
        }
        throw new IllegalArgumentException("В JSONL неизвестный тип: " + json);
    }
}
