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
                "Первое касание открывает крышку: иначе дух работает с закрытым сундуком и звук открытия "
                        + "не прозвучит. При смене touch проверь первое касание");
        assertEquals(
                1,
                hold.openers(CHEST),
                "Один дух у контейнера: счётчик открывших обязан быть равен единице, иначе событие блока "
                        + "покажет неверное число. При смене touch проверь openers");
    }

    @Test
    void touch_SecondSpirit_DoesNotReportNewOpening() {
        hold.touch(CHEST, FIRST, 100);

        assertFalse(
                hold.touch(CHEST, SECOND, 104),
                "Второй дух не открывает крышку заново: иначе звук открытия прозвучит дважды и выдаст "
                        + "обмен. При смене touch проверь второго духа");
        assertEquals(
                2,
                hold.openers(CHEST),
                "Два духа у контейнера: оба обязаны попасть в счётчик открывших, иначе событие блока "
                        + "соврёт о числе. При смене touch проверь openers двух духов");
    }

    @Test
    void tick_WithinHold_KeepsOpen() {
        hold.touch(CHEST, FIRST, 100);

        assertTrue(
                hold.tick(109).isEmpty(),
                "До истечения ожидания крышка не закрывается: обмен ещё может продолжиться, иначе "
                        + "дух останется у захлопнутого сундука. При смене tick проверь окно удержания");
        assertEquals(
                1,
                hold.openers(CHEST),
                "Внутри окна удержания контейнер обязан оставаться открытым, иначе обмен оборвётся "
                        + "на середине. При смене tick проверь openers внутри окна");
    }

    @Test
    void tick_AtHoldLimit_Closes() {
        hold.touch(CHEST, FIRST, 100);

        assertEquals(
                List.of(CHEST),
                hold.tick(110),
                "Ровно через десять тиков после последнего обмена контейнер закрывается: иначе дух "
                        + "оставит крышку открытой. При смене tick проверь границу удержания");
        assertEquals(
                0,
                hold.openers(CHEST),
                "Закрытый контейнер обязан покинуть список удерживаемых, иначе дух придёт к уже закрытому "
                        + "сундуку. При смене tick проверь openers после закрытия");
    }

    @Test
    void touch_NewExchange_RefreshesHold() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(CHEST, FIRST, 109);

        assertTrue(
                hold.tick(110).isEmpty(),
                "Новый обмен продлевает удержание: крышка не захлопывается посреди работы, иначе дух "
                        + "останется без доступа. При смене touch проверь продление");
        assertEquals(
                List.of(CHEST),
                hold.tick(119),
                "Отсчёт идёт от последнего обмена, а не от первого открытия крышки, иначе контейнер "
                        + "закроется раньше времени. При смене tick проверь отсчёт от последнего обмена");
    }

    @Test
    void tick_SecondSpiritTouched_ExtendsHold() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(CHEST, SECOND, 107);

        assertTrue(
                hold.tick(115).isEmpty(),
                "Пока второй дух обменивается, крышка обязана оставаться открытой, иначе обмен второго "
                        + "духа оборвётся. При смене touch проверь удержание вторым духом");
        assertEquals(
                List.of(CHEST),
                hold.tick(117),
                "Закрытие считается от последнего обмена любого духа: иначе второй дух останется у закрытого "
                        + "сундука. При смене tick проверь отсчёт от второго духа");
    }

    @Test
    void tick_MixedAges_ClosesOnlyExpiredPositions() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(BARREL, SECOND, 105);

        assertEquals(
                List.of(CHEST),
                hold.tick(110),
                "Истёкший контейнер закрывается, свежий обязан остаться открытым, иначе свежий дух "
                        + "останется у захлопнутого сундука. При смене tick проверь смешанные возрасты");
        assertTrue(
                hold.openers(BARREL) > 0,
                "У свежего контейнера ожидание ещё не истекло, иначе его закрыли бы раньше срока, "
                        + "пока обмен идёт. При смене tick проверь openers свежего");
        assertEquals(
                List.of(BARREL),
                hold.tick(115),
                "Второй контейнер закрывается по своему таймеру, иначе он останется открытым навсегда. "
                        + "При смене tick проверь второй контейнер");
    }

    @Test
    void touch_AfterClose_ReportsOpeningAgain() {
        hold.touch(CHEST, FIRST, 100);
        hold.tick(110);

        assertTrue(
                hold.touch(CHEST, FIRST, 111),
                "Новый обмен после закрытия обязан снова открыть крышку и дать звук открытия, иначе "
                        + "игрок не услышит визит духа. При смене touch проверь повторное открытие");
    }

    @Test
    void openers_RepeatedTouchBySameSpirit_CountsOnce() {
        hold.touch(CHEST, FIRST, 100);
        hold.touch(CHEST, FIRST, 101);
        hold.touch(CHEST, SECOND, 102);

        assertEquals(
                2,
                hold.openers(CHEST),
                "Повторные касания одного духа не раздувают счётчик открывших: иначе число в событии "
                        + "блока соврёт и звук сработает лишний раз. При смене openers проверь того же духа");
    }

    @Test
    void constructor_NonPositiveHold_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContainerOpenHold(0),
                "Нулевое ожидание закрытия запрещено: крышка закрывалась бы раньше, чем кончается обмен, "
                        + "иначе дух застрянет у сундука. При смене конструктора проверь ноль");
    }

    @Test
    void touch_NegativeTick_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold.touch(CHEST, FIRST, -1),
                "Отрицательное время запрещено: таймер удержания посчитал бы прошлое, иначе крышка "
                        + "не закроется вовсе. При смене touch проверь отрицательный тик");
    }
}
