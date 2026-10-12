package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class PolicyIdTests {
    @Test
    void values_ContainsAllExpectedPolicies_ExactlyTen() {
        EnumSet<PolicyId> expected = EnumSet.of(
                PolicyId.LAVA_FIRE, PolicyId.DROP, PolicyId.WATER, PolicyId.AIR,
                PolicyId.EXPLOSIVE, PolicyId.PORTAL, PolicyId.PROTECTED_BLOCKS,
                PolicyId.PLAYER_BUILDINGS, PolicyId.PROTECTED_ZONE, PolicyId.WORK_ZONE
        );

        assertEquals(expected, EnumSet.allOf(PolicyId.class),
                "PolicyId должен содержать ровно 10 политик из таблицы чеклиста 20");
    }

    @Test
    void valueOf_ExistingName_ReturnsPolicy() {
        assertEquals(PolicyId.LAVA_FIRE, PolicyId.valueOf("LAVA_FIRE"),
                "valueOf(LAVA_FIRE) обязан вернуть LAVA_FIRE");
        assertEquals(PolicyId.WORK_ZONE, PolicyId.valueOf("WORK_ZONE"),
                "valueOf(WORK_ZONE) обязан вернуть WORK_ZONE");
    }

    @Test
    void valueOf_UnknownName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> PolicyId.valueOf("UNKNOWN"),
                "valueOf с неизвестным именем обязан бросать IllegalArgumentException");
    }
}