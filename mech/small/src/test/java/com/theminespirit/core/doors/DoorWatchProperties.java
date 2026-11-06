package com.theminespirit.core.doors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.doors.DoorTuning.DoorRules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class DoorWatchProperties {
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID THIRD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final List<SpiritRef> NONE = List.of();
    private static final List<SpiritRef> ONE = List.of(new SpiritRef(FIRST, new BlockCoord(64, 64, 70)));
    private static final List<SpiritRef> TWO = List.of(
            new SpiritRef(FIRST, new BlockCoord(64, 64, 70)), new SpiritRef(SECOND, new BlockCoord(64, 64, 58)));
    private static final List<SpiritRef> THREE = List.of(
            new SpiritRef(FIRST, new BlockCoord(64, 64, 70)),
            new SpiritRef(SECOND, new BlockCoord(64, 64, 58)),
            new SpiritRef(THIRD, new BlockCoord(70, 64, 64)));

    private final DoorWatch watch = new DoorWatch(new DoorRules(0.5, 60, 2.0, 32, 100));

    @Property
    void closing_PlayerInOpening_NeverCloses(
            @ForAll @IntRange(min = -16, max = 16) int dx,
            @ForAll @IntRange(min = -16, max = 16) int dz,
            @ForAll @IntRange(min = 0, max = 100) long openedAgo,
            @ForAll("spiritSets") List<SpiritRef> spirits) {
        BlockCoord door = new BlockCoord(64, 64, 64);
        List<BlockCoord> players = new ArrayList<>();
        players.add(new BlockCoord(64 + dx, 64, 64 + dz));
        DoorState open = new DoorState(door, true, 10_000L - openedAgo, players);

        List<DoorClosing> closings = watch.decide(List.of(open), spirits, 10_000L, false);

        if (Math.sqrt(dx * dx + dz * dz) > 2.0) {
            return;
        }
        assertFalse(
                closings.stream().anyMatch(closing -> closing.door().equals(door)),
                "Дух закрыл дверь, в проёме которой стоит игрок: иначе игрок оказывается зажат створкой");
    }

    @Property
    void closing_OpenDoor_AtMostOneSpirit(
            @ForAll @Size(min = 1, max = 12) List<@IntRange(min = -24, max = 24) Integer> flat,
            @ForAll("spiritSets") List<SpiritRef> spirits) {
        long now = 20_000L;
        List<DoorState> doors = new ArrayList<>();
        Set<BlockCoord> seen = new HashSet<>();
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            BlockCoord position = new BlockCoord(64 + flat.get(i), 64, 64 + flat.get(i + 1));
            if (seen.add(position)) {
                doors.add(new DoorState(position, true, now - 100, List.of()));
            }
        }

        List<DoorClosing> closings = watch.decide(doors, spirits, now, true);

        Set<BlockCoord> uniqueDoors = new HashSet<>();
        for (DoorClosing closing : closings) {
            assertTrue(
                    uniqueDoors.add(closing.door()),
                    "Одна дверь получила двух духов: иначе духи толкаются у одной двери вместо работы");
        }
    }

    @Property
    void closing_OneSpirit_AtMostOneDoor(@ForAll("spiritSets") List<SpiritRef> spirits) {
        long now = 20_000L;
        List<DoorState> doors = List.of(
                new DoorState(new BlockCoord(64, 64, 64), true, now - 100, List.of()),
                new DoorState(new BlockCoord(70, 64, 70), true, now - 100, List.of()),
                new DoorState(new BlockCoord(58, 64, 58), true, now - 100, List.of()));

        List<DoorClosing> closings = watch.decide(doors, spirits, now, true);

        Set<UUID> uniqueSpirits = new HashSet<>();
        for (DoorClosing closing : closings) {
            assertTrue(
                    uniqueSpirits.add(closing.spirit()),
                    "Один дух получил две двери: иначе вторая дверь ждёт, а дух стоит между двумя");
        }
    }

    @Property
    void closing_Evening_AllOpenDoorsWithoutPlayersGetSpirit(
            @ForAll @Size(min = 0, max = 8) List<@IntRange(min = -24, max = 24) Integer> flat,
            @ForAll("spiritSets") List<SpiritRef> spirits) {
        long now = 20_000L;
        List<DoorState> doors = new ArrayList<>();
        Set<BlockCoord> seen = new HashSet<>();
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            BlockCoord position = new BlockCoord(64 + flat.get(i), 64, 64 + flat.get(i + 1));
            if (seen.add(position)) {
                doors.add(new DoorState(position, true, now - 1, List.of()));
            }
        }

        List<DoorClosing> closings = watch.decide(doors, spirits, now, true);

        assertEquals(
                Math.min(doors.size(), spirits.size()),
                closings.size(),
                "Вечером каждая открытая дверь без игроков обязана получить духа: иначе ночью остаётся открытой дверь");
    }

    @Property
    void closing_DoorOpenedRecently_DoesNotClose(
            @ForAll("spiritSets") List<SpiritRef> spirits, @ForAll @IntRange(min = 1, max = 59) long openedAgo) {
        long now = 20_000L;
        DoorState door = new DoorState(new BlockCoord(64, 64, 64), true, now - openedAgo, List.of());

        if (openedAgo >= 60) {
            return;
        }
        assertTrue(
                watch.decide(List.of(door), spirits, now, false).isEmpty(),
                "Днём дверь, открытая меньше предела тиков, не закрывается: иначе дух хватает игрока за руку");
    }

    @Provide
    Arbitrary<List<SpiritRef>> spiritSets() {
        return Arbitraries.of(NONE, ONE, TWO, THREE);
    }
}
