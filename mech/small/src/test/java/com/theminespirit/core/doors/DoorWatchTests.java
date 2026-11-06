package com.theminespirit.core.doors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.doors.DoorTuning.DoorRules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoorWatchTests {
    private static final long NOW = 10_000;
    private static final BlockCoord DOOR = new BlockCoord(10, 64, 10);
    private static final BlockCoord PLAYER_IN_OPENING = new BlockCoord(10, 64, 10);
    private static final BlockCoord PLAYER_NEARBY = new BlockCoord(12, 64, 10);
    private static final BlockCoord PLAYER_FAR = new BlockCoord(20, 64, 10);
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final DoorWatch watch = new DoorWatch(new DoorRules(0.5, 60, 2.0, 32, 100));

    @Test
    void door_OpenBeyondLimit_ClosesByNearestSpirit() {
        DoorState door = new DoorState(DOOR, true, NOW - 61, List.of());
        List<DoorClosing> closings = watch.decide(
                List.of(door),
                List.of(
                        new SpiritRef(SECOND, new BlockCoord(30, 64, 10)),
                        new SpiritRef(FIRST, new BlockCoord(12, 64, 10))),
                NOW,
                false);

        assertEquals(
                List.of(new DoorClosing(DOOR, FIRST)),
                closings,
                "Дверь, открытая дольше предела, обязана закрываться ближайшим свободным духом: иначе игрок, "
                        + "забывший дверь, оставляет поселение открытым ночью");
    }

    @Test
    void door_OpenWithinLimit_DoesNotClose() {
        DoorState door = new DoorState(DOOR, true, NOW - 59, List.of());

        assertTrue(
                watch.decide(List.of(door), List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))), NOW, false)
                        .isEmpty(),
                "Дверь, открытая недавно, днём не закрывается: иначе дух хватает игрока за руку при каждом входе");
    }

    @Test
    void door_PlayerNearby_DoesNotClose() {
        DoorState door = new DoorState(DOOR, true, NOW - 500, List.of(PLAYER_NEARBY));

        assertTrue(
                watch.decide(List.of(door), List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))), NOW, false)
                        .isEmpty(),
                "Пока игрок стоит рядом с дверью, дух не закрывает её: иначе дух запирает игрока в проёме");
    }

    @Test
    void door_PlayerInOpening_DoesNotClose() {
        DoorState door = new DoorState(DOOR, true, NOW - 500, List.of(PLAYER_IN_OPENING));

        assertTrue(
                watch.decide(List.of(door), List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))), NOW, false)
                        .isEmpty(),
                "Игрок в проёме — дверь не закрывается никогда: иначе дух зажимает игрока створкой");
    }

    @Test
    void evening_OpenDoor_ClosesImmediately() {
        DoorState door = new DoorState(DOOR, true, NOW - 1, List.of());

        assertEquals(
                List.of(new DoorClosing(DOOR, FIRST)),
                watch.decide(List.of(door), List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))), NOW, true),
                "Вечером открытая дверь закрывается сразу, без ожидания: ночь — жёсткая граница поселения");
    }

    @Test
    void evening_PlayerInOpening_DoesNotClose() {
        DoorState door = new DoorState(DOOR, true, NOW - 1, List.of(PLAYER_IN_OPENING));

        assertTrue(
                watch.decide(List.of(door), List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))), NOW, true)
                        .isEmpty(),
                "Даже вечером дверь с игроком в проёме не закрывается: игрок важнее границы поселения");
    }

    @Test
    void twoOpenDoors_TwoSpirits_EachDoorHasItsOwnSpirit() {
        BlockCoord second = new BlockCoord(20, 64, 20);
        List<DoorClosing> closings = watch.decide(
                List.of(
                        new DoorState(DOOR, true, NOW - 100, List.of()),
                        new DoorState(second, true, NOW - 100, List.of())),
                List.of(
                        new SpiritRef(FIRST, new BlockCoord(12, 64, 10)),
                        new SpiritRef(SECOND, new BlockCoord(22, 64, 20))),
                NOW,
                true);

        assertEquals(
                List.of(new DoorClosing(DOOR, FIRST), new DoorClosing(second, SECOND)),
                closings,
                "Каждый дух идёт к своей ближайшей двери: перекрёстные назначения заставляют духов толкаться у одной двери");
    }

    @Test
    void oneSpirit_TwoOpenDoors_OnlyOneDoorAssigned() {
        BlockCoord second = new BlockCoord(20, 64, 20);

        assertEquals(
                1,
                watch.decide(
                                List.of(
                                        new DoorState(DOOR, true, NOW - 100, List.of()),
                                        new DoorState(second, true, NOW - 100, List.of())),
                                List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))),
                                NOW,
                                true)
                        .size(),
                "Один дух не закрывает две двери сразу: иначе вторая дверь ждёт, а дух стоит между ними");
    }

    @Test
    void door_Closed_DoesNotClose() {
        DoorState door = new DoorState(DOOR, false, NOW - 500, List.of());

        assertTrue(
                watch.decide(List.of(door), List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))), NOW, true)
                        .isEmpty(),
                "Закрытая дверь не назначается: иначе дух бежит к уже закрытой двери вместо открытой");
    }

    @Test
    void door_PlayerBeyondRadius_Closes() {
        DoorState door = new DoorState(DOOR, true, NOW - 500, List.of(PLAYER_FAR));

        assertEquals(
                List.of(new DoorClosing(DOOR, FIRST)),
                watch.decide(List.of(door), List.of(new SpiritRef(FIRST, new BlockCoord(12, 64, 10))), NOW, false),
                "Игрок дальше радиуса не мешает: иначе дух не закроет дверь, пока игрок смотрит издалека");
    }

    @Test
    void isEvening_DayFractionAtThreshold_IsEvening() {
        assertTrue(
                watch.isEvening(0.5),
                "Доля суток на пороге — уже вечер: закат обязан закрывать двери, а не ждать следующего тика");
    }

    @Test
    void isEvening_DayFractionBelowThreshold_IsDay() {
        assertFalse(watch.isEvening(0.49), "День до порога — не вечер: иначе духи закрывают двери задолго до заката");
    }

    @Test
    void inSettlement_DoorWithinRadius_IsInSettlement() {
        assertTrue(
                watch.inSettlement(DOOR, List.of(new BlockCoord(10, 64, 40))),
                "Дверь в радиусе от дома — дверь поселения: иначе дух не закроет дверь у своего дома");
    }

    @Test
    void inSettlement_DoorBeyondRadius_IsOutside() {
        assertFalse(
                watch.inSettlement(DOOR, List.of(new BlockCoord(10, 64, 43))),
                "дверь дальше радиуса от домов — не дверь поселения: иначе дух бежит закрывать чужие двери");
    }

    @Test
    void inSettlement_DoorExactlyAtRadius_IsInSettlement() {
        assertTrue(
                watch.inSettlement(DOOR, List.of(new BlockCoord(10, 64, 42))),
                "дверь ровно на границе радиуса — дверь поселения: иначе дух не закроет дверь на краю своих владений");
    }
}
