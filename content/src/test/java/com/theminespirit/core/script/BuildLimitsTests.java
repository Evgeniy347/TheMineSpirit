package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.api.CatalogLimits;
import org.junit.jupiter.api.Test;

class BuildLimitsTests {
    @Test
    void digSizeMin_LimitValue_EqualsOne() {
        assertEquals(
                1,
                CatalogLimits.DIG_SIZE_MIN,
                "Сторона куба dig_area начинается с 1: меньший куб нечего копать, число меняет геймдизайн только с согласия"
                        + " Иначе тест digSizeMin_LimitValue_EqualsOne перестанет ловить эту поломку. При смене digSizeMin_LimitValue_EqualsOne проверь это утверждение.");
    }

    @Test
    void digSizeMax_LimitValue_EqualsSixteen() {
        assertEquals(
                16,
                CatalogLimits.DIG_SIZE_MAX,
                "Сторона куба dig_area ограничена 16: больший куб роет слишком долго и грузит тик, число меняет геймдизайн только с согласия"
                        + " Иначе тест digSizeMax_LimitValue_EqualsSixteen перестанет ловить эту поломку. При смене digSizeMax_LimitValue_EqualsSixteen проверь это утверждение.");
    }

    @Test
    void buildVolumeMax_LimitValue_Equals4096() {
        assertEquals(
                4096,
                CatalogLimits.BUILD_VOLUME_MAX,
                "Объём стройки ограничен 4096 (16³): больший объём держит духа часами, число меняет геймдизайн только с согласия"
                        + " Иначе тест buildVolumeMax_LimitValue_Equals4096 перестанет ловить эту поломку. При смене buildVolumeMax_LimitValue_Equals4096 проверь это утверждение.");
    }

    @Test
    void schematicSizeMax_LimitValue_Equals64() {
        assertEquals(
                64,
                CatalogLimits.SCHEMATIC_SIZE_MAX,
                "Сторона схемы ограничена 64: участки поселения бывают 64×64, а пустые поля схемы не добавляют блоков;"
                        + " число меняет геймдизайн только с согласия"
                        + " Иначе тест schematicSizeMax_LimitValue_Equals64 перестанет ловить эту поломку. При смене schematicSizeMax_LimitValue_Equals64 проверь это утверждение.");
    }

    @Test
    void schematicBlocksMax_LimitValue_Equals4096() {
        assertEquals(
                4096,
                CatalogLimits.SCHEMATIC_BLOCKS_MAX,
                "Число блоков схемы ограничено 4096: больше блоков строятся слишком долго, число меняет геймдизайн только с согласия"
                        + " Иначе тест schematicBlocksMax_LimitValue_Equals4096 перестанет ловить эту поломку. При смене schematicBlocksMax_LimitValue_Equals4096 проверь это утверждение.");
    }

    @Test
    void farmPlotSide_LimitValues_MatchDeclared() {
        assertEquals(
                16,
                CatalogLimits.FARM_PLOT_SIDE_MAX,
                "Сторона грядки ограничена 16, как радиус фермы: больше держит духа часами"
                        + " Иначе тест farmPlotSide_LimitValues_MatchDeclared перестанет ловить эту поломку. При смене farmPlotSide_LimitValues_MatchDeclared проверь это утверждение.");
    }

    @Test
    void fence_LimitValues_MatchDeclared() {
        assertEquals(
                3,
                CatalogLimits.FENCE_CORNERS_MIN,
                "Контур забора начинается с трёх углов: двумя точками задаётся линия, а не забор"
                        + " Иначе тест fence_LimitValues_MatchDeclared перестанет ловить эту поломку. При смене fence_LimitValues_MatchDeclared проверь это утверждение.");
        assertEquals(
                64,
                CatalogLimits.FENCE_CORNERS_MAX,
                "Углов контура не больше 64: длинный контур не влезает в один проход"
                        + " Иначе тест fence_LimitValues_MatchDeclared перестанет ловить эту поломку. При смене fence_LimitValues_MatchDeclared проверь это утверждение.");
        assertEquals(
                512,
                CatalogLimits.FENCE_BLOCKS_MAX,
                "Клеток забора не больше 512: больше блоков дух ставит слишком долго"
                        + " Иначе тест fence_LimitValues_MatchDeclared перестанет ловить эту поломку. При смене fence_LimitValues_MatchDeclared проверь это утверждение.");
    }

    @Test
    void campRadius_LimitValues_MatchDeclared() {
        assertEquals(
                1,
                CatalogLimits.CAMP_RADIUS_MIN,
                "Радиус лагеря начинается с 1: меньшая зона не прикроет даже одного духа"
                        + " Иначе тест campRadius_LimitValues_MatchDeclared перестанет ловить эту поломку. При смене campRadius_LimitValues_MatchDeclared проверь это утверждение.");
        assertEquals(
                32,
                CatalogLimits.CAMP_RADIUS_MAX,
                "Радиус лагеря ограничен 32: большая зона расползается дальше обороны"
                        + " Иначе тест campRadius_LimitValues_MatchDeclared перестанет ловить эту поломку. При смене campRadius_LimitValues_MatchDeclared проверь это утверждение.");
        assertEquals(
                16,
                CatalogLimits.CAMP_RADIUS_DEFAULT,
                "Радиус лагеря по умолчанию 16: как радиус обороны и CampTuning.CAMP_RADIUS"
                        + " Иначе тест campRadius_LimitValues_MatchDeclared перестанет ловить эту поломку. При смене campRadius_LimitValues_MatchDeclared проверь это утверждение.");
    }
}
