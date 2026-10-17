package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CraterProfilesTests {
    private static BlockCoord at(int x, int y, int z) {
        return new BlockCoord(x, y, z);
    }

    @Test
    void retainLive_FilledCrater_RemovesCause() {
        CraterProfiles profiles = new CraterProfiles(8);
        profiles.remember("crater:1:64:1", List.of(at(1, 64, 1), at(1, 63, 1)));

        Set<String> live = profiles.retainLive(cell -> false);

        assertTrue(live.isEmpty(), "Засыпанная воронка уходит из живых причин: " + live);
        assertEquals(0, profiles.size(), "Профиль засыпанной воронки не копится в кольце");
    }

    @Test
    void retainLive_RemainingHole_KeepsOnlyThatCause() {
        CraterProfiles profiles = new CraterProfiles(8);
        profiles.remember("crater:1:64:1", List.of(at(1, 64, 1), at(1, 63, 1)));
        profiles.remember("crater:9:64:9", List.of(at(9, 64, 9)));
        Set<BlockCoord> holes = new HashSet<>(List.of(at(1, 63, 1)));

        Set<String> live = profiles.retainLive(holes::contains);

        assertEquals(Set.of("crater:1:64:1"), live,
                "Живая причина — только воронка с оставшейся пустой клеткой: " + live);
        assertEquals(Set.of("crater:1:64:1"), profiles.causes(),
                "Засыпанная воронка уходит из кольца, живая остаётся: " + profiles.causes());
    }

    @Test
    void remember_SameCauseTwice_ReplacesProfileWithoutDuplicate() {
        CraterProfiles profiles = new CraterProfiles(8);
        profiles.remember("crater:1:64:1", List.of(at(1, 64, 1)));
        profiles.remember("crater:1:64:1", List.of(at(2, 64, 2)));

        assertEquals(1, profiles.size(), "Повторный взрыв той же точки не плодит вторую причину");
        assertEquals(List.of(at(2, 64, 2)), profiles.profile("crater:1:64:1"),
                "Профиль той же воронки заменяется последним снимком");
    }

    @Test
    void remember_TooManyCraters_EvictsOldest() {
        CraterProfiles profiles = new CraterProfiles(2);
        profiles.remember("crater:1:64:1", List.of(at(1, 64, 1)));
        profiles.remember("crater:2:64:2", List.of(at(2, 64, 2)));
        profiles.remember("crater:3:64:3", List.of(at(3, 64, 3)));

        assertEquals(Set.of("crater:2:64:2", "crater:3:64:3"), profiles.causes(),
                "Кольцо ограничено: старейшая воронка вытесняется, память не растёт от числа взрывов");
    }

    @Test
    void remember_Profile_IsCappedAndOrdered() {
        CraterProfiles profiles = new CraterProfiles(4, 3);
        profiles.remember("crater:5:64:5", List.of(at(5, 65, 5), at(5, 64, 5), at(5, 63, 5), at(5, 62, 5)));

        assertEquals(List.of(at(5, 62, 5), at(5, 63, 5), at(5, 64, 5)),
                profiles.profile("crater:5:64:5"),
                "Профиль обрезается пределом и идёт по координатам: проверка воронки стоит не больше "
                        + CraterProfiles.MAX_CELLS + " чтений блоков");
    }
}
