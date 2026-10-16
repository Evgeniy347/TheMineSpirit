package com.theminespirit.core.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BedChoiceTests {
    private static final BlockCoord HOME = new BlockCoord(0, 64, 0);
    private static final int RADIUS = 32;
    private static final BlockCoord FROM = new BlockCoord(0, 64, 0);
    private static final BlockCoord NEAR = new BlockCoord(2, 64, 0);
    private static final BlockCoord FAR = new BlockCoord(20, 64, 0);

    @Test
    void choose_OwnBedFreeInside_ReturnsOwnBedEvenIfFar() {
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.of(FAR), List.of(NEAR, FAR), Set.of(), FROM, HOME, RADIUS);

        assertEquals(Optional.of(FAR), chosen,
                "Своя кровать предпочтительнее ближней чужой: дух ложится на привычное место");
    }

    @Test
    void choose_OwnBedTaken_FallsBackToNearestFree() {
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.of(FAR), List.of(NEAR, FAR), Set.of(FAR), FROM, HOME, RADIUS);

        assertEquals(Optional.of(NEAR), chosen,
                "Занятая своя кровать не берётся: дух ищет свободную, а не ложится поверх соседа");
    }

    @Test
    void choose_OwnBedOutsideSettlement_IgnoredInFavourOfFreeInside() {
        BlockCoord outside = new BlockCoord(RADIUS + 5, 64, 0);
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.of(outside), List.of(NEAR, outside), Set.of(), FROM, HOME, RADIUS);

        assertEquals(Optional.of(NEAR), chosen,
                "Своя кровать за границей поселения не берётся ночью: за границу дух не выходит");
    }

    @Test
    void choose_OwnBedNotPresentInWorld_FallsBackToNearestFree() {
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.of(FAR), List.of(NEAR), Set.of(), FROM, HOME, RADIUS);

        assertEquals(Optional.of(NEAR), chosen,
                "Снесённая своя кровать не выбирается: помнится только та, что стоит в мире");
    }

    @Test
    void choose_NoOwnBed_NearestFreeWins() {
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.empty(), List.of(FAR, NEAR), Set.of(), FROM, HOME, RADIUS);

        assertEquals(Optional.of(NEAR), chosen,
                "Без своей кровати берётся ближайшая свободная в поселении");
    }

    @Test
    void choose_NearestTaken_NextFreeWins() {
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.empty(), List.of(NEAR, FAR), Set.of(NEAR), FROM, HOME, RADIUS);

        assertEquals(Optional.of(FAR), chosen,
                "Занятую кровать не берут: двое духов не ложатся в одну кровать");
    }

    @Test
    void choose_AllTaken_Empty() {
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.empty(), List.of(NEAR, FAR), Set.of(NEAR, FAR), FROM, HOME, RADIUS);

        assertTrue(chosen.isEmpty(),
                "Все кровати заняты — выбора нет: дух остаётся без места, а не ложится к соседу");
    }

    @Test
    void choose_AllBedsOutsideSettlement_Empty() {
        BlockCoord farOutside = new BlockCoord(1000, 64, 0);
        Optional<BlockCoord> chosen =
                BedChoice.choose(Optional.empty(), List.of(farOutside), Set.of(), FROM, HOME, RADIUS);

        assertTrue(chosen.isEmpty(),
                "Кровати за границей поселения ночью не выбираются: иначе дух уйдёт за поводок");
    }

    @Test
    void choose_NullHome_Throws() {
        assertThrows(NullPointerException.class,
                () -> BedChoice.choose(Optional.empty(), List.of(NEAR), Set.of(), FROM, null, RADIUS),
                "Без дома нет границы поселения: ошибка обязана быть явной, а не молчаливым выбором");
    }

    @Test
    void choose_RadiusBelowOne_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BedChoice.choose(Optional.empty(), List.of(NEAR), Set.of(), FROM, HOME, 0),
                "Радиус меньше 1 не оставил бы ни одной кровати своей: отказ обязан быть явной ошибкой");
    }
}
