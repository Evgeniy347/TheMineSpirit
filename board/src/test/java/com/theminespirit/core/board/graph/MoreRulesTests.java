package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MoreRulesTests {
    private static final MoreRules.Player AWAY = new MoreRules.Player(false, false, false);
    private static final MoreRules.Clock DAY = new MoreRules.Clock(false, false);

    @Test
    void quietSettlement_NoInputs_RaisesNothing() {
        assertTrue(
                MoreRules.signals(base()).isEmpty(),
                "Тихий поселок не должен поднимать узлы more"
                        + " Иначе тест quietSettlement_NoInputs_RaisesNothing перестанет ловить эту поломку. При смене quietSettlement_NoInputs_RaisesNothing проверь это утверждение.");
    }

    @Test
    void playerNear_EmptyHand_HelpOnly() {
        Set<String> signals = MoreRules.signals(withPlayer(new MoreRules.Player(true, false, false)));

        assertTrue(
                signals.contains(MoreRules.PLAYER_NEAR),
                "Рядом игрок — помощь доступна"
                        + " Иначе тест playerNear_EmptyHand_HelpOnly перестанет ловить эту поломку. При смене playerNear_EmptyHand_HelpOnly проверь это утверждение.");
        assertFalse(
                signals.contains(MoreRules.PLAYER_BUILDING),
                "Пустая рука не повод уходить с дороги"
                        + " Иначе тест playerNear_EmptyHand_HelpOnly перестанет ловить эту поломку. При смене playerNear_EmptyHand_HelpOnly проверь это утверждение.");
    }

    @Test
    void playerNearHoldingBlock_BlockInHand_HelpAndKeepClear() {
        Set<String> signals = MoreRules.signals(withPlayer(new MoreRules.Player(true, true, false)));

        assertTrue(
                signals.contains(MoreRules.PLAYER_NEAR),
                "Близость игрока остаётся входом"
                        + " Иначе тест playerNearHoldingBlock_BlockInHand_HelpAndKeepClear перестанет ловить эту поломку. При смене playerNearHoldingBlock_BlockInHand_HelpAndKeepClear проверь это утверждение.");
        assertTrue(
                signals.contains(MoreRules.PLAYER_BUILDING),
                "Игрок с блоком в руке — не мешать стройке"
                        + " Иначе тест playerNearHoldingBlock_BlockInHand_HelpAndKeepClear перестанет ловить эту поломку. При смене playerNearHoldingBlock_BlockInHand_HelpAndKeepClear проверь это утверждение.");
    }

    @Test
    void playerNearUsingItem_ItemInUse_KeepClear() {
        Set<String> signals = MoreRules.signals(withPlayer(new MoreRules.Player(true, false, true)));

        assertTrue(
                signals.contains(MoreRules.PLAYER_BUILDING),
                "Игрок занят предметом — дух не толпится рядом"
                        + " Иначе тест playerNearUsingItem_ItemInUse_KeepClear перестанет ловить эту поломку. При смене playerNearUsingItem_ItemInUse_KeepClear проверь это утверждение.");
    }

    @Test
    void playerNearDigging_NearPlayer_KeepClearForWork() {
        Set<String> signals = MoreRules.signals(withPlayer(new MoreRules.Player(true, false, false, true)));

        assertTrue(
                signals.contains(MoreRules.PLAYER_NEAR),
                "Копающий рядом игрок — близость"
                        + " Иначе тест playerNearDigging_NearPlayer_KeepClearForWork перестанет ловить эту поломку. При смене playerNearDigging_NearPlayer_KeepClearForWork проверь это утверждение.");
        assertTrue(
                signals.contains(MoreRules.PLAYER_BUILDING),
                "Копающий игрок — дух не толпится в его клетке"
                        + " Иначе тест playerNearDigging_NearPlayer_KeepClearForWork перестанет ловить эту поломку. При смене playerNearDigging_NearPlayer_KeepClearForWork проверь это утверждение.");
    }

    @Test
    void playerFarHoldingBlock_DistantPlayer_NoSignals() {
        Set<String> signals = MoreRules.signals(withPlayer(new MoreRules.Player(false, true, true)));

        assertFalse(
                signals.contains(MoreRules.PLAYER_NEAR),
                "Далёкий игрок не считается близким"
                        + " Иначе тест playerFarHoldingBlock_DistantPlayer_NoSignals перестанет ловить эту поломку. При смене playerFarHoldingBlock_DistantPlayer_NoSignals проверь это утверждение.");
        assertFalse(
                signals.contains(MoreRules.PLAYER_BUILDING),
                "Далёкая стройка не мешает духу"
                        + " Иначе тест playerFarHoldingBlock_DistantPlayer_NoSignals перестанет ловить эту поломку. При смене playerFarHoldingBlock_DistantPlayer_NoSignals проверь это утверждение.");
    }

    @Test
    void darkCells_DarkCellsInside_AddsSignalAndCount() {
        Set<String> signals = MoreRules.signals(new MoreRules.Signals(
                AWAY, 3, DAY, 0, 0, new MoreRules.Drops(0, 0), okResources(), quietSecurity(), quietReserves(), 0));

        assertTrue(
                signals.contains(MoreRules.DARK_SPOTS),
                "Тёмные клетки внутри периметра — вход света"
                        + " Иначе тест darkCells_DarkCellsInside_AddsSignalAndCount перестанет ловить эту поломку. При смене darkCells_DarkCellsInside_AddsSignalAndCount проверь это утверждение.");
        assertTrue(
                signals.contains("more:dark:3"),
                "Число тёмных клеток передаётся дальше"
                        + " Иначе тест darkCells_DarkCellsInside_AddsSignalAndCount перестанет ловить эту поломку. При смене darkCells_DarkCellsInside_AddsSignalAndCount проверь это утверждение.");
    }

    @Test
    void duskSoon_BeforeSunset_ReturnHomeAndAvoidFarTrip() {
        Set<String> signals = MoreRules.signals(new MoreRules.Signals(
                AWAY,
                0,
                new MoreRules.Clock(true, false),
                0,
                0,
                new MoreRules.Drops(0, 0),
                okResources(),
                quietSecurity(),
                quietReserves(),
                0));

        assertTrue(
                signals.contains(MoreRules.DUSK_SOON),
                "До заката близко — вход домой"
                        + " Иначе тест duskSoon_BeforeSunset_ReturnHomeAndAvoidFarTrip перестанет ловить эту поломку. При смене duskSoon_BeforeSunset_ReturnHomeAndAvoidFarTrip проверь это утверждение.");
        assertTrue(
                signals.contains(MoreRules.BAD_TRIP_CONDITIONS),
                "Перед ночью дальний поход не начинают"
                        + " Иначе тест duskSoon_BeforeSunset_ReturnHomeAndAvoidFarTrip перестанет ловить эту поломку. При смене duskSoon_BeforeSunset_ReturnHomeAndAvoidFarTrip проверь это утверждение.");
    }

    @Test
    void rain_NotDusk_AvoidFarTripOnly() {
        Set<String> signals = MoreRules.signals(new MoreRules.Signals(
                AWAY,
                0,
                new MoreRules.Clock(false, true),
                0,
                0,
                new MoreRules.Drops(0, 0),
                okResources(),
                quietSecurity(),
                quietReserves(),
                0));

        assertFalse(
                signals.contains(MoreRules.DUSK_SOON),
                "Дождь сам по себе не закат"
                        + " Иначе тест rain_NotDusk_AvoidFarTripOnly перестанет ловить эту поломку. При смене rain_NotDusk_AvoidFarTripOnly проверь это утверждение.");
        assertTrue(
                signals.contains(MoreRules.BAD_TRIP_CONDITIONS),
                "В дождь дальний поход не начинают"
                        + " Иначе тест rain_NotDusk_AvoidFarTripOnly перестанет ловить эту поломку. При смене rain_NotDusk_AvoidFarTripOnly проверь это утверждение.");
    }

    @Test
    void failures_OneRepeat_NoSignal() {
        assertFalse(
                MoreRules.signals(withRepeats(1)).contains(MoreRules.FAILURES_REPEAT),
                "Один повторный провал — ещё не история"
                        + " Иначе тест failures_OneRepeat_NoSignal перестанет ловить эту поломку. При смене failures_OneRepeat_NoSignal проверь это утверждение.");
    }

    @Test
    void failures_TwoRepeats_SignalWithCount() {
        Set<String> signals = MoreRules.signals(withRepeats(2));

        assertTrue(
                signals.contains(MoreRules.FAILURES_REPEAT),
                "Два повторных провала — снижать вес"
                        + " Иначе тест failures_TwoRepeats_SignalWithCount перестанет ловить эту поломку. При смене failures_TwoRepeats_SignalWithCount проверь это утверждение.");
        assertTrue(
                signals.contains("more:failures:2"),
                "Число повторов передаётся дальше"
                        + " Иначе тест failures_TwoRepeats_SignalWithCount перестанет ловить эту поломку. При смене failures_TwoRepeats_SignalWithCount проверь это утверждение.");
    }

    @Test
    void stationQueue_OneWaiter_NoSignal() {
        assertFalse(
                MoreRules.signals(withWaiters(1)).contains(MoreRules.STATION_QUEUE),
                "Один ждущий — не очередь"
                        + " Иначе тест stationQueue_OneWaiter_NoSignal перестанет ловить эту поломку. При смене stationQueue_OneWaiter_NoSignal проверь это утверждение.");
    }

    @Test
    void stationQueue_TwoWaiters_SignalWithCount() {
        Set<String> signals = MoreRules.signals(withWaiters(2));

        assertTrue(
                signals.contains(MoreRules.STATION_QUEUE),
                "Двое ждут печь или верстак — нужна вторая станция"
                        + " Иначе тест stationQueue_TwoWaiters_SignalWithCount перестанет ловить эту поломку. При смене stationQueue_TwoWaiters_SignalWithCount проверь это утверждение.");
        assertTrue(
                signals.contains("more:queue:2"),
                "Длина очереди передаётся дальше"
                        + " Иначе тест stationQueue_TwoWaiters_SignalWithCount перестанет ловить эту поломку. При смене stationQueue_TwoWaiters_SignalWithCount проверь это утверждение.");
    }

    @Test
    void drops_NotExpiring_NoSignal() {
        Set<String> signals = MoreRules.signals(withDrops(new MoreRules.Drops(3, 0)));

        assertFalse(
                signals.contains(MoreRules.DROPS_EXPIRING),
                "Свежий дроп не горит сроком"
                        + " Иначе тест drops_NotExpiring_NoSignal перестанет ловить эту поломку. При смене drops_NotExpiring_NoSignal проверь это утверждение.");
    }

    @Test
    void drops_Expiring_SignalWithCount() {
        Set<String> signals = MoreRules.signals(withDrops(new MoreRules.Drops(3, 1)));

        assertTrue(
                signals.contains(MoreRules.DROPS_EXPIRING),
                "Дроп скоро исчезнет — подобрать до деспавна"
                        + " Иначе тест drops_Expiring_SignalWithCount перестанет ловить эту поломку. При смене drops_Expiring_SignalWithCount проверь это утверждение.");
        assertTrue(
                signals.contains("more:drops:1"),
                "Число исчезающих дропов передаётся дальше"
                        + " Иначе тест drops_Expiring_SignalWithCount перестанет ловить эту поломку. При смене drops_Expiring_SignalWithCount проверь это утверждение.");
    }

    @Test
    void negativeCounts_BelowZero_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MoreRules.Drops(1, 2),
                "Исчезающих дропов не больше всех"
                        + " Иначе тест negativeCounts_BelowZero_Rejected перестанет ловить эту поломку. При смене negativeCounts_BelowZero_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MoreRules.Security(-1, 0, 0, false),
                "Отрицательный пролом невозможен"
                        + " Иначе тест negativeCounts_BelowZero_Rejected перестанет ловить эту поломку. При смене negativeCounts_BelowZero_Rejected проверь это утверждение.");
    }

    private static MoreRules.Signals base() {
        return new MoreRules.Signals(
                AWAY, 0, DAY, 0, 0, new MoreRules.Drops(0, 0), okResources(), quietSecurity(), quietReserves(), 0);
    }

    private static MoreRules.Signals withPlayer(MoreRules.Player player) {
        return new MoreRules.Signals(
                player, 0, DAY, 0, 0, new MoreRules.Drops(0, 0), okResources(), quietSecurity(), quietReserves(), 0);
    }

    private static MoreRules.Signals withRepeats(int repeats) {
        return new MoreRules.Signals(
                AWAY,
                0,
                DAY,
                repeats,
                0,
                new MoreRules.Drops(0, 0),
                okResources(),
                quietSecurity(),
                quietReserves(),
                0);
    }

    private static MoreRules.Signals withWaiters(int waiters) {
        return new MoreRules.Signals(
                AWAY,
                0,
                DAY,
                0,
                waiters,
                new MoreRules.Drops(0, 0),
                okResources(),
                quietSecurity(),
                quietReserves(),
                0);
    }

    private static MoreRules.Signals withDrops(MoreRules.Drops drops) {
        return new MoreRules.Signals(AWAY, 0, DAY, 0, 0, drops, okResources(), quietSecurity(), quietReserves(), 0);
    }

    private static MoreRules.Resources okResources() {
        return new MoreRules.Resources(10, 0, 0, 10, false);
    }

    private static MoreRules.Security quietSecurity() {
        return new MoreRules.Security(0, 0, 0, false);
    }

    private static MoreRules.Reserves quietReserves() {
        return new MoreRules.Reserves(0, 0, 0);
    }
}
