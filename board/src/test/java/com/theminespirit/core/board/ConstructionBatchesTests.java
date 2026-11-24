package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConstructionBatchesTests {
    private static final ConstructionBatches.ChunkCell ZERO = new ConstructionBatches.ChunkCell(0, 0);

    @Test
    void spiral_FirstBatch_IsNearestThreeByThree() {
        List<ConstructionBatches.ChunkCell> order = ConstructionBatches.spiral(ZERO.x(), ZERO.z(), 4);
        List<ConstructionBatches.ChunkCell> first = ConstructionBatches.batch(order, 0);
        Set<ConstructionBatches.ChunkCell> expected = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                expected.add(new ConstructionBatches.ChunkCell(x, z));
            }
        }

        assertEquals(
                expected,
                new HashSet<>(first),
                "Первая пачка обязана покрыть ровно 3×3 чанка вокруг игрока: иначе ближайшие к игроку блоки придут позже дальних и подсветка начнётся не с того места; при смене порядка спирали в ConstructionBatches проверь первые девять чанков");
    }

    @Test
    void spiral_FirstRing_IsClockwiseFromNorthWest() {
        List<ConstructionBatches.ChunkCell> order = ConstructionBatches.spiral(ZERO.x(), ZERO.z(), 1);

        assertEquals(
                List.of(
                        new ConstructionBatches.ChunkCell(0, 0),
                        new ConstructionBatches.ChunkCell(-1, -1),
                        new ConstructionBatches.ChunkCell(0, -1),
                        new ConstructionBatches.ChunkCell(1, -1),
                        new ConstructionBatches.ChunkCell(1, 0),
                        new ConstructionBatches.ChunkCell(1, 1),
                        new ConstructionBatches.ChunkCell(0, 1),
                        new ConstructionBatches.ChunkCell(-1, 1),
                        new ConstructionBatches.ChunkCell(-1, 0)),
                order,
                "Кольцо чанков обязано идти по часовой стрелке от северо-западного угла: иначе порядок выдачи станет непредсказуемым и проверка пачек перестанет ловить перестановки; при смене обхода кольца в ConstructionBatches проверь последовательность девяти клеток");
    }

    @Test
    void batches_LastBatch_HasNoMore() {
        List<ConstructionBatches.ChunkCell> order = ConstructionBatches.spiral(ZERO.x(), ZERO.z(), 4);

        assertEquals(
                9,
                ConstructionBatches.batch(order, 8).size(),
                "Последняя пачка обязана быть полной: 81 чанк в радиусе четырёх делится на девять пачек по девять, иначе клиент запросит лишнюю пустую; при смене размера пачки в ConstructionBatches проверь разбиение 81 = 9 × 9");
        assertFalse(
                ConstructionBatches.hasMore(order, 8),
                "После последней пачки клиент не должен запрашивать следующую: иначе круг аудита не закроется и повторится лишний запрос; при смене hasMore в ConstructionBatches проверь границу последней пачки");
        assertTrue(
                ConstructionBatches.batch(order, 9).isEmpty(),
                "Пачка за пределами спирали обязана быть пустой: иначе сервер будет аудитировать несуществующие чанки; при смене batch в ConstructionBatches проверь запрос за последней пачкой");
    }

    @Test
    void spiral_AllChunks_AreUnique() {
        List<ConstructionBatches.ChunkCell> order = ConstructionBatches.spiral(ZERO.x(), ZERO.z(), 4);

        assertEquals(
                81,
                new HashSet<>(order).size(),
                "Спираль не должна повторять чанки: иначе аудит посчитает одну и ту же клетку дважды и раздует пакеты; при смене обхода кольца в ConstructionBatches проверь уникальность всех 81 чанка");
    }

    @Test
    void order_OnlySettlementChunks_KeepClockwiseOrder() {
        Set<ConstructionBatches.ChunkCell> included = Set.of(
                new ConstructionBatches.ChunkCell(0, 0),
                new ConstructionBatches.ChunkCell(2, 0),
                new ConstructionBatches.ChunkCell(-1, 1));

        List<ConstructionBatches.ChunkCell> order = ConstructionBatches.order(included, ZERO.x(), ZERO.z());

        assertEquals(
                List.of(
                        new ConstructionBatches.ChunkCell(0, 0),
                        new ConstructionBatches.ChunkCell(-1, 1),
                        new ConstructionBatches.ChunkCell(2, 0)),
                order,
                "Порядок обязан фильтровать спираль чанками поселения, сохраняя обход по часовой стрелке: иначе пачки выйдут за пределы поселения или перестанут быть ближайшими первыми; при смене фильтра поселения в ConstructionBatches проверь порядок трёх чанков");
    }

    @Test
    void order_NoSettlementChunks_IsEmpty() {
        List<ConstructionBatches.ChunkCell> order = ConstructionBatches.order(Set.of(), ZERO.x(), ZERO.z());

        assertTrue(
                order.isEmpty(),
                "Без чанков поселения порядок обязан быть пустым: иначе клиент запросит пачки с пустым районом и будет крутить аудит впустую; при смене order в ConstructionBatches проверь пустой набор");
    }
}
