package com.theminespirit.core.observation;

import static com.theminespirit.core.observation.WorldObservationTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WorldObservationPart2Tests {
    @Test
    void hash_DayTurnsToNight_HashDiffers() {
        WorldObservation day = richObservation();
        WorldObservation night = new WorldObservation(
                day.botId(),
                day.tick(),
                day.position(),
                day.inventory(),
                day.blocks(),
                day.entities(),
                day.containers(),
                day.radius(),
                day.spirit(),
                day.groundItems(),
                day.containerItems(),
                new WorldTime(14000L, false));
        assertNotEquals(
                day.hash(),
                night.hash(),
                "Смена дня и ночи — смена правил поведения: hash обязан измениться Иначе тест hash_DayTurnsToNight_HashDiffers перестанет ловить эту поломку. При смене hash_DayTurnsToNight_HashDiffers проверь это утверждение.");
    }

    @Test
    void hash_RichDataDifferentListOrder_IsEqual() {
        WorldObservation first = new WorldObservation(
                "spirit_1",
                1L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("minecraft:iron_pickaxe", 1)),
                List.of(),
                List.of(),
                List.of(),
                32,
                null,
                List.of(new GroundItem("minecraft:oak_log", 3), new GroundItem("minecraft:oak_log", 2)),
                List.of(
                        new ContainerContents(new BlockCoord(9, 63, -5), List.of(new ItemCount("minecraft:coal", 1))),
                        new ContainerContents(
                                new BlockCoord(8, 63, -5), List.of(new ItemCount("minecraft:iron_ingot", 1)))),
                null);
        WorldObservation second = new WorldObservation(
                "spirit_1",
                1L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("minecraft:iron_pickaxe", 1)),
                List.of(),
                List.of(),
                List.of(),
                32,
                null,
                List.of(new GroundItem("minecraft:oak_log", 5)),
                List.of(
                        new ContainerContents(
                                new BlockCoord(8, 63, -5), List.of(new ItemCount("minecraft:iron_ingot", 1))),
                        new ContainerContents(new BlockCoord(9, 63, -5), List.of(new ItemCount("minecraft:coal", 1)))),
                null);
        assertEquals(
                first.hash(),
                second.hash(),
                "Выпавшие предметы одного вида сводятся в одну запись, а контейнеры сортируются по позиции: "
                        + "иначе тот же мир даёт разные хэши При смене hash_RichDataDifferentListOrder_IsEqual проверь это утверждение.");
    }

    @Test
    void json_OpenBlocks_RoundTripsSortedByDistance() {
        String json = "{\"bot\":\"spirit_1\",\"tick\":1,\"pos\":[0,64,0],\"radius\":8,"
                + "\"inventory\":[],\"blocks\":[],\"entities\":[],\"containers\":[],"
                + "\"open_blocks\":[{\"item\":\"minecraft:stone\",\"pos\":[3,64,0],\"distance\":3.0},"
                + "{\"item\":\"minecraft:iron_ore\",\"pos\":[1,64,0],\"distance\":1.0}],\"hash\":\"any\"}";

        String rendered = WorldObservation.fromJson(json).toJson();

        assertTrue(
                rendered.contains("\"open_blocks\":[{\"item\":\"minecraft:iron_ore\",\"pos\":[1,64,0],"
                        + "\"distance\":1.0},{\"item\":\"minecraft:stone\",\"pos\":[3,64,0],\"distance\":3.0}]"),
                "Поле open_blocks обязано доживать от REST-запроса до промпта и идти по возрастанию расстояния: "
                        + "без позиций стратег не может поставить goto к ближайшей открытой руде,  Иначе тест json_OpenBlocks_RoundTripsSortedByDistance перестанет ловить эту поломку. При смене json_OpenBlocks_RoundTripsSortedByDistance проверь это утверждение."
                        + rendered);
    }

    @Test
    void hash_OpenBlockMoved_HashDiffers() {
        WorldObservation near = WorldObservation.fromJson(openBlocksJson("[1,64,0]"));
        WorldObservation far = WorldObservation.fromJson(openBlocksJson("[7,64,0]"));

        assertNotEquals(
                near.hash(),
                far.hash(),
                "Перенос открытой руды на другую позицию меняет картину мира: иначе стратег считает изменённый мир "
                        + "прежним и не перепланирует путь к новой жиле При смене hash_OpenBlockMoved_HashDiffers проверь это утверждение.");
    }

    @Test
    void toJson_RichObservation_NewFieldsMatchContract() {
        String json = richObservation().toJson();
        assertTrue(
                json.contains("\"held\":{\"item\":\"minecraft:iron_pickaxe\",\"durability\":250}"),
                "Предмет в руке идёт полем held с id и прочностью:  Иначе тест toJson_RichObservation_NewFieldsMatchContract перестанет ловить эту поломку. При смене toJson_RichObservation_NewFieldsMatchContract проверь это утверждение."
                        + json);
        assertTrue(
                json.contains("\"health\":18.0,\"max_health\":20.0,\"food\":0.7"),
                "Здоровье, максимум здоровья и сытость идут полями наблюдения:  Иначе тест toJson_RichObservation_NewFieldsMatchContract перестанет ловить эту поломку. При смене toJson_RichObservation_NewFieldsMatchContract проверь это утверждение."
                        + json);
        assertTrue(
                json.contains("\"board_task\":{\"case\":\"MINE\",\"status\":\"ASSIGNED\"}"),
                "Текущее задание доски идёт полем board_task с кейсом и статусом:  Иначе тест toJson_RichObservation_NewFieldsMatchContract перестанет ловить эту поломку. При смене toJson_RichObservation_NewFieldsMatchContract проверь это утверждение."
                        + json);
        assertTrue(
                json.contains("\"ground_items\":[{\"item\":\"minecraft:oak_log\",\"count\":3}]"),
                "Выпавшие предметы идут полем ground_items с id и количеством:  Иначе тест toJson_RichObservation_NewFieldsMatchContract перестанет ловить эту поломку. При смене toJson_RichObservation_NewFieldsMatchContract проверь это утверждение."
                        + json);
        assertTrue(
                json.contains("\"time\":{\"day\":true,\"day_time\":6000}"),
                "Время суток идёт полем time с днём/ночью и тиком дня:  Иначе тест toJson_RichObservation_NewFieldsMatchContract перестанет ловить эту поломку. При смене toJson_RichObservation_NewFieldsMatchContract проверь это утверждение."
                        + json);
    }

    @Test
    void toJson_ContractShape_MatchesExpectedJson() {
        WorldObservation obs = new WorldObservation(
                "spirit_1",
                123456L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("oak_log", 5), new ItemCount("iron_pickaxe", 1)),
                List.of(new ItemCount("grass_block", 340), new ItemCount("oak_log", 12)),
                List.of(
                        new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1), 5.0),
                        new EntityRef("minecraft:sheep", new BlockCoord(2, 64, 1), 9.0)),
                List.of(new ContainerRef("minecraft:chest", new BlockCoord(8, 63, -5))),
                32);
        String json = obs.toJson();
        String hash = obs.hash();
        String expected = "{\"bot\":\"spirit_1\",\"tick\":123456,\"pos\":[10,64,-3],\"radius\":32,"
                + "\"inventory\":[{\"item\":\"iron_pickaxe\",\"count\":1},{\"item\":\"oak_log\",\"count\":5}],"
                + "\"blocks\":[{\"item\":\"grass_block\",\"count\":340},{\"item\":\"oak_log\",\"count\":12}],"
                + "\"entities\":[{\"type\":\"minecraft:cow\",\"pos\":[14,64,-1],\"distance\":5.0},"
                + "{\"type\":\"minecraft:sheep\",\"pos\":[2,64,1],\"distance\":9.0}],"
                + "\"containers\":[{\"type\":\"minecraft:chest\",\"pos\":[8,63,-5]}],"
                + "\"hash\":\"" + hash + "\"}";
        assertEquals(
                expected,
                json,
                "JSON наблюдения обязан строго совпадать с контрактом API: имена полей, порядок, структура, включая hash Иначе тест toJson_ContractShape_MatchesExpectedJson перестанет ловить эту поломку. При смене toJson_ContractShape_MatchesExpectedJson проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.observation.WorldObservationTestsSupport#brokenPosCases")
    void fromJson_BrokenPos_ThrowsIllegalArgument(String description, String brokenPos) {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorldObservation.fromJson(jsonWithPos(brokenPos)),
                description + ": битый JSON с pos обязан приводить к IllegalArgumentException с понятной причиной, "
                        + "иначе bridge падает нечитаемой ошибкой на повреждённом запросе При смене fromJson_BrokenPos_ThrowsIllegalArgument проверь это утверждение.");
    }

    @Test
    void fromJson_CorrectPos_ControlCaseReturnsValidPos() {
        WorldObservation observation = WorldObservation.fromJson(jsonWithPos("[1,2,3]"));

        assertEquals(
                new BlockCoord(1, 2, 3),
                observation.position(),
                "Контрольный случай pos из трёх чисел обязан разбираться в координаты 1,2,3: без него опечатка "
                        + "в шаблоне jsonWithPos сделает зелёными все строки битого pos по чужой причине Иначе тест fromJson_CorrectPos_ControlCaseReturnsValidPos перестанет ловить эту поломку. При смене fromJson_CorrectPos_ControlCaseReturnsValidPos проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.observation.WorldObservationTestsSupport#serializationCases")
    void json_AnyObservation_RoundTripsWithoutLoss(String description, WorldObservation original) {
        assertEquals(
                original,
                WorldObservation.fromJson(original.toJson()),
                description + ": fromJson(toJson(x)) обязан возвращать равное наблюдение, иначе bridge теряет "
                        + "данные при передаче по REST и решение принимается по испорченной картине мира При смене json_AnyObservation_RoundTripsWithoutLoss проверь это утверждение.");
    }
}
