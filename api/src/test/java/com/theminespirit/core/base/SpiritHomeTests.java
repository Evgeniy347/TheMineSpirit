package com.theminespirit.core.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpiritHomeTests {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final BlockCoord SELF = new BlockCoord(0, 64, 0);
    private static final BlockCoord NEAR = new BlockCoord(3, 64, 0);
    private static final BlockCoord FAR = new BlockCoord(30, 64, 0);

    @Test
    void none_NoPoint_IsNotSet() {
        assertFalse(
                SpiritHome.none().isSet(),
                "Дом без точки обязан считаться незаданным: иначе дух пойдёт " + "в точку, которой нет");
        assertEquals(
                Optional.empty(),
                SpiritHome.none().point(),
                "Дом без точки обязан отдавать пустой Optional: null вместо него сломал бы return_home");
    }

    @Test
    void at_Point_IsSetAndKeepsCoordinates() {
        SpiritHome home = SpiritHome.at(NEAR);
        assertTrue(home.isSet(), "Дом с точкой обязан считаться заданным: иначе return_home откажется идти");
        assertEquals(
                NEAR,
                home.point().orElseThrow(),
                "Дом обязан сохранить координаты без подмены: иначе дух уйдёт не туда, куда его поставили");
    }

    @Test
    void at_NullPoint_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> SpiritHome.at(null),
                "Дом без координаты не ставится: null вместо точки увёл бы духа в никуда");
    }

    @Test
    void resolve_SavedHomeAndLegacyBase_KeepsSavedWithoutMigration() {
        MemoryPoint legacy = memory("home", NEAR, MemoryType.BASE, OVERWORLD);

        SpiritHome resolved = SpiritHome.resolve(Optional.of(SpiritHome.at(FAR)), List.of(legacy), OVERWORLD, SELF);

        assertEquals(
                FAR,
                resolved.point().orElseThrow(),
                "Сохранённый дом — источник правды: легаси-память BASE не имеет права его перебить");
    }

    @Test
    void resolve_ExplicitlyClearedHomeAndLegacyBase_StaysCleared() {
        MemoryPoint legacy = memory("home", NEAR, MemoryType.BASE, OVERWORLD);

        SpiritHome resolved = SpiritHome.resolve(Optional.of(SpiritHome.none()), List.of(legacy), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "Снятый дом не воскресает из легаси-памяти: иначе /spirit home clear отменялся бы при перезагрузке");
    }

    @Test
    void resolve_NoSavedHomeAndLegacyBase_MigratesNearestBase() {
        MemoryPoint far = memory("far", FAR, MemoryType.BASE, OVERWORLD);
        MemoryPoint near = memory("camp", NEAR, MemoryType.BASE, OVERWORLD);

        SpiritHome resolved = SpiritHome.resolve(Optional.empty(), List.of(far, near), OVERWORLD, SELF);

        assertEquals(
                NEAR,
                resolved.point().orElseThrow(),
                "Старое сохранение без SpiritHome читается: домом становится ближайшая база, как выбирал прежний "
                        + "WorldFactCollector");
    }

    @Test
    void resolve_LegacyBaseInOtherDimension_NotMigrated() {
        MemoryPoint nether = memory("home", NEAR, MemoryType.BASE, NETHER);

        SpiritHome resolved = SpiritHome.resolve(Optional.empty(), List.of(nether), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "База чужого измерения домом не становится: иначе дух в Верхнем мире шёл бы к точке Нижнего");
    }

    @Test
    void resolve_LegacyResourceNamedHome_NotMigrated() {
        MemoryPoint resource = memory("home", NEAR, MemoryType.RESOURCE, OVERWORLD);

        SpiritHome resolved = SpiritHome.resolve(Optional.empty(), List.of(resource), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "Домом становится только легаси-база (BASE): ресурс с именем home домом не считается");
    }

    @Test
    void resolve_NoLegacyBase_GivesNoHome() {
        SpiritHome resolved = SpiritHome.resolve(Optional.empty(), List.of(), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "Без сохранённого дома и без BASE-памяти дома нет: иначе return_home " + "нашёл бы точку из воздуха");
    }

    private static MemoryPoint memory(String name, BlockCoord pos, MemoryType type, String dimension) {
        return new MemoryPoint(name, type, dimension, pos);
    }
}
