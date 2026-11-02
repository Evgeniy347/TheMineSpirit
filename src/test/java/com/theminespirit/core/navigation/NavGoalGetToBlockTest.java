// lib: Из Baritone (github.com/cabaletta/baritone, pathing/goals/GoalGetToBlockTest.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NavGoalGetToBlockTest {
    @Test
    void isInGoal_GridAroundTarget_AcceptsOnlyAdjacent() {
        Set<String> acceptable = new HashSet<>(Set.of(
                "0,0,0", "0,0,1", "0,0,-1", "1,0,0", "-1,0,0", "0,-1,1", "0,-1,-1", "1,-1,0", "-1,-1,0", "0,1,0",
                "0,-1,0", "0,-2,0"));
        NavGoalGetToBlock goal = new NavGoalGetToBlock(0, 0, 0);
        for (int x = -10; x <= 10; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -10; z <= 10; z++) {
                    if (goal.isInGoal(x, y, z)) {
                        String repr = x + "," + y + "," + z;
                        assertTrue(acceptable.contains(repr), "лишняя клетка цели: " + repr);
                        acceptable.remove(repr);
                    }
                }
            }
        }
        assertTrue(acceptable.isEmpty(), "не все соседние клетки попали в цель: " + acceptable);
    }
}
