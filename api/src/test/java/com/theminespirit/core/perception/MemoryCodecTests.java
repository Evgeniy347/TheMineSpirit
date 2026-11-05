package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MemoryCodecTests {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String GOLDEN_V1 = "{\"version\":1,\"points\":["
            + "{\"name\":\"iron\",\"type\":\"resource\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":-2},"
            + "{\"name\":\"fortress\",\"type\":\"danger\",\"dimension\":\"minecraft:the_nether\",\"x\":0,\"y\":70,\"z\":9}]}";

    private static String oneSegment(String name, String type, String coords) {
        return "{\"version\":1,\"points\":[{\"name\":\"" + name + "\",\"type\":\"" + type
                + "\",\"dimension\":\"minecraft:overworld\"" + coords + "}]}";
    }

    @Test
    void encode_AnyStore_WritesVersionOne() throws IOException {
        JsonNode root = new ObjectMapper().readTree(MemoryCodec.encode(MemoryStore.empty()));

        assertEquals(1, MemoryCodec.VERSION, "версия формата памяти согласована: 1");
        assertEquals(
                1,
                root.get("version").asInt(),
                "в записи обязана лежать версия 1: по ней будущий читатель выберет разбор формата");
    }

    @Test
    void decode_GoldenV1_ReadsKnownStore() {
        MemoryStore expected = new MemoryStore(List.of(
                new MemoryPoint("iron", MemoryType.RESOURCE, OVERWORLD, new BlockCoord(1, 64, -2)),
                new MemoryPoint("fortress", MemoryType.DANGER, NETHER, new BlockCoord(0, 70, 9))));

        assertEquals(
                expected,
                MemoryCodec.decode(GOLDEN_V1),
                "запись формата v1 обязана читаться и впредь: сохранённая память духов не пропадает при обновлении "
                        + "мода; сменишь формат — подними version и добавь читателя старой");
        assertEquals(
                GOLDEN_V1,
                MemoryCodec.encode(expected),
                "кодирование обязано давать ту же запись v1: порядок точек и поля части контракта хранения");
    }

    @Test
    void roundTrip_StoreWithAllTypesAndDimensions_IsEqual() {
        MemoryStore store = new MemoryStore(List.of(
                new MemoryPoint("Дом", MemoryType.BASE, OVERWORLD, new BlockCoord(-5, 62, 300)),
                new MemoryPoint(
                        "quote\"slash\\",
                        MemoryType.RESOURCE,
                        NETHER,
                        new BlockCoord(Integer.MIN_VALUE, -64, Integer.MAX_VALUE)),
                new MemoryPoint("lava", MemoryType.DANGER, OVERWORLD, new BlockCoord(0, 11, 0))));

        assertEquals(
                store,
                MemoryCodec.decode(MemoryCodec.encode(store)),
                "память обязана переживать encode→decode без потерь: типы, измерения, юникод, экранирование, "
                        + "крайние координаты");
    }

    @Test
    void roundTrip_FullMemory_IsEqual() {
        MemoryStore store = MemoryStore.empty();
        for (MemoryType type : MemoryType.values()) {
            for (int i = 0; i < 16; i++) {
                store = store.remember(new MemoryPoint(type.id() + i, type, OVERWORLD, new BlockCoord(i, 64, -i)));
            }
        }

        assertEquals(48, store.all().size(), "подготовка: заполненная память — 48 точек");
        assertEquals(
                store,
                MemoryCodec.decode(MemoryCodec.encode(store)),
                "заполненная память (48 точек) обязана переживать encode→decode");
    }

    static Stream<Arguments> corrupted() {
        List<String> seventeen = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            seventeen.add(
                    "{\"name\":\"r" + i + "\",\"type\":\"resource\",\"dimension\":\"d\",\"x\":0,\"y\":0,\"z\":0}");
        }
        return Stream.of(
                Arguments.of("пустая строка", "", "JSON"),
                Arguments.of("не JSON", "not json", "JSON"),
                Arguments.of("обрыв посередине", GOLDEN_V1.substring(0, GOLDEN_V1.length() / 2), "JSON"),
                Arguments.of("мусор после объекта", GOLDEN_V1 + " tail", "JSON"),
                Arguments.of("массив вместо объекта", "[]", "объект"),
                Arguments.of("null", "null", "объект"),
                Arguments.of("нет версии", "{\"points\":[]}", "version"),
                Arguments.of("версия строкой", "{\"version\":\"1\",\"points\":[]}", "version"),
                Arguments.of("версия новее", "{\"version\":2,\"points\":[]}", "новее"),
                Arguments.of("версия ноль", "{\"version\":0,\"points\":[]}", "меньше 1"),
                Arguments.of("нет points", "{\"version\":1}", "points"),
                Arguments.of("points не массив", "{\"version\":1,\"points\":{}}", "points"),
                Arguments.of("точка не объект", "{\"version\":1,\"points\":[5]}", "объект"),
                Arguments.of("нет имени", "{\"version\":1,\"points\":[{\"type\":\"base\"}]}", "name"),
                Arguments.of("тип заглавными", oneSegment("a", "RESOURCE", ",\"x\":0,\"y\":0,\"z\":0"), "тип"),
                Arguments.of("неизвестный тип", oneSegment("a", "ore", ",\"x\":0,\"y\":0,\"z\":0"), "ore"),
                Arguments.of("координата дробная", oneSegment("a", "base", ",\"x\":1.5,\"y\":0,\"z\":0"), "'x'"),
                Arguments.of("нет координаты", oneSegment("a", "base", ",\"x\":0,\"y\":0"), "'z'"),
                Arguments.of(
                        "координата вне int", oneSegment("a", "base", ",\"x\":0,\"y\":99999999999,\"z\":0"), "'y'"),
                Arguments.of("пустое имя", oneSegment(" ", "base", ",\"x\":0,\"y\":0,\"z\":0"), "пусто"),
                Arguments.of(
                        "повтор имени",
                        "{\"version\":1,\"points\":[{\"name\":\"a\",\"type\":\"base\",\"dimension\":\"d\",\"x\":0,\"y\":0,\"z\":0},"
                                + "{\"name\":\"a\",\"type\":\"danger\",\"dimension\":\"d\",\"x\":0,\"y\":0,\"z\":0}]}",
                        "дважды"),
                Arguments.of(
                        "17 точек одного типа",
                        "{\"version\":1,\"points\":[" + String.join(",", seventeen) + "]}",
                        "заполнена"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corrupted")
    void decode_CorruptedData_ThrowsFormatExceptionNamingProblem(String label, String json, String fragment) {
        MemoryFormatException error = assertThrows(
                MemoryFormatException.class,
                () -> MemoryCodec.decode(json),
                "повреждённая память («" + label + "») обязана давать ошибку, а не пустую память: тихий fallback "
                        + "стирал бы память духа без следа; ловит и логирует адаптер");

        assertTrue(
                error.getMessage().contains(fragment),
                "сообщение для «" + label + "» обязано называть проблему («" + fragment + "»). Фактическое: "
                        + error.getMessage());
    }

    @Test
    void decode_NullText_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> MemoryCodec.decode(null),
                "null вместо текста обязан отклоняться: адаптер сам решает, что делать с отсутствующим ключом");
    }
}
