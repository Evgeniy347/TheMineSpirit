package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.TimeOfDay;
import com.theminespirit.core.tactics.api.Point;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnvFactsBuilderTests {
    private static final Point SELF = new Point(0, 1, 0);

    @Test
    void build_HomeEightBlocksAway_HomeReachableWithDistance() {
        EnvFacts facts =
                EnvFactsBuilder.build(EnvReading.NOTHING, SELF, 15, TimeOfDay.DAY, Optional.of(new Point(8, 1, 0)));

        assertTrue(
                facts.homeReachable(),
                "Дом в памяти духа — путь к нему доступен ядру отхода HOME"
                        + " Иначе тест build_HomeEightBlocksAway_HomeReachableWithDistance перестанет ловить эту поломку. При смене build_HomeEightBlocksAway_HomeReachableWithDistance проверь это утверждение.");
        assertEquals(
                8.0,
                facts.homeDistance(),
                1e-9,
                "Расстояние до дома считается от духа до точки дома"
                        + " Иначе тест build_HomeEightBlocksAway_HomeReachableWithDistance перестанет ловить эту поломку. При смене build_HomeEightBlocksAway_HomeReachableWithDistance проверь это утверждение.");
    }

    @Test
    void build_NoHome_HomeFarAndNotReachable() {
        EnvFacts facts = EnvFactsBuilder.build(EnvReading.NOTHING, SELF, 15, TimeOfDay.DAY, Optional.empty());

        assertFalse(
                facts.homeReachable(),
                "Без точки дома отход HOME недоступен, иначе дух побежит в никуда"
                        + " При смене build_NoHome_HomeFarAndNotReachable проверь это утверждение.");
        assertEquals(
                EnvFacts.FAR_DISTANCE,
                facts.homeDistance(),
                "Нет дома — расстояние FAR"
                        + " Иначе тест build_NoHome_HomeFarAndNotReachable перестанет ловить эту поломку. При смене build_NoHome_HomeFarAndNotReachable проверь это утверждение.");
    }

    @Test
    void build_HomeBeyondFar_DistanceClampedToFar() {
        EnvFacts facts =
                EnvFactsBuilder.build(EnvReading.NOTHING, SELF, 15, TimeOfDay.DAY, Optional.of(new Point(5000, 1, 0)));

        assertEquals(
                EnvFacts.FAR_DISTANCE,
                facts.homeDistance(),
                "Дом дальше FAR не должен ломать EnvFacts: расстояние зажимается до FAR"
                        + " Иначе тест build_HomeBeyondFar_DistanceClampedToFar перестанет ловить эту поломку. При смене build_HomeBeyondFar_DistanceClampedToFar проверь это утверждение.");
    }

    @Test
    void build_Night_PassesTimeAndLight() {
        EnvFacts facts = EnvFactsBuilder.build(EnvReading.NOTHING, SELF, 3, TimeOfDay.NIGHT, Optional.empty());

        assertTrue(
                facts.night(),
                "Ночь из мира дойдёт до ядра без подмены: от неё зависит фактор DARKNESS"
                        + " Иначе тест build_Night_PassesTimeAndLight перестанет ловить эту поломку. При смене build_Night_PassesTimeAndLight проверь это утверждение.");
        assertEquals(
                3,
                facts.light(),
                "Освещённость из мира дойдёт до ядра без подмены"
                        + " Иначе тест build_Night_PassesTimeAndLight перестанет ловить эту поломку. При смене build_Night_PassesTimeAndLight проверь это утверждение.");
    }

    @Test
    void build_Reading_CarriesLavaWaterCoverAndDrop() {
        EnvReading reading = new EnvReading(2.5, 4, Optional.empty(), 6.0, Optional.empty(), 3.5, 10, false);

        EnvFacts facts = EnvFactsBuilder.build(reading, SELF, 15, TimeOfDay.DAY, Optional.empty());

        assertEquals(
                2.5,
                facts.lavaNear(),
                "Лава из скана обязана дойти до фактов"
                        + " Иначе тест build_Reading_CarriesLavaWaterCoverAndDrop перестанет ловить эту поломку. При смене build_Reading_CarriesLavaWaterCoverAndDrop проверь это утверждение.");
        assertEquals(
                4.0,
                facts.dropHeight(),
                "Обрыв из скана обязан дойти до фактов"
                        + " Иначе тест build_Reading_CarriesLavaWaterCoverAndDrop перестанет ловить эту поломку. При смене build_Reading_CarriesLavaWaterCoverAndDrop проверь это утверждение.");
        assertEquals(
                6.0,
                facts.waterDistance(),
                "Вода из скана обязана дойти до фактов"
                        + " Иначе тест build_Reading_CarriesLavaWaterCoverAndDrop перестанет ловить эту поломку. При смене build_Reading_CarriesLavaWaterCoverAndDrop проверь это утверждение.");
        assertEquals(
                3.5,
                facts.coverDistance(),
                "Укрытие из скана обязано дойти до фактов"
                        + " Иначе тест build_Reading_CarriesLavaWaterCoverAndDrop перестанет ловить эту поломку. При смене build_Reading_CarriesLavaWaterCoverAndDrop проверь это утверждение.");
    }
}
