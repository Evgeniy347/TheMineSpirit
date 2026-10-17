package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorldObservationTests {
    private static WorldObservation observation(List<ItemCount> inventory, List<EntityRef> entities) {
        return new WorldObservation(
                "spirit_1",
                123456L,
                new BlockCoord(10, 64, -3),
                inventory,
                List.of(new ItemCount("oak_log", 12), new ItemCount("grass_block", 340)),
                entities,
                List.of(new ContainerRef("minecraft:chest", new BlockCoord(8, 63, -5))),
                32);
    }

    private static WorldObservation richObservation() {
        return new WorldObservation(
                "spirit_1",
                1L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("minecraft:iron_pickaxe", 1)),
                List.of(new ItemCount("minecraft:stone", 9)),
                List.of(),
                List.of(new ContainerRef("minecraft:chest", new BlockCoord(8, 63, -5))),
                32,
                SpiritStatus.of(new HeldItem("minecraft:iron_pickaxe", 250), 18.0, 20.0, 0.7,
                        new BoardTaskRef("MINE", "ASSIGNED")),
                List.of(new GroundItem("minecraft:oak_log", 3)),
                List.of(new ContainerContents(new BlockCoord(8, 63, -5),
                        List.of(new ItemCount("minecraft:iron_ingot", 7)))),
                new WorldTime(6000L, true));
    }

    @Test
    void hash_SameDataDifferentListOrder_IsEqual() {
        WorldObservation first = observation(
                List.of(new ItemCount("oak_log", 5), new ItemCount("iron_pickaxe", 1)),
                List.of(new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1), 5.0),
                        new EntityRef("minecraft:sheep", new BlockCoord(2, 64, 1), 9.0)));
        WorldObservation second = observation(
                List.of(new ItemCount("iron_pickaxe", 1), new ItemCount("oak_log", 5)),
                List.of(new EntityRef("minecraft:sheep", new BlockCoord(2, 64, 1), 9.0),
                        new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1), 5.0)));

        assertEquals(first.hash(), second.hash(),
                "hash наблюдения обязан быть стабильным при любом порядке входных списков: иначе две одинаковые "
                        + "картины мира дают разные хэши, и кэш решений ИИ промахивается при каждом пересборе списка");
        assertEquals(first, second,
                "Наблюдения с одними данными и разным порядком списков должны быть равны: канонизация в конструкторе "
                        + "обязана сортировать списки");
    }

    @Test
    void hash_DataChanged_Differs() {
        WorldObservation base = observation(List.of(new ItemCount("oak_log", 5)), List.of());
        WorldObservation changed = observation(List.of(new ItemCount("oak_log", 6)), List.of());

        assertNotEquals(base.hash(), changed.hash(),
                "hash обязан меняться при изменении данных наблюдения: иначе ИИ считает изменившийся мир прежним "
                        + "и не перепланирует");
    }

    @Test
    void hash_HexForm_IsSha256Length() {
        String hash = observation(List.of(), List.of()).hash();

        assertEquals(64, hash.length(),
                "hash — SHA-256 в hex, ровно 64 символа: другая длина означает, что используется не SHA-256 "
                        + "и хэши несравнимы между версиями");
    }

    @Test
    void hash_InventoryWithDuplicatesAggregated_IsEqualRegardlessOfSlots() {
        WorldObservation first = observation(
                List.of(new ItemCount("oak_log", 64), new ItemCount("oak_log", 5), new ItemCount("iron_pickaxe", 1)),
                List.of());
        WorldObservation second = observation(
                List.of(new ItemCount("iron_pickaxe", 1), new ItemCount("oak_log", 69)),
                List.of());

        assertEquals(first.hash(), second.hash(),
                "Одинаковый мультимножественный инвентарь с повторами должен давать одинаковый hash после агрегации");
    }

    @Test
    void hash_SameWorldDifferentTicks_SameHash() {
        WorldObservation first = new WorldObservation("spirit_1", 1L, new BlockCoord(10, 64, -3),
                List.of(new ItemCount("oak_log", 5)), List.of(), List.of(), List.of(), 32);
        WorldObservation second = new WorldObservation("spirit_1", 999999L, new BlockCoord(10, 64, -3),
                List.of(new ItemCount("oak_log", 5)), List.of(), List.of(), List.of(), 32);
        assertEquals(first.hash(), second.hash(),
                "hash должен описывать картину мира без тика: одно и то же состояние в разные тики даёт одинаковый hash");
    }

    @Test
    void hash_EntityMovesBetweenBlocks_HashDiffers() {
        WorldObservation first = observation(List.of(), List.of(new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1))));
        WorldObservation second = observation(List.of(), List.of(new EntityRef("minecraft:cow", new BlockCoord(15, 64, -1))));
        assertNotEquals(first.hash(), second.hash(),
                "Сдвиг сущности на блок изменяет картину мира — hash должен отличаться");
    }

    @Test
    void hash_HealthChanged_HashDiffers() {
        WorldObservation base = richObservation();
        WorldObservation hurt = withSpirit(base, SpiritStatus.of(base.spirit().held(), 12.0, 20.0, 0.7,
                base.spirit().boardTask()));
        assertNotEquals(base.hash(), hurt.hash(),
                "Падение здоровья меняет наблюдение — стратег обязан перепланировать, а не считать мир прежним");
    }

    @Test
    void hash_HeldItemChanged_HashDiffers() {
        WorldObservation base = richObservation();
        WorldObservation switched = withSpirit(base, SpiritStatus.of(new HeldItem("minecraft:diamond_pickaxe", 150),
                18.0, 20.0, 0.7, base.spirit().boardTask()));
        assertNotEquals(base.hash(), switched.hash(),
                "Смена предмета в руке — ресурс для стратега: hash обязан измениться, иначе решение устареет");
    }

    @Test
    void hash_GroundItemsChanged_HashDiffers() {
        WorldObservation base = richObservation();
        WorldObservation picked = new WorldObservation(base.botId(), base.tick(), base.position(), base.inventory(),
                base.blocks(), base.entities(), base.containers(), base.radius(), base.spirit(), List.of(),
                base.containerItems(), base.time());
        assertNotEquals(base.hash(), picked.hash(),
                "Подобранные с земли предметы меняют ресурсы — hash обязан отличаться");
    }

    @Test
    void hash_DayTimeChangedWithinSameDay_SameHash() {
        WorldObservation base = richObservation();
        WorldObservation laterSameDay = new WorldObservation(base.botId(), base.tick() + 100, base.position(),
                base.inventory(), base.blocks(), base.entities(), base.containers(), base.radius(), base.spirit(),
                base.groundItems(), base.containerItems(), new WorldTime(7000L, true));
        assertEquals(base.hash(), laterSameDay.hash(),
                "Точный тик суток в наблюдении есть, но в hash входит только день/ночь: иначе тик растёт каждый "
                        + "цикл и стратег зовёт модель впустую");
    }

    @Test
    void hash_DayTurnsToNight_HashDiffers() {
        WorldObservation day = richObservation();
        WorldObservation night = new WorldObservation(day.botId(), day.tick(), day.position(), day.inventory(),
                day.blocks(), day.entities(), day.containers(), day.radius(), day.spirit(), day.groundItems(),
                day.containerItems(), new WorldTime(14000L, false));
        assertNotEquals(day.hash(), night.hash(),
                "Смена дня и ночи — смена правил поведения: hash обязан измениться");
    }

    @Test
    void hash_RichDataDifferentListOrder_IsEqual() {
        WorldObservation first = new WorldObservation("spirit_1", 1L, new BlockCoord(10, 64, -3),
                List.of(new ItemCount("minecraft:iron_pickaxe", 1)), List.of(), List.of(), List.of(), 32,
                null,
                List.of(new GroundItem("minecraft:oak_log", 3), new GroundItem("minecraft:oak_log", 2)),
                List.of(new ContainerContents(new BlockCoord(9, 63, -5), List.of(new ItemCount("minecraft:coal", 1))),
                        new ContainerContents(new BlockCoord(8, 63, -5), List.of(new ItemCount("minecraft:iron_ingot", 1)))),
                null);
        WorldObservation second = new WorldObservation("spirit_1", 1L, new BlockCoord(10, 64, -3),
                List.of(new ItemCount("minecraft:iron_pickaxe", 1)), List.of(), List.of(), List.of(), 32,
                null,
                List.of(new GroundItem("minecraft:oak_log", 5)),
                List.of(new ContainerContents(new BlockCoord(8, 63, -5), List.of(new ItemCount("minecraft:iron_ingot", 1))),
                        new ContainerContents(new BlockCoord(9, 63, -5), List.of(new ItemCount("minecraft:coal", 1)))),
                null);
        assertEquals(first.hash(), second.hash(),
                "Выпавшие предметы одного вида сводятся в одну запись, а контейнеры сортируются по позиции: "
                        + "иначе тот же мир даёт разные хэши");
    }

    @Test
    void toJson_RichObservation_NewFieldsMatchContract() {
        String json = richObservation().toJson();
        assertTrue(json.contains("\"held\":{\"item\":\"minecraft:iron_pickaxe\",\"durability\":250}"),
                "Предмет в руке идёт полем held с id и прочностью: " + json);
        assertTrue(json.contains("\"health\":18.0,\"max_health\":20.0,\"food\":0.7"),
                "Здоровье, максимум здоровья и сытость идут полями наблюдения: " + json);
        assertTrue(json.contains("\"board_task\":{\"case\":\"MINE\",\"status\":\"ASSIGNED\"}"),
                "Текущее задание доски идёт полем board_task с кейсом и статусом: " + json);
        assertTrue(json.contains("\"ground_items\":[{\"item\":\"minecraft:oak_log\",\"count\":3}]"),
                "Выпавшие предметы идут полем ground_items с id и количеством: " + json);
        assertTrue(json.contains("\"time\":{\"day\":true,\"day_time\":6000}"),
                "Время суток идёт полем time с днём/ночью и тиком дня: " + json);
    }

    private static WorldObservation withSpirit(WorldObservation base, SpiritStatus spirit) {
        return new WorldObservation(base.botId(), base.tick(), base.position(), base.inventory(), base.blocks(),
                base.entities(), base.containers(), base.radius(), spirit, base.groundItems(), base.containerItems(),
                base.time());
    }

    @Test
    void toJson_ContractShape_MatchesExpectedJson() {
        WorldObservation obs = new WorldObservation(
                "spirit_1",
                123456L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("oak_log", 5), new ItemCount("iron_pickaxe", 1)),
                List.of(new ItemCount("grass_block", 340), new ItemCount("oak_log", 12)),
                List.of(new EntityRef("minecraft:cow", new BlockCoord(14, 64, -1), 5.0),
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
        assertEquals(expected, json,
                "JSON наблюдения обязан строго совпадать с контрактом API: имена полей, порядок, структура, включая hash");
    }

    static Stream<Arguments> brokenPosCases() {
        return Stream.of(
                Arguments.of("pos из двух чисел", "[1,2]"),
                Arguments.of("pos из четырёх чисел", "[1,2,3,4]"),
                Arguments.of("pos не массив", "\"not-array\""),
                Arguments.of("pos с нечисловым элементом", "[1,2,\"x\"]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenPosCases")
    void fromJson_BrokenPos_ThrowsIllegalArgument(String description, String brokenPos) {
        assertThrows(IllegalArgumentException.class, () -> WorldObservation.fromJson(jsonWithPos(brokenPos)),
                description + ": битый JSON с pos обязан приводить к IllegalArgumentException с понятной причиной, "
                        + "иначе bridge падает нечитаемой ошибкой на повреждённом запросе");
    }

    @Test
    void fromJson_CorrectPos_ControlCaseReturnsValidPos() {
        WorldObservation observation = WorldObservation.fromJson(jsonWithPos("[1,2,3]"));

        assertEquals(new BlockCoord(1, 2, 3), observation.position(),
                "Контрольный случай pos из трёх чисел обязан разбираться в координаты 1,2,3: без него опечатка "
                        + "в шаблоне jsonWithPos сделает зелёными все строки битого pos по чужой причине");
    }

    static Stream<Arguments> serializationCases() {
        WorldObservation withContainers = new WorldObservation("spirit_1", 7L, new BlockCoord(1, 2, 3),
                List.of(new ItemCount("oak_log", 5)),
                List.of(new ItemCount("stone", 9)),
                List.of(new EntityRef("minecraft:cow", new BlockCoord(1, 1, 1), 2.0),
                        new EntityRef("minecraft:sheep", new BlockCoord(3, 3, 3), 4.0)),
                List.of(new ContainerRef("minecraft:chest", new BlockCoord(5, 5, 5)),
                        new ContainerRef("minecraft:barrel", new BlockCoord(6, 6, 6))),
                16);
        WorldObservation extreme = new WorldObservation("spirit_extreme", Long.MIN_VALUE,
                new BlockCoord(Integer.MIN_VALUE, -64, Integer.MAX_VALUE),
                List.of(new ItemCount("item", Integer.MAX_VALUE)),
                List.of(new ItemCount("item", Integer.MIN_VALUE)),
                List.of(),
                List.of(),
                Integer.MAX_VALUE);
        WorldObservation empty = new WorldObservation("spirit_1", 0L, new BlockCoord(0, 64, 0),
                List.of(), List.of(), List.of(), List.of(), 32);
        return Stream.of(
                Arguments.of("пустое наблюдение", empty),
                Arguments.of("повторы предметов в инвентаре", observation(
                        List.of(new ItemCount("oak_log", 64), new ItemCount("oak_log", 5),
                                new ItemCount("iron_pickaxe", 1)),
                        List.of())),
                Arguments.of("сущности и контейнеры", withContainers),
                Arguments.of("крайние координаты и значения", extreme),
                Arguments.of("новые поля: рука, здоровье, доска, дроп, сундуки, время", richObservation()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serializationCases")
    void json_AnyObservation_RoundTripsWithoutLoss(String description, WorldObservation original) {
        assertEquals(original, WorldObservation.fromJson(original.toJson()),
                description + ": fromJson(toJson(x)) обязан возвращать равное наблюдение, иначе bridge теряет "
                        + "данные при передаче по REST и решение принимается по испорченной картине мира");
    }

    private static String jsonWithPos(String posField) {
        return "{\"bot\":\"spirit_1\",\"tick\":1,\"pos\":" + posField
                + ",\"radius\":32,\"inventory\":[],\"blocks\":[],\"entities\":[],\"containers\":[],\"hash\":\"any\"}";
    }
}
