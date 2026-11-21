package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

        assertTrue(
                live.isEmpty(),
                "Засыпанная воронка уходит из живых причин: " + live
                        + " Иначе тест retainLive_FilledCrater_RemovesCause перестанет ловить эту поломку. При смене retainLive_FilledCrater_RemovesCause проверь это утверждение.");
        assertEquals(
                0,
                profiles.size(),
                "Профиль засыпанной воронки не копится в кольце"
                        + " Иначе тест retainLive_FilledCrater_RemovesCause перестанет ловить эту поломку. При смене retainLive_FilledCrater_RemovesCause проверь это утверждение.");
    }

    @Test
    void retainLive_RemainingHole_KeepsOnlyThatCause() {
        CraterProfiles profiles = new CraterProfiles(8);
        profiles.remember("crater:1:64:1", List.of(at(1, 64, 1), at(1, 63, 1)));
        profiles.remember("crater:9:64:9", List.of(at(9, 64, 9)));
        Set<BlockCoord> holes = new HashSet<>(List.of(at(1, 63, 1)));

        Set<String> live = profiles.retainLive(holes::contains);

        assertEquals(
                Set.of("crater:1:64:1"),
                live,
                "Живая причина — только воронка с оставшейся пустой клеткой: " + live
                        + " Иначе тест retainLive_RemainingHole_KeepsOnlyThatCause перестанет ловить эту поломку. При смене retainLive_RemainingHole_KeepsOnlyThatCause проверь это утверждение.");
        assertEquals(
                Set.of("crater:1:64:1"),
                profiles.causes(),
                "Засыпанная воронка уходит из кольца, живая остаётся: " + profiles.causes()
                        + " Иначе тест retainLive_RemainingHole_KeepsOnlyThatCause перестанет ловить эту поломку. При смене retainLive_RemainingHole_KeepsOnlyThatCause проверь это утверждение.");
    }

    @Test
    void remember_SameCauseTwice_ReplacesProfileWithoutDuplicate() {
        CraterProfiles profiles = new CraterProfiles(8);
        profiles.remember("crater:1:64:1", List.of(at(1, 64, 1)));
        profiles.remember("crater:1:64:1", List.of(at(2, 64, 2)));

        assertEquals(
                1,
                profiles.size(),
                "Повторный взрыв той же точки не плодит вторую причину"
                        + " Иначе тест remember_SameCauseTwice_ReplacesProfileWithoutDuplicate перестанет ловить эту поломку. При смене remember_SameCauseTwice_ReplacesProfileWithoutDuplicate проверь это утверждение.");
        assertEquals(
                List.of(at(2, 64, 2)),
                profiles.profile("crater:1:64:1"),
                "Профиль той же воронки заменяется последним снимком"
                        + " Иначе тест remember_SameCauseTwice_ReplacesProfileWithoutDuplicate перестанет ловить эту поломку. При смене remember_SameCauseTwice_ReplacesProfileWithoutDuplicate проверь это утверждение.");
    }

    @Test
    void remember_TooManyCraters_EvictsOldest() {
        CraterProfiles profiles = new CraterProfiles(2);
        profiles.remember("crater:1:64:1", List.of(at(1, 64, 1)));
        profiles.remember("crater:2:64:2", List.of(at(2, 64, 2)));
        profiles.remember("crater:3:64:3", List.of(at(3, 64, 3)));

        assertEquals(
                Set.of("crater:2:64:2", "crater:3:64:3"),
                profiles.causes(),
                "Кольцо ограничено: старейшая воронка вытесняется, память не растёт от числа взрывов"
                        + " Иначе тест remember_TooManyCraters_EvictsOldest перестанет ловить эту поломку. При смене remember_TooManyCraters_EvictsOldest проверь это утверждение.");
    }

    @Test
    void center_CraterCause_ParsesCoordinates() {
        assertEquals(
                Optional.of(at(1, -64, 3)),
                CraterProfiles.center("crater:1:-64:3"),
                "Центр воронки берётся из причины: без него граф не поставит FILL_CRATER по факту взрыва"
                        + " Иначе тест center_CraterCause_ParsesCoordinates перестанет ловить эту поломку. При смене center_CraterCause_ParsesCoordinates проверь это утверждение.");
    }

    @Test
    void center_ForeignOrBrokenCause_StaysEmpty() {
        assertEquals(
                Optional.empty(),
                CraterProfiles.center("fence:1:64:3"),
                "Чужая причина не выдаёт центр воронки: засыпка ставится только по взрыву крипера"
                        + " Иначе тест center_ForeignOrBrokenCause_StaysEmpty перестанет ловить эту поломку. При смене center_ForeignOrBrokenCause_StaysEmpty проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                CraterProfiles.center("crater:1:x:3"),
                "Испорченная причина не выдаёт центр: молчаливая подстановка нуля послала бы духа в пустоту"
                        + " Иначе тест center_ForeignOrBrokenCause_StaysEmpty перестанет ловить эту поломку. При смене center_ForeignOrBrokenCause_StaysEmpty проверь это утверждение.");
    }

    @Test
    void remember_Profile_IsCappedAndOrdered() {
        CraterProfiles profiles = new CraterProfiles(4, 3);
        profiles.remember("crater:5:64:5", List.of(at(5, 65, 5), at(5, 64, 5), at(5, 63, 5), at(5, 62, 5)));

        assertEquals(
                List.of(at(5, 62, 5), at(5, 63, 5), at(5, 64, 5)),
                profiles.profile("crater:5:64:5"),
                "Профиль обрезается пределом и идёт по координатам: проверка воронки стоит не больше "
                        + CraterProfiles.MAX_CELLS + " чтений блоков"
                        + " Иначе тест remember_Profile_IsCappedAndOrdered перестанет ловить эту поломку. При смене remember_Profile_IsCappedAndOrdered проверь это утверждение.");
    }
}
