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
                "Дом без точки обязан считаться незаданным: иначе дух пойдёт " + "в точку, которой нет"
                        + " При смене none_NoPoint_IsNotSet проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                SpiritHome.none().point(),
                "Дом без точки обязан отдавать пустой Optional: null вместо него сломал бы return_home"
                        + " Иначе тест none_NoPoint_IsNotSet перестанет ловить эту поломку. При смене none_NoPoint_IsNotSet проверь это утверждение.");
    }

    @Test
    void at_Point_IsSetAndKeepsCoordinates() {
        SpiritHome home = SpiritHome.at(NEAR);
        assertTrue(
                home.isSet(),
                "Дом с точкой обязан считаться заданным: иначе return_home откажется идти"
                        + " При смене at_Point_IsSetAndKeepsCoordinates проверь это утверждение.");
        assertEquals(
                NEAR,
                home.point().orElseThrow(),
                "Дом обязан сохранить координаты без подмены: иначе дух уйдёт не туда, куда его поставили"
                        + " При смене at_Point_IsSetAndKeepsCoordinates проверь это утверждение.");
    }

    @Test
    void at_NullPoint_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> SpiritHome.at(null),
                "Дом без координаты не ставится: null вместо точки увёл бы духа в никуда"
                        + " Иначе тест at_NullPoint_Throws перестанет ловить эту поломку. При смене at_NullPoint_Throws проверь это утверждение.");
    }

    @Test
    void resolve_SavedHomeAndLegacyBase_KeepsSavedWithoutMigration() {
        MemoryPoint legacy = memory("home", NEAR, MemoryType.BASE, OVERWORLD);

        SpiritHome resolved = SpiritHome.resolve(Optional.of(SpiritHome.at(FAR)), List.of(legacy), OVERWORLD, SELF);

        assertEquals(
                FAR,
                resolved.point().orElseThrow(),
                "Сохранённый дом — источник правды: легаси-память BASE не имеет права его перебить"
                        + " Иначе тест resolve_SavedHomeAndLegacyBase_KeepsSavedWithoutMigration перестанет ловить эту поломку. При смене resolve_SavedHomeAndLegacyBase_KeepsSavedWithoutMigration проверь это утверждение.");
    }

    @Test
    void resolve_ExplicitlyClearedHomeAndLegacyBase_StaysCleared() {
        MemoryPoint legacy = memory("home", NEAR, MemoryType.BASE, OVERWORLD);

        SpiritHome resolved = SpiritHome.resolve(Optional.of(SpiritHome.none()), List.of(legacy), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "Снятый дом не воскресает из легаси-памяти: иначе /spirit home clear отменялся бы при перезагрузке"
                        + " При смене resolve_ExplicitlyClearedHomeAndLegacyBase_StaysCleared проверь это утверждение.");
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
                        + "WorldFactCollector"
                        + " Иначе тест resolve_NoSavedHomeAndLegacyBase_MigratesNearestBase перестанет ловить эту поломку. При смене resolve_NoSavedHomeAndLegacyBase_MigratesNearestBase проверь это утверждение.");
    }

    @Test
    void resolve_LegacyBaseInOtherDimension_NotMigrated() {
        MemoryPoint nether = memory("home", NEAR, MemoryType.BASE, NETHER);

        SpiritHome resolved = SpiritHome.resolve(Optional.empty(), List.of(nether), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "База чужого измерения домом не становится: иначе дух в Верхнем мире шёл бы к точке Нижнего"
                        + " При смене resolve_LegacyBaseInOtherDimension_NotMigrated проверь это утверждение.");
    }

    @Test
    void resolve_LegacyResourceNamedHome_NotMigrated() {
        MemoryPoint resource = memory("home", NEAR, MemoryType.RESOURCE, OVERWORLD);

        SpiritHome resolved = SpiritHome.resolve(Optional.empty(), List.of(resource), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "Домом становится только легаси-база (BASE): ресурс с именем home домом не считается"
                        + " Иначе тест resolve_LegacyResourceNamedHome_NotMigrated перестанет ловить эту поломку. При смене resolve_LegacyResourceNamedHome_NotMigrated проверь это утверждение.");
    }

    @Test
    void resolve_NoLegacyBase_GivesNoHome() {
        SpiritHome resolved = SpiritHome.resolve(Optional.empty(), List.of(), OVERWORLD, SELF);

        assertFalse(
                resolved.isSet(),
                "Без сохранённого дома и без BASE-памяти дома нет: иначе return_home " + "нашёл бы точку из воздуха"
                        + " При смене resolve_NoLegacyBase_GivesNoHome проверь это утверждение.");
    }

    private static MemoryPoint memory(String name, BlockCoord pos, MemoryType type, String dimension) {
        return new MemoryPoint(name, type, dimension, pos);
    }
}
