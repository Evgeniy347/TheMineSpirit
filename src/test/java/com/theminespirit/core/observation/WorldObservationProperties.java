package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;

class WorldObservationProperties {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final int MAX_COUNT = 64;

    @Property
    void hash_InventoryOrderChanged_StaysEqual(@ForAll @Size(max = 16) List<Integer> raw) {
        List<InventoryEntry> entries = entries(raw);
        List<InventoryEntry> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);

        assertEquals(
                observation(entries, 32, 1L).hash(),
                observation(reversed, 32, 1L).hash(),
                "hash наблюдения обязан не зависеть от порядка списка: иначе один и тот же мир даёт разные хэши, "
                        + "и идемпотентность задач по request_id ломается");
    }

    @Property
    void json_RoundTrip_EqualsOriginal(@ForAll int radius, @ForAll long tick) {
        WorldObservation original = observation(entries(List.of(radius)), radius, tick);

        assertEquals(
                original,
                WorldObservation.fromJson(original.toJson()),
                "наблюдение обязано восстанавливаться из своего JSON без потерь: иначе REST-клиент и ИИ увидят "
                        + "не тот мир, что сервер, и план будет строиться по искажённым данным");
    }

    private static WorldObservation observation(List<InventoryEntry> inventory, int radius, long tick) {
        return new WorldObservation("spirit", tick, ORIGIN, inventory, List.of(), List.of(), List.of(),
                Math.floorMod(radius, 4096));
    }

    private static List<InventoryEntry> entries(List<Integer> raw) {
        List<InventoryEntry> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            result.add(new InventoryEntry("item_" + i, Math.floorMod(raw.get(i), MAX_COUNT + 1)));
        }
        return result;
    }
}
