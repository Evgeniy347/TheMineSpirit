package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageOverflowLiveTests {
    private static final Path OBSERVE = Path.of("src/test/resources/live/storefull_observe.json");
    private static final Path BOTS = Path.of("src/test/resources/live/storefull_bots.json");
    private static final BlockCoord FULL = new BlockCoord(-181, 69, -517);
    private static final BlockCoord FREE = new BlockCoord(-171, 71, -487);

    @Test
    void liveWarehouse_FullChestSkipped_LogNamesContainerAndFreeSlots() throws IOException {
        List<StoragePoints.Spot> spots = points();

        StoragePoints.Choice choice =
                StoragePoints.deposit(spots, origin(), new Stack("minecraft:wooden_pickaxe", 0, 2, 64));

        assertTrue(choice.found(), "в живом складе есть свободный сундук — сдача обязана найти его: " + choice.log());
        assertEquals(FREE, choice.point(), "закреплённый полный сундук " + FULL + " не выбирается заново");
        assertTrue(
                choice.log().contains("-181,69,-517") && choice.log().contains("свободно 0 из 27"),
                "решение [deposit] называет контейнер и число свободных слотов, а не «свободных слотов нет»: "
                        + choice.log());
        assertTrue(
                choice.log().contains("-171,71,-487") && choice.log().contains("выбрана"),
                "выбранный контейнер виден в логе с координатами: " + choice.log());
    }

    @Test
    void liveWarehouse_AllFull_RefusalNamesEveryContainerAndFreeSlots() throws IOException {
        List<ContainerSnapshot> full = List.of(
                snapshot(FULL, 27, "minecraft:dirt"),
                snapshot(new BlockCoord(-152, 72, -491), 27, "minecraft:cobblestone"));

        ContainerChoice choice = ContainerPicker.choose(full, origin(), new Stack("minecraft:oak_stairs", 0, 9, 64));

        assertTrue(choice instanceof ContainerChoice.Refused, "во всех полных контейнерах места нет: " + choice);
        String reason = ((ContainerChoice.Refused) choice).reason();
        assertTrue(
                reason.contains("-181,69,-517") && reason.contains("свободно 0 из 27"),
                "отказ называет каждый контейнер и свободные слоты — по нему решают, ставить ли второй сундук: "
                        + reason);
        assertTrue(
                reason.contains("-152,72,-491") && reason.contains("поставьте сундук"),
                "отказ говорит, что нужен новый сундук, и не повторяет одно и то же без адреса: " + reason);
    }

    private static List<StoragePoints.Spot> points() throws IOException {
        JsonNode observe = new ObjectMapper().readTree(read(OBSERVE));
        List<StoragePoints.Spot> spots = new ArrayList<>();
        for (JsonNode container : observe.get("container_items")) {
            BlockCoord pos = coord(container.get("pos"));
            if (pos.equals(FULL)) {
                spots.add(new StoragePoints.Spot(pos, "minecraft:chest", snapshot(FULL, 27, "minecraft:dirt")));
            } else if (pos.equals(FREE)) {
                spots.add(new StoragePoints.Spot(pos, "minecraft:chest", new ContainerSnapshot(FREE, empty(27))));
            }
        }
        assertFalse(spots.isEmpty(), "наблюдение с сервера без контейнеров: фикстура не о том складе");
        return spots;
    }

    private static BlockCoord origin() throws IOException {
        JsonNode bots = new ObjectMapper().readTree(read(BOTS));
        JsonNode position = bots.get("bots").get(0).get("position");
        return new BlockCoord(
                position.get(0).asInt(),
                position.get(1).asInt(),
                position.get(2).asInt());
    }

    private static ContainerSnapshot snapshot(BlockCoord pos, int size, String item) {
        List<Stack> slots = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            slots.add(new Stack(item, 0, 64, 64));
        }
        return new ContainerSnapshot(pos, slots);
    }

    private static List<Stack> empty(int size) {
        List<Stack> slots = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            slots.add(null);
        }
        return slots;
    }

    private static BlockCoord coord(JsonNode value) {
        return new BlockCoord(
                value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
