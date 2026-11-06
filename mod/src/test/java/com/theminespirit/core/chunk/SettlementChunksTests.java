package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementChunksTests {
    private static final BlockCoord HOME = new BlockCoord(8, 64, 8);

    @Test
    void around_HomeWithWorkRadius_CoversChunksOfWholePlot() {
        Set<ChunkCoord> chunks = SettlementChunks.around(List.of(HOME), SettlementChunks.WORK_RADIUS_BLOCKS);

        assertEquals(
                Set.of(
                        new ChunkCoord(-1, -1),
                        new ChunkCoord(-1, 0),
                        new ChunkCoord(-1, 1),
                        new ChunkCoord(0, -1),
                        new ChunkCoord(0, 0),
                        new ChunkCoord(0, 1),
                        new ChunkCoord(1, -1),
                        new ChunkCoord(1, 0),
                        new ChunkCoord(1, 1)),
                chunks,
                "Дом с участком 33×33 блока лежит в девяти чанках вокруг своего: если набор другой, доска и "
                        + "участки без духов останутся выгруженными, пока на сервере есть игрок");
    }

    @Test
    void around_HomeOnChunkEdge_CoversChunkAndNeighbours() {
        Set<ChunkCoord> chunks =
                SettlementChunks.around(List.of(new BlockCoord(16, 64, 16)), SettlementChunks.WORK_RADIUS_BLOCKS);

        assertEquals(
                Set.of(
                        new ChunkCoord(0, 0),
                        new ChunkCoord(0, 1),
                        new ChunkCoord(0, 2),
                        new ChunkCoord(1, 0),
                        new ChunkCoord(1, 1),
                        new ChunkCoord(1, 2),
                        new ChunkCoord(2, 0),
                        new ChunkCoord(2, 1),
                        new ChunkCoord(2, 2)),
                chunks,
                "Дом ровно на границе чанка обязан держать свой чанк и восемь соседних: округление вниз "
                        + "не должно терять западный и северный ряд участка");
    }

    @Test
    void around_SeveralHomes_UnitesTheirChunks() {
        Set<ChunkCoord> chunks = SettlementChunks.around(
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(160, 64, 0)), SettlementChunks.WORK_RADIUS_BLOCKS);

        assertEquals(
                18,
                chunks.size(),
                "Два дома в трёх чанках друг от друга обязаны дать объединение участков без дублей: "
                        + "каждый дом — свои девять чанков, пересечений нет");
    }

    @Test
    void forPlan_BigSettlement_HoldsEveryPlacementChunk() {
        TownPlan plan = TownFixtures.flatPlan();
        Set<ChunkCoord> chunks =
                SettlementChunks.forPlan(plan, List.of(plan.anchor()), SettlementChunks.WORK_RADIUS_BLOCKS);
        Set<ChunkCoord> homesOnly =
                SettlementChunks.around(List.of(plan.anchor()), SettlementChunks.WORK_RADIUS_BLOCKS);
        for (Placement place : plan.placements()) {
            ChunkCoord chunk = new ChunkCoord(place.x() >> 4, place.z() >> 4);
            assertTrue(
                    chunks.contains(chunk),
                    "Место '" + place.id() + "' в (" + place.x() + ", " + place.z() + ") лежит в чанке " + chunk
                            + ", а поселение держит только чанки вокруг домов: дух идёт к цели, чанк выгружен, "
                            + "position_tick замирает, задание проваливается «нет прогресса»");
        }
        Placement storage = plan.placement("storage").orElseThrow();
        assertFalse(
                homesOnly.contains(new ChunkCoord(storage.x() >> 4, storage.z() >> 4)),
                "Склад обязан быть дальше радиуса дома: иначе тест не воспроизводит observe6 (−144,71,−368 "
                        + "в чанке [−9,−23] вне чанков вокруг дома)");
    }

    @Test
    void around_NoAnchors_IsEmpty() {
        assertEquals(
                Set.of(),
                SettlementChunks.around(List.of(), SettlementChunks.WORK_RADIUS_BLOCKS),
                "У поселения без домов и лагерей нет чанков: пустой набор, а не чанк (0,0)");
    }

    @Test
    void around_NegativeRadius_ThrowsIllegalArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SettlementChunks.around(List.of(HOME), -1),
                "Отрицательный радиус работы — ошибка конфигурации: молча пустой набор оставил бы поселение "
                        + "без чанков. Ожидается IllegalArgumentException");
    }

    @Test
    void transition_NoPlayers_RemovesEveryHeldChunk() {
        Set<ChunkCoord> settlement = SettlementChunks.around(List.of(HOME), SettlementChunks.WORK_RADIUS_BLOCKS);

        ChunkDiff diff = SettlementChunks.transition(settlement, settlement, false);

        assertEquals(
                settlement,
                diff.toRemove(),
                "Без игроков на сервере поселение обязано выгрузиться полностью: сервер не тратит ресурсы "
                        + "на тикающие чанки");
        assertEquals(
                Set.of(),
                diff.toAdd(),
                "Без игроков добавлять чанки нечего: иначе поселение останется нагруженным навсегда");
    }

    @Test
    void transition_FirstPlayerJoins_AddsWholeSettlement() {
        Set<ChunkCoord> settlement = SettlementChunks.around(List.of(HOME), SettlementChunks.WORK_RADIUS_BLOCKS);

        ChunkDiff diff = SettlementChunks.transition(Set.of(), settlement, true);

        assertEquals(
                settlement,
                diff.toAdd(),
                "С первым игроком (в любом мире) чанки поселения обязаны загрузиться целиком, включая "
                        + "участки без духов");
        assertEquals(
                Set.of(),
                diff.toRemove(),
                "При входе первого игрока снимать нечего: билеты ставятся на весь набор поселения");
    }

    @Test
    void transition_LastPlayerLeaves_RemovesChunksAddedForPlayers() {
        Set<ChunkCoord> settlement = SettlementChunks.around(List.of(HOME), SettlementChunks.WORK_RADIUS_BLOCKS);
        Set<ChunkCoord> held = ChunkSquare.apply(Set.of(), SettlementChunks.transition(Set.of(), settlement, true));

        ChunkDiff diff = SettlementChunks.transition(held, settlement, false);

        assertEquals(
                settlement,
                diff.toRemove(),
                "После выхода последнего игрока обязаны сняться все билеты, поставленные для поселения: "
                        + "иначе мир продолжает тикать без игроков");
    }

    @Test
    void around_ManySpiritsInOneHome_KeepsOneTicketSet() {
        List<BlockCoord> many = new ArrayList<>(Collections.nCopies(512, HOME));
        Set<ChunkCoord> one = SettlementChunks.around(List.of(HOME), SettlementChunks.WORK_RADIUS_BLOCKS);
        Set<ChunkCoord> crowded = SettlementChunks.around(many, SettlementChunks.WORK_RADIUS_BLOCKS);

        assertEquals(
                one,
                crowded,
                "Пятьсот двенадцать духов с одним домом обязаны держать тот же набор чанков, что и один дух: "
                        + "тикеты считаются на поселение, а не на духа");
        ChunkDiff crowdedDiff = SettlementChunks.transition(Set.of(), crowded, true);
        assertEquals(
                one.size(),
                crowdedDiff.toAdd().size(),
                "Число операций постановки билетов не должно расти с числом духов: пятьсот духов в одном "
                        + "поселении ставят столько же билетов, сколько один");
    }
}
