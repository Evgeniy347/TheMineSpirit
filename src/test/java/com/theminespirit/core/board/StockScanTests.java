package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StockScanTests {

    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);

    /** Мир-заглушка: контейнеры в заданных клетках, часть чанков «не загружена». */
    private static final class FakeBlocks implements StockScan.Blocks {
        private final Set<BlockCoord> containers = new HashSet<>();
        private final Set<BlockCoord> unloaded = new HashSet<>();
        private int reads;

        FakeBlocks containerAt(BlockCoord pos) {
            containers.add(pos);
            return this;
        }

        FakeBlocks unloaded(BlockCoord pos) {
            unloaded.add(pos);
            return this;
        }

        @Override
        public boolean isLoaded(BlockCoord pos) {
            return !unloaded.contains(pos);
        }

        @Override
        public boolean isContainer(BlockCoord pos) {
            reads++;
            return containers.contains(pos);
        }
    }

    @Test
    void containers_AreFound_WithinRadius() {
        FakeBlocks blocks = new FakeBlocks()
                .containerAt(new BlockCoord(0, 64, 3))
                .containerAt(new BlockCoord(-2, 64, 1))
                .containerAt(new BlockCoord(20, 64, 20));

        StockScan.Result result = StockScan.containers(List.of(CENTER), 8, 4, 4096, blocks);

        assertEquals(
                List.of(new BlockCoord(-2, 64, 1), new BlockCoord(0, 64, 3)),
                result.containers(),
                "Контейнеры ближе к центру читаются первыми, а сундук в 20 блоках за радиусом — "
                        + "нет: снимок видит склад вокруг работы, а не весь мир");
    }

    @Test
    void containers_UnderBudget_StopsAtBudget() {
        BlockCoord first = new BlockCoord(-8, 60, -8);
        FakeBlocks blocks = new FakeBlocks().containerAt(first);

        StockScan.Result result = StockScan.containers(List.of(CENTER), 8, 4, 100, blocks);

        assertEquals(
                100,
                result.blockReads(),
                "Обход обязан остановиться ровно на бюджете: иначе снимок перебирал бы куб "
                        + "вокруг каждого духа и грузил чанки на главном потоке (причина "
                        + "41-минутного GameTest)");
        assertEquals(
                List.of(first),
                result.containers(),
                "Сундук в пределах прочитанной части всё равно найден: бюджет режет обход, " + "а не отменяет поиск");
    }

    @Test
    void containers_UnloadedChunks_AreSkipped() {
        FakeBlocks blocks =
                new FakeBlocks().containerAt(new BlockCoord(1, 64, 1)).unloaded(new BlockCoord(1, 64, 1));

        StockScan.Result result = StockScan.containers(List.of(CENTER), 8, 4, 4096, blocks);

        assertEquals(
                List.of(),
                result.containers(),
                "Незагруженный чанк пропускается: чтение такого чанка ждёт генерации и "
                        + "останавливает серверный тик — лучше пропустить сундук до следующей "
                        + "пересборки");
        assertTrue(result.blockReads() > 0, "Обход всё равно читает блоки: пропуск чанка — не отказ от обхода");
    }

    @Test
    void containers_EmptyCenters_ReadsNothing() {
        FakeBlocks blocks = new FakeBlocks().containerAt(CENTER);

        StockScan.Result result = StockScan.containers(List.of(), 8, 4, 4096, blocks);

        assertEquals(0, result.blockReads(), "Без центров обходить нечего: снимок не должен читать мир просто так");
        assertEquals(
                List.of(),
                result.containers(),
                "Без центров найденных контейнеров нет: снимок не придумывает склад из ничего");
    }

    @Test
    void containers_OverlappingCenters_CountedOnce() {
        BlockCoord near = new BlockCoord(0, 64, 8);
        FakeBlocks blocks = new FakeBlocks().containerAt(near);

        StockScan.Result result = StockScan.containers(List.of(CENTER, new BlockCoord(0, 64, 4)), 8, 4, 4096, blocks);

        assertEquals(
                1,
                result.containers().stream().filter(pos -> pos.equals(near)).count(),
                "Сундук в пересечении двух центров читается один раз: иначе один и тот же "
                        + "склад вычитался бы несколько раз за пересборку");
    }

    @Test
    void containers_RepeatedScan_OrderStaysDeterministic() {
        FakeBlocks blocks = new FakeBlocks()
                .containerAt(new BlockCoord(3, 64, 3))
                .containerAt(new BlockCoord(-3, 64, -3))
                .containerAt(new BlockCoord(0, 64, 0));
        List<BlockCoord> centers = new ArrayList<>(List.of(CENTER));

        StockScan.Result first = StockScan.containers(centers, 8, 4, 4096, blocks);
        StockScan.Result second = StockScan.containers(centers, 8, 4, 4096, blocks);

        assertEquals(
                first,
                second,
                "Порядок найденных контейнеров детерминирован: иначе бюджет читал бы разные "
                        + "сундуки на одинаковом входе");
    }
}
