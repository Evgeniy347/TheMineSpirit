package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.policy.api.PolicyId;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyIdTests {
    @Test
    void values_ContainsAllExpectedPolicies_ExactlyEleven() {
        Set<PolicyId> expected = EnumSet.of(
                PolicyId.LAVA_FIRE,
                PolicyId.DROP,
                PolicyId.WATER,
                PolicyId.AIR,
                PolicyId.EXPLOSIVE,
                PolicyId.PORTAL,
                PolicyId.PROTECTED_BLOCKS,
                PolicyId.PLAYER_BUILDINGS,
                PolicyId.PROTECTED_ZONE,
                PolicyId.WORK_ZONE,
                PolicyId.HUNGER);

        assertEquals(
                expected,
                EnumSet.allOf(PolicyId.class),
                "PolicyId должен содержать ровно 11 политик из таблицы чеклиста 20 и рефлекса голода"
                        + " Иначе тест values_ContainsAllExpectedPolicies_ExactlyEleven перестанет ловить эту поломку. При смене values_ContainsAllExpectedPolicies_ExactlyEleven проверь это утверждение.");
    }

    @Test
    void valueOf_ExistingName_ReturnsPolicy() {
        assertEquals(
                PolicyId.LAVA_FIRE,
                PolicyId.valueOf("LAVA_FIRE"),
                "valueOf(LAVA_FIRE) обязан вернуть LAVA_FIRE"
                        + " Иначе тест valueOf_ExistingName_ReturnsPolicy перестанет ловить эту поломку. При смене valueOf_ExistingName_ReturnsPolicy проверь это утверждение.");
        assertEquals(
                PolicyId.WORK_ZONE,
                PolicyId.valueOf("WORK_ZONE"),
                "valueOf(WORK_ZONE) обязан вернуть WORK_ZONE"
                        + " Иначе тест valueOf_ExistingName_ReturnsPolicy перестанет ловить эту поломку. При смене valueOf_ExistingName_ReturnsPolicy проверь это утверждение.");
    }

    @Test
    void valueOf_UnknownName_ThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PolicyId.valueOf("UNKNOWN"),
                "valueOf с неизвестным именем обязан бросать IllegalArgumentException"
                        + " Иначе тест valueOf_UnknownName_ThrowsException перестанет ловить эту поломку. При смене valueOf_UnknownName_ThrowsException проверь это утверждение.");
    }
}
