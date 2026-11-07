package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.town.TownSnapshot;
import org.junit.jupiter.api.Test;

class BuildProgressSnapshotsTests {
    @Test
    void remember_BetweenTasks_KeepsLastBuiltPercent() {
        BuildProgressSnapshots snapshots = new BuildProgressSnapshots();
        snapshots.remember("wheat_field", new TownSnapshot.Progress(7, 10, false));

        assertEquals(
                new TownSnapshot.Progress(7, 10, false),
                snapshots.last("wheat_field").orElseThrow(),
                "Снимок прогресса живёт между заданиями: без него [plot] грядки/постройки пропадает, пока билета нет");
    }

    @Test
    void remember_UnknownProgress_DoesNotEraseLastSnapshot() {
        BuildProgressSnapshots snapshots = new BuildProgressSnapshots();
        snapshots.remember("mine", new TownSnapshot.Progress(40, 100, false));
        snapshots.remember("mine", TownSnapshot.Progress.NONE);

        assertEquals(
                40,
                snapshots.last("mine").orElseThrow().done(),
                "Пустой замер (билета нет, схема не прочитана) не стирает последний честный процент: "
                        + "иначе [plot] мигает 0% между заданиями");
    }

    @Test
    void last_UnknownBuilding_IsEmpty() {
        assertTrue(new BuildProgressSnapshots().last("streets").isEmpty(), "Незнакомая постройка — пусто, а не 0%");
    }
}
