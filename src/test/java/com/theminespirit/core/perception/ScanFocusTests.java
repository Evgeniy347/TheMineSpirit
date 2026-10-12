package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ContainerRef;
import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScanFocusTests {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);

    static Stream<Arguments> foci() {
        return Stream.of(
                Arguments.of(ScanFocus.ALL, true, true, true, false, true),
                Arguments.of(ScanFocus.BLOCKS, true, false, false, false, true),
                Arguments.of(ScanFocus.ENTITIES, false, true, false, true, false),
                Arguments.of(ScanFocus.CONTAINERS, false, false, true, false, true));
    }

    @ParameterizedTest(name = "{0}: блоки={1} сущности={2} контейнеры={3} фильтр сущностей={4} срезы={5}")
    @MethodSource("foci")
    void focus_Each_CollectsOnlyItsParts(ScanFocus focus, boolean blocks, boolean entities, boolean containers,
            boolean filtersEntities, boolean needsSlices) {
        assertEquals(blocks, focus.collectsBlocks(), focus + " собирает блоки: " + blocks
                + ", иначе scan_blocks отдаёт лишнее или scan теряет блоки");
        assertEquals(entities, focus.collectsEntities(), focus + " собирает сущности: " + entities
                + ", иначе scan_entities не отличается от полного скана по стоимости");
        assertEquals(containers, focus.collectsContainers(), focus + " собирает контейнеры: " + containers);
        assertEquals(filtersEntities, focus.filtersEntities(),
                focus + ": фильтр применяется к сущностям: " + filtersEntities
                        + ", иначе фильтр scan_entities отбирает блоки, которых нет в выдаче");
        assertEquals(needsSlices, focus.needsSlices(),
                focus + " читает срезы блоков: " + needsSlices + ", иначе scan_entities тратит тики на пустой обход");
    }

    @Test
    void of_BlocksAndEntitiesQueries_MapToSameNamedFocus() {
        assertEquals(ScanFocus.BLOCKS, ScanFocus.of(PerceptionType.BLOCKS),
                "запрос блоков обязан собирать только блоки");
        assertEquals(ScanFocus.ENTITIES, ScanFocus.of(PerceptionType.ENTITIES),
                "запрос сущностей обязан собирать только сущности");
    }

    @Test
    void of_HazardsQuery_IsRejectedBecauseHazardsAreNotSliceScanned() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ScanFocus.of(PerceptionType.HAZARDS),
                "опасности ищет sense_hazard, а не скан срезов: фокус для них обязан отклоняться");

        assertTrue(error.getMessage().contains("sense_hazard"),
                "сообщение обязано назвать скрипт опасностей, сейчас: " + error.getMessage());
    }

    @Test
    void resultOf_BlocksFocus_KeepsOnlyBlocksOfObservation() {
        PerceptionResult result = ScanFocus.BLOCKS.resultOf(observation(), ScanRadius.of(8)).orElseThrow();

        assertEquals(PerceptionResult.blocks(ScanRadius.of(8), List.of(new ItemCount("minecraft:iron_ore", 3))), result,
                "результат scan_blocks обязан содержать блоки наблюдения и ничего больше");
    }

    @Test
    void resultOf_EntitiesFocus_KeepsOnlyEntitiesOfObservation() {
        PerceptionResult result = ScanFocus.ENTITIES.resultOf(observation(), ScanRadius.of(8)).orElseThrow();

        assertEquals(PerceptionResult.entities(ScanRadius.of(8), List.of(new EntityRef("minecraft:cow", ORIGIN, 2.0))),
                result, "результат scan_entities обязан содержать сущности наблюдения и ничего больше");
    }

    @Test
    void resultOf_AllAndContainersFocus_AreEmptyBecauseTheyFillObservation() {
        assertEquals(Optional.empty(), ScanFocus.ALL.resultOf(observation(), ScanRadius.of(8)),
                "полный скан кладёт наблюдение, а не результат восприятия");
        assertEquals(Optional.empty(), ScanFocus.CONTAINERS.resultOf(observation(), ScanRadius.of(8)),
                "поиск контейнера читает наблюдение сам и результата восприятия не строит");
    }

    private static WorldObservation observation() {
        return new WorldObservation("bot", 1, ORIGIN, List.of(), List.of(new ItemCount("minecraft:iron_ore", 3)),
                List.of(new EntityRef("minecraft:cow", ORIGIN, 2.0)),
                List.of(new ContainerRef("minecraft:chest", ORIGIN)), 8);
    }
}
