package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContainerOpenHoldTests {
    private static final BlockCoord CHEST = new BlockCoord(10, 64, 10);
    private static final BlockCoord BARREL = new BlockCoord(5, 64, 3);
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final ContainerOpenHold hold = new ContainerOpenHold(10);

    @Test
    void touch_FirstTime_ReportsOpening() {
        assertTrue(
                hold.touch(CHEST, FIRST, 100),
                "Первое касание открывает крышку: иначе дух работает с закрытым сундуком");
        assertEquals(1, hold.openers(CHEST), "Один дух у контейнера: счётчик открывших обязан быть равен единице");
    }

    @Test
    void touch_SecondSpirit_DoesNotReportNewOpening() {
        hold.touch(CHEST, FIRST, 100);

        assertFalse(
                hold.touch(CHEST, SECOND, 104),
                "Второй дух не открывает крышку заново: повторный звук открытия выдал бы обмен дважды");
        assertEquals(2, hold.openers(CHEST), "Два духа у контейнера: оба обязаны попасть в счётчик открывших");
    }

    @Test
    void tick_WithinHold_KeepsOpen() {
        hold.touch(CHEST, FIRST, 100);

        assertTrue(
                hold.tick(109).isEmpty(), "До истечения ожидания крышка не закрывается: обмен ещё может продолжиться");
        assertEquals(1, hold.openers(CHEST), "Внутри окна удержания контейнер обязан оставаться открытым");
    }

    @Test
    void tick_AtHoldLimit_Closes() {
        hold.touch(CHEST, FIRST, 100);

        assertEquals(
                List.of(CHEST),
                hold.tick(110),
                "Ровно через десять тиков после последнего обмена контейнер закрывается: иначе дух оставит крышку открытой");
        assertEquals(0, hold.openers(CHEST), "Закрытый контейнер обязан покинуть список удерживаемых");
    }

    @Test
    void touch_NewExchange_RefreshesHold() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(CHEST, FIRST, 109);

        assertTrue(
                hold.tick(110).isEmpty(), "Новый обмен продлевает удержание: крышка не захлопывается посреди работы");
        assertEquals(
                List.of(CHEST), hold.tick(119), "Отсчёт идёт от последнего обмена, а не от первого открытия крышки");
    }

    @Test
    void tick_SecondSpiritTouched_ExtendsHold() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(CHEST, SECOND, 107);

        assertTrue(hold.tick(115).isEmpty(), "Пока второй дух обменивается, крышка обязана оставаться открытой");
        assertEquals(
                List.of(CHEST),
                hold.tick(117),
                "Закрытие считается от последнего обмена любого духа: иначе второй дух останется у закрытого сундука");
    }

    @Test
    void tick_MixedAges_ClosesOnlyExpiredPositions() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(BARREL, SECOND, 105);

        assertEquals(List.of(CHEST), hold.tick(110), "Истёкший контейнер закрывается, свежий обязан остаться открытым");
        assertTrue(hold.openers(BARREL) > 0, "У свежего контейнера ожидание ещё не истекло");
        assertEquals(List.of(BARREL), hold.tick(115), "Второй контейнер закрывается по своему таймеру");
    }

    @Test
    void touch_AfterClose_ReportsOpeningAgain() {
        hold.touch(CHEST, FIRST, 100);
        hold.tick(110);

        assertTrue(
                hold.touch(CHEST, FIRST, 111),
                "Новый обмен после закрытия обязан снова открыть крышку и дать звук открытия");
    }

    @Test
    void openers_RepeatedTouchBySameSpirit_CountsOnce() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(CHEST, FIRST, 101);
        hold.touch(CHEST, SECOND, 102);

        assertEquals(
                2,
                hold.openers(CHEST),
                "Повторные касания одного духа не раздувают счётчик открывших: иначе число в событии блока соврёт");
    }

    @Test
    void constructor_NonPositiveHold_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContainerOpenHold(0),
                "Нулевое ожидание закрытия запрещено: крышка закрывалась бы раньше, чем кончается обмен");
    }

    @Test
    void touch_NegativeTick_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold.touch(CHEST, FIRST, -1),
                "Отрицательное время запрещено: таймер удержания посчитал бы прошлое");
    }
}
