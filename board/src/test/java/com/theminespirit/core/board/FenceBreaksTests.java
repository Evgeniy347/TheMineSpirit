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
                "Причина сломанного забора — ключ живой причины доски: формат fence:x:y:z держит удержание билета"
                        + " Иначе тест cause_BlockCoord_IsStableFenceKey перестанет ловить эту поломку. При смене cause_BlockCoord_IsStableFenceKey проверь это утверждение.");
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
                "Починенная клетка выпадает из памяти: иначе граф вечно ставит ремонт уже целого забора"
                        + " При смене retainLive_RepairedCells_AreForgotten проверь это утверждение.");
        assertEquals(
                1,
                breaks.size(),
                "После чистки в памяти остаётся только реально сломанная клетка"
                        + " Иначе тест retainLive_RepairedCells_AreForgotten перестанет ловить эту поломку. При смене retainLive_RepairedCells_AreForgotten проверь это утверждение.");
    }

    @Test
    void retainLive_Budget_ChecksOnlySliceAndKeepsKnownLive() {
        FenceBreaks breaks = new FenceBreaks(3);
        BlockCoord first = new BlockCoord(1, 64, 1);
        BlockCoord second = new BlockCoord(2, 64, 2);
        BlockCoord third = new BlockCoord(3, 64, 3);
        breaks.remember(first, "oak_fence");
        breaks.remember(second, "oak_fence");
        breaks.remember(third, "oak_fence");
        int[] checks = {0};

        Map<BlockCoord, String> one = breaks.retainLive(
                at -> {
                    checks[0]++;
                    return true;
                },
                1);
        Map<BlockCoord, String> two = breaks.retainLive(
                at -> {
                    checks[0]++;
                    return true;
                },
                1);
        Map<BlockCoord, String> three = breaks.retainLive(
                at -> {
                    checks[0]++;
                    return true;
                },
                1);

        assertEquals(
                Map.of(first, "oak_fence"),
                one,
                "Первый пакет проверяет одну клетку и сразу сохраняет её живую причину: иначе до конца скана "
                        + "ремонт забора может исчезнуть с доски. При смене retainLive_Budget_ChecksOnlySliceAndKeepsKnownLive "
                        + "проверь FenceWatch.liveBreaks и BoardSources.tick.");
        assertEquals(
                Map.of(first, "oak_fence", second, "oak_fence"),
                two,
                "Второй пакет добавляет следующую живую клетку, не теряя уже найденную: иначе причина ремонта "
                        + "мигает между пересборками. При смене retainLive_Budget_ChecksOnlySliceAndKeepsKnownLive "
                        + "проверь FenceBreaks.live.");
        assertEquals(
                Map.of(first, "oak_fence", second, "oak_fence", third, "oak_fence"),
                three,
                "После трёх пакетов в живом наборе все три клетки: иначе память потеряет часть сломанных "
                        + "клеток и ремонт до них не дойдёт; бюджет ограничивает один тик, а не весь скан. "
                        + "При смене retainLive_Budget_ChecksOnlySliceAndKeepsKnownLive проверь FenceBreaks.retainLive.");
        assertEquals(
                3,
                checks[0],
                "За три вызова проверены ровно три адреса: иначе пакетный скан снова выполняет полный обход "
                        + "и возвращает холодный фриз. При смене retainLive_Budget_ChecksOnlySliceAndKeepsKnownLive "
                        + "проверь бюджет FenceWatch.SCAN_BUDGET.");
    }

    @Test
    void retainLive_CompletedScan_RechecksRepairedCell() {
        FenceBreaks breaks = new FenceBreaks(1);
        BlockCoord broken = new BlockCoord(1, 64, 1);
        breaks.remember(broken, "oak_fence");

        assertEquals(
                Map.of(broken, "oak_fence"),
                breaks.retainLive(at -> true, 1),
                "Первый пакет запоминает сломанный забор: иначе причина ремонта не попадёт на доску. "
                        + "При смене retainLive_CompletedScan_RechecksRepairedCell проверь FenceBreaks.live.");
        assertEquals(
                Map.of(),
                breaks.retainLive(at -> false, 1),
                "После завершения круга клетка проверяется заново и починенный забор удаляется: иначе "
                        + "память навсегда останется с уже целым блоком. При смене "
                        + "retainLive_CompletedScan_RechecksRepairedCell проверь scanCursor и FenceWatch.liveBreaks.");
        assertEquals(
                0,
                breaks.size(),
                "Починенная клетка не остаётся в памяти после повторного скана: иначе граф будет бесконечно "
                        + "ставить тот же ремонт. При смене retainLive_CompletedScan_RechecksRepairedCell проверь "
                        + "FenceBreaks.retainLive.");
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
                "Память забора хранит последние клетки: переполнение вытесняет самую старую, а не ломает ремонт"
                        + " При смене remember_OverCapacity_DropsOldestCell проверь это утверждение.");
    }

    @Test
    void remember_InvalidArguments_AreRejected() {
        FenceBreaks breaks = new FenceBreaks(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> breaks.remember(new BlockCoord(0, 64, 0), " "),
                "Пустой блок забора нечем ставить обратно: память обязана отказать, а не молча ждать"
                        + " Иначе тест remember_InvalidArguments_AreRejected перестанет ловить эту поломку. При смене remember_InvalidArguments_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new FenceBreaks(0),
                "Память забора без ёмкости не проверяет живые клетки: конструктор обязан отказать"
                        + " Иначе тест remember_InvalidArguments_AreRejected перестанет ловить эту поломку. При смене remember_InvalidArguments_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> breaks.retainLive(at -> true, 0),
                "Нулевой бюджет не продвигает скан и снова оставляет полный обход следующему тику: иначе "
                        + "холодный фриз возвращается, поэтому память обязана отказать. При смене "
                        + "remember_InvalidArguments_AreRejected проверь "
                        + "FenceBreaks.retainLive и FenceWatch.SCAN_BUDGET.");
    }
}
