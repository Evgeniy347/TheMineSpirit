package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FenceBreaksTests {
    @Test
    void cause_BlockCoord_IsStableFenceKey() {
        assertEquals(
                "fence:10:-64:3",
                FenceBreaks.cause(new BlockCoord(10, -64, 3)),
                "Причина сломанного забора — ключ живой причины доски: формат fence:x:y:z держит удержание билета");
    }

    @Test
    void retainLive_RepairedCells_AreForgotten() {
        FenceBreaks breaks = new FenceBreaks(4);
        BlockCoord broken = new BlockCoord(1, 64, 1);
        BlockCoord repaired = new BlockCoord(2, 64, 2);
        breaks.remember(broken, "oak_fence");
        breaks.remember(repaired, "oak_fence");

        Map<BlockCoord, String> live = breaks.retainLive(at -> !at.equals(repaired));

        assertEquals(
                Map.of(broken, "oak_fence"),
                live,
                "Починенная клетка выпадает из памяти: иначе граф вечно ставит ремонт уже целого забора");
        assertEquals(1, breaks.size(), "После чистки в памяти остаётся только реально сломанная клетка");
    }

    @Test
    void remember_OverCapacity_DropsOldestCell() {
        FenceBreaks breaks = new FenceBreaks(2);
        breaks.remember(new BlockCoord(0, 64, 0), "oak_fence");
        breaks.remember(new BlockCoord(1, 64, 0), "oak_fence");
        breaks.remember(new BlockCoord(2, 64, 0), "oak_fence");

        assertEquals(
                Map.of(new BlockCoord(1, 64, 0), "oak_fence", new BlockCoord(2, 64, 0), "oak_fence"),
                breaks.current(),
                "Память забора хранит последние клетки: переполнение вытесняет самую старую, а не ломает ремонт");
    }

    @Test
    void remember_InvalidArguments_AreRejected() {
        FenceBreaks breaks = new FenceBreaks(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> breaks.remember(new BlockCoord(0, 64, 0), " "),
                "Пустой блок забора нечем ставить обратно: память обязана отказать, а не молча ждать");
        assertThrows(
                IllegalArgumentException.class,
                () -> new FenceBreaks(0),
                "Память забора без ёмкости не проверяет живые клетки: конструктор обязан отказать");
    }
}
