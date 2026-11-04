package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class NavChunkLoadingTests {
    @Test
    void route_GoalInUnloadedChunk_NotDeclaredAirThenFoundWhenLoaded() {
        NavSegmentedRoute before = plan(NavTestWorld.flat(0).unloadFrom(5));

        assertNotEquals(
                NavSearchStatus.FOUND,
                before.status(),
                "observe22: цель в незагруженном чанке — «неизвестно», а не воздух: расчёт не объявляет путь "
                        + "найденным, пока чанк не подгружен");

        NavSegmentedRoute after = plan(NavTestWorld.flat(0));

        assertEquals(
                NavSearchStatus.FOUND,
                after.status(),
                "Чанк коридора подгружен (NavigationTickets) — путь находится: дух не ходит по кругу "
                        + "«маршрут не найден»");
    }

    private static NavSegmentedRoute plan(NavTestWorld world) {
        NavSegmentedSession session = new NavSegmentedSession(
                new NavContext(world, new NavSettings(10_000, 10_000, 200_000, 50, 3, 12, 2, 20, 2, true, true), 64),
                new BlockCoord(0, 1, 0),
                new NavGoalBlock(100, 1, 0),
                50,
                6,
                0.5);
        return session.step(Long.MAX_VALUE);
    }
}
