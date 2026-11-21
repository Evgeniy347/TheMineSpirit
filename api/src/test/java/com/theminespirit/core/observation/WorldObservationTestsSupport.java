package com.theminespirit.core.observation;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

class WorldObservationTestsSupport {
    static WorldObservation observation(List<ItemCount> inventory, List<EntityRef> entities) {
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

    static WorldObservation richObservation() {
        return new WorldObservation(
                "spirit_1",
                1L,
                new BlockCoord(10, 64, -3),
                List.of(new ItemCount("minecraft:iron_pickaxe", 1)),
                List.of(new ItemCount("minecraft:stone", 9)),
                List.of(),
                List.of(new ContainerRef("minecraft:chest", new BlockCoord(8, 63, -5))),
                32,
                SpiritStatus.of(
                        new HeldItem("minecraft:iron_pickaxe", 250),
                        18.0,
                        20.0,
                        0.7,
                        new BoardTaskRef("MINE", "ASSIGNED")),
                List.of(new GroundItem("minecraft:oak_log", 3)),
                List.of(new ContainerContents(
                        new BlockCoord(8, 63, -5), List.of(new ItemCount("minecraft:iron_ingot", 7)))),
                new WorldTime(6000L, true));
    }

    static String openBlocksJson(String pos) {
        return "{\"bot\":\"spirit_1\",\"tick\":1,\"pos\":[0,64,0],\"radius\":8,"
                + "\"inventory\":[],\"blocks\":[],\"entities\":[],\"containers\":[],"
                + "\"open_blocks\":[{\"item\":\"minecraft:iron_ore\",\"pos\":" + pos
                + ",\"distance\":1.0}],\"hash\":\"any\"}";
    }

    static WorldObservation withSpirit(WorldObservation base, SpiritStatus spirit) {
        return new WorldObservation(
                base.botId(),
                base.tick(),
                base.position(),
                base.inventory(),
                base.blocks(),
                base.entities(),
                base.containers(),
                base.radius(),
                spirit,
                base.groundItems(),
                base.containerItems(),
                base.time());
    }

    static Stream<Arguments> brokenPosCases() {
        return Stream.of(
                Arguments.of("pos из двух чисел", "[1,2]"),
                Arguments.of("pos из четырёх чисел", "[1,2,3,4]"),
                Arguments.of("pos не массив", "\"not-array\""),
                Arguments.of("pos с нечисловым элементом", "[1,2,\"x\"]"));
    }

    static Stream<Arguments> serializationCases() {
        WorldObservation withContainers = new WorldObservation(
                "spirit_1",
                7L,
                new BlockCoord(1, 2, 3),
                List.of(new ItemCount("oak_log", 5)),
                List.of(new ItemCount("stone", 9)),
                List.of(
                        new EntityRef("minecraft:cow", new BlockCoord(1, 1, 1), 2.0),
                        new EntityRef("minecraft:sheep", new BlockCoord(3, 3, 3), 4.0)),
                List.of(
                        new ContainerRef("minecraft:chest", new BlockCoord(5, 5, 5)),
                        new ContainerRef("minecraft:barrel", new BlockCoord(6, 6, 6))),
                16);
        WorldObservation extreme = new WorldObservation(
                "spirit_extreme",
                Long.MIN_VALUE,
                new BlockCoord(Integer.MIN_VALUE, -64, Integer.MAX_VALUE),
                List.of(new ItemCount("item", Integer.MAX_VALUE)),
                List.of(new ItemCount("item", Integer.MIN_VALUE)),
                List.of(),
                List.of(),
                Integer.MAX_VALUE);
        WorldObservation empty = new WorldObservation(
                "spirit_1", 0L, new BlockCoord(0, 64, 0), List.of(), List.of(), List.of(), List.of(), 32);
        return Stream.of(
                Arguments.of("пустое наблюдение", empty),
                Arguments.of(
                        "повторы предметов в инвентаре",
                        observation(
                                List.of(
                                        new ItemCount("oak_log", 64),
                                        new ItemCount("oak_log", 5),
                                        new ItemCount("iron_pickaxe", 1)),
                                List.of())),
                Arguments.of("сущности и контейнеры", withContainers),
                Arguments.of("крайние координаты и значения", extreme),
                Arguments.of("новые поля: рука, здоровье, доска, дроп, сундуки, время", richObservation()));
    }

    static String jsonWithPos(String posField) {
        return "{\"bot\":\"spirit_1\",\"tick\":1,\"pos\":" + posField
                + ",\"radius\":32,\"inventory\":[],\"blocks\":[],\"entities\":[],\"containers\":[],\"hash\":\"any\"}";
    }
}
