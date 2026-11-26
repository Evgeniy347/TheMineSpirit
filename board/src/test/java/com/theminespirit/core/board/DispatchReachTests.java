package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchReachTests {
    private static final UUID SPIRIT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BlockCoord FROM = new BlockCoord(0, 64, 0);
    private static final BlockCoord TARGET = new BlockCoord(80, 64, 0);

    @Test
    void allows_ReachableVerdict_AcceptsCandidate() {
        FakeCache cache = new FakeCache(Optional.of(new DispatchReach.Verdict(true, "путь есть")));

        assertTrue(
                DispatchReach.allows(cache, SPIRIT, FROM, TARGET),
                "Достижимый кандидат допускается к выдаче: иначе доска будет откладывать работу даже при "
                        + "готовом пути. При смене allows_ReachableVerdict_AcceptsCandidate проверь DispatchPick "
                        + "и живое назначение ближнего духа на wheat_field.");
        assertEquals(
                0,
                cache.requests,
                "Готовый вердикт не запускает повторный поиск: иначе каждый тик будет тратить бюджет "
                        + "навигации на уже известный путь. При смене allows_ReachableVerdict_AcceptsCandidate "
                        + "проверь RescueReach.Cache и BoardDispatchDesk.tick.");
    }

    @Test
    void allows_UnknownVerdict_RequestsAndDefers() {
        FakeCache cache = new FakeCache(Optional.empty());

        assertFalse(
                DispatchReach.allows(cache, SPIRIT, FROM, TARGET),
                "Неизвестный путь откладывает выдачу: иначе дальний дух снова получит грядку в 84 блоках "
                        + "и будет три минуты биться в clear_area без прогресса. При смене "
                        + "allows_UnknownVerdict_RequestsAndDefers проверь NavigationReach и RescueReach.advance.");
        assertEquals(
                1,
                cache.requests,
                "Для неизвестного пути ставится ровно один запрос: иначе доска либо не проверит "
                        + "достижимость, либо забит кэш дублями. При смене allows_UnknownVerdict_RequestsAndDefers "
                        + "проверь RescueReach.MAX_PENDING и DispatchPick.reachable.");
    }

    @Test
    void allows_UnreachableVerdict_RejectsCandidate() {
        FakeCache cache = new FakeCache(Optional.of(new DispatchReach.Verdict(false, "путь не найден")));

        assertFalse(
                DispatchReach.allows(cache, SPIRIT, FROM, TARGET),
                "Доказанный отсутствие пути исключает кандидата: иначе граф поставит невыполнимое "
                        + "задание и провалит его по тайм-ауту. При смене allows_UnreachableVerdict_RejectsCandidate "
                        + "проверь DispatchPick.pick, ClearStands и живой wheat_field.");
    }

    private static final class FakeCache implements DispatchReach.Cache {
        private final Optional<DispatchReach.Verdict> verdict;
        private int requests;

        private FakeCache(Optional<DispatchReach.Verdict> verdict) {
            this.verdict = verdict;
        }

        @Override
        public Optional<DispatchReach.Verdict> verdict(UUID spirit, BlockCoord from, BlockCoord target) {
            return verdict;
        }

        @Override
        public void request(UUID spirit, BlockCoord from, BlockCoord target) {
            requests++;
        }
    }
}
