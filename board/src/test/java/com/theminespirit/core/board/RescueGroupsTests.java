package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueGroupsTests {
    private static final BlockCoord PIT = new BlockCoord(10, 40, -5);

    @Test
    void form_SixSpiritsInOnePit_OneGroupWithTooledLeader() {
        List<RescueGroups.Member> members = List.of(
                member(1, PIT, 0, 0),
                member(2, PIT, 0, 12),
                member(3, PIT, 3, 0),
                member(4, PIT, 0, 4),
                member(5, PIT, 0, 0),
                member(6, PIT, 0, 0));

        List<RescueGroups.Group> groups = RescueGroups.form(members);

        assertEquals(1, groups.size(), "Шесть духов в одной яме — один план выхода, а не шесть");
        RescueGroups.Group group = groups.get(0);
        assertEquals(member(3, PIT, 3, 0).id(), group.leader(), "Ведущий — дух с лучшей киркой");
        assertEquals(6, group.members().size(), "Все шестеро в группе: никто не ищет выход сам");
        assertEquals(5, group.followers().size(), "Пятеро идут следом по готовому пути");
    }

    @Test
    void form_TwoClustersFarApart_TwoGroups() {
        List<RescueGroups.Member> members = List.of(
                member(1, PIT, 0, 0),
                member(2, PIT, 0, 0),
                member(3, new BlockCoord(100, 40, -5), 2, 0),
                member(4, new BlockCoord(100, 40, -5), 0, 0));

        List<RescueGroups.Group> groups = RescueGroups.form(members);

        assertEquals(2, groups.size(), "Духи из разных областей не тянут один план через полкарты");
        assertTrue(
                groups.stream()
                        .anyMatch(group -> group.leader()
                                .equals(member(3, new BlockCoord(100, 40, -5), 2, 0)
                                        .id())),
                "В дальней группе ведущий с киркой");
    }

    @Test
    void form_Empty_NoGroups() {
        assertTrue(RescueGroups.form(List.of()).isEmpty(), "Пустой список духов не даёт групп");
    }

    @Test
    void form_DuplicateSpirit_Rejected() {
        RescueGroups.Member member = member(1, PIT, 0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> RescueGroups.form(List.of(member, member)),
                "Один дух не может быть в двух группах: спасение адресное");
    }

    private static RescueGroups.Member member(int id, BlockCoord at, int toolTier, int blocks) {
        return new RescueGroups.Member(new UUID(0, id), at, toolTier, blocks);
    }
}
