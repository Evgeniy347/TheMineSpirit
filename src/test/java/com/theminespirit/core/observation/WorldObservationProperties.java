package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Tag;

class WorldObservationProperties {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);
    private static final int MAX_COUNT = 64;

    @Tag("slow")
    @Property
    void hash_AllListsOrderChanged_StaysEqual(@ForAll @Size(max = 16) List<Integer> raw) {
        WorldObservation original = randomObservation(raw, 32, 1L);
        WorldObservation reversed = randomObservation(reversed(raw), 32, 1L);

        assertEquals(original.hash(), reversed.hash(),
                "hash наблюдения обязан не зависеть от порядка ВСЕХ списков (inventory, blocks, entities, "
                        + "containers): иначе один и тот же мир даёт разные хэши в зависимости от порядка сбора "
                        + "сущностей и контейнеров, и кэш решений ИИ промахивается");
    }

    @Tag("slow")
    @Property
    void json_RoundTrip_EqualsOriginal(@ForAll @Size(max = 8) List<Integer> raw,
            @ForAll int radius, @ForAll long tick) {
        WorldObservation original = randomObservation(raw, radius, tick);

        assertEquals(
                original,
                WorldObservation.fromJson(original.toJson()),
                "наблюдение обязано восстанавливаться из своего JSON без потерь на случайных данных, включая "
                        + "повторы предметов и пустые списки: иначе REST-клиент и ИИ увидят не тот мир, что сервер, "
                        + "и план будет строиться по искажённым данным");
    }

    @Tag("slow")
    @Property
    void hash_SameDataAnyPermutation_StaysEqual(@ForAll @Size(max = 12) List<Integer> raw,
            @ForAll @IntRange(min = 0, max = 10) int rotation) {
        WorldObservation original = randomObservation(raw, 32, 1L);
        List<Integer> rotated = new ArrayList<>(raw);
        Collections.rotate(rotated, rotation);

        assertEquals(original.hash(), randomObservation(rotated, 32, 1L).hash(),
                "hash обязан не зависеть от порядка списков при любой циклической перестановке: канонизация "
                        + "в конструкторе обязана сортировать, а не полагаться на порядок прихода данных");
    }

    private static List<Integer> reversed(List<Integer> raw) {
        List<Integer> copy = new ArrayList<>(raw);
        Collections.reverse(copy);
        return copy;
    }

    private static WorldObservation randomObservation(List<Integer> raw, int radius, long tick) {
        return new WorldObservation("spirit", tick, ORIGIN, entries(raw, "item_"), entries(raw, "block_"),
                entities(raw), containers(raw), Math.floorMod(radius, 4096));
    }

    private static List<ItemCount> entries(List<Integer> raw, String prefix) {
        List<ItemCount> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int count = Math.floorMod(raw.get(i), MAX_COUNT) + 1;
            String name = prefix + Math.floorMod(raw.get(i) * 131542391, 4);
            result.add(new ItemCount(name, count));
        }
        return result;
    }

    private static List<EntityRef> entities(List<Integer> raw) {
        List<EntityRef> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int v = raw.get(i);
            String id = Math.floorMod(v, 4) == 0 ? null : "uuid_" + v;
            result.add(new EntityRef("entity_" + Math.floorMod(v, 3), coord(v), id,
                    Math.floorMod(v, 100) / 10.0));
        }
        return result;
    }

    private static List<ContainerRef> containers(List<Integer> raw) {
        List<ContainerRef> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int v = raw.get(i);
            String id = Math.floorMod(v, 3) == 0 ? null : "cid_" + v;
            result.add(new ContainerRef("container_" + Math.floorMod(v, 2), coord(v * 17), id));
        }
        return result;
    }

    private static BlockCoord coord(int v) {
        return new BlockCoord(Math.floorMod(v, 1000), 64 + Math.floorMod(v, 3), Math.floorMod(v * 31, 1000));
    }
}