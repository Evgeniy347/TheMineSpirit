package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DropRouteTests {
    private static DropRef drop(String id, double x, double z, boolean pickable) {
        return new DropRef(id, "minecraft:cobblestone", 1, x, 0, z, pickable);
    }

    @Test
    void next_SeveralDrops_ReturnsNearest() {
        List<DropRef> drops = List.of(drop("far", 8, 0, true), drop("near", 2, 0, true), drop("mid", 4, 0, true));

        assertEquals(
                Optional.of(drops.get(1)),
                DropRoute.next(0, 0, 0, drops, Set.of()),
                "Дух идёт к ближайшей кучке: дальняя не должна обгонять ближнюю");
    }

    @Test
    void next_NearestIsSkipped_ReturnsNextNearest() {
        List<DropRef> drops = List.of(drop("near", 2, 0, true), drop("mid", 4, 0, true));

        assertEquals(
                Optional.of(drops.get(1)),
                DropRoute.next(0, 0, 0, drops, Set.of("near")),
                "Пропущенная кучка (до неё не дошли) не должна выбираться снова: иначе дух зациклится на ней");
    }

    @Test
    void next_NearestNotPickable_IsIgnored() {
        List<DropRef> drops = List.of(drop("full", 1, 0, false), drop("ok", 5, 0, true));

        assertEquals(
                Optional.of(drops.get(1)),
                DropRoute.next(0, 0, 0, drops, Set.of()),
                "Кучка, которая не помещается в инвентарь, не выбирается: иначе дух стоял бы у неё вечно");
    }

    @Test
    void next_NoCandidates_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                DropRoute.next(0, 0, 0, List.of(drop("a", 1, 0, true), drop("b", 2, 0, false)), Set.of("a")),
                "Когда все кучки пропущены или не подбираются, маршрута нет: вызывающий решает, успех это или провал");
        assertEquals(
                Optional.empty(),
                DropRoute.next(0, 0, 0, List.of(), Set.of()),
                "Пустой список кучек даёт пустой маршрут");
    }

    @Test
    void next_EqualDistance_ReturnsSmallerId() {
        List<DropRef> drops = List.of(drop("b", 3, 0, true), drop("a", -3, 0, true));

        assertEquals(
                "a",
                DropRoute.next(0, 0, 0, drops, Set.of()).orElseThrow().id(),
                "При равном расстоянии выбирается меньший id: выбор не должен зависеть от порядка сущностей в мире");
    }

    @Test
    void next_VerticalDistance_CountsTowardsNearest() {
        DropRef high = new DropRef("high", "minecraft:stick", 1, 1, 4, 0, true);
        DropRef flat = new DropRef("flat", "minecraft:stick", 1, 3, 0, 0, true);

        assertEquals(
                "flat",
                DropRoute.next(0, 0, 0, List.of(high, flat), Set.of())
                        .orElseThrow()
                        .id(),
                "Расстояние считается в трёх измерениях: кучка на столбе дальше кучки на полу при равном сдвиге по X");
    }
}
