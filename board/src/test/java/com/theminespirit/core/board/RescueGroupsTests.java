package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueGroupsTests {
    private static final BlockCoord PIT = new BlockCoord(10, 40, -5);
    private static final RescueGroups.Passage OPEN = cell -> false;

    @Test
    void form_SixSpiritsInOnePit_OneGroupWithTooledLeader() {
        List<RescueGroups.Member> members = List.of(
                member(1, PIT, 0, 0),
                member(2, PIT, 0, 12),
                member(3, PIT, 3, 0),
                member(4, PIT, 0, 4),
                member(5, PIT, 0, 0),
                member(6, PIT, 0, 0));

        List<RescueGroups.Group> groups = RescueGroups.form(members, OPEN);

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

        List<RescueGroups.Group> groups = RescueGroups.form(members, OPEN);

        assertEquals(2, groups.size(), "Духи из разных областей не тянут один план через полкарты");
        assertTrue(
                groups.stream()
                        .anyMatch(group -> group.leader()
                                .equals(member(3, new BlockCoord(100, 40, -5), 2, 0)
                                        .id())),
                "В дальней группе ведущий с киркой");
    }

    @Test
    void form_SameCaveNineApart_OneGroupByPassage() {
        Set<BlockCoord> cave = new HashSet<>();
        for (int x = 0; x <= 9; x++) {
            cave.add(new BlockCoord(x, 40, 0));
        }
        List<RescueGroups.Member> members =
                List.of(member(1, new BlockCoord(0, 40, 0), 0, 0), member(2, new BlockCoord(9, 40, 0), 2, 4));

        List<RescueGroups.Group> groups = RescueGroups.form(members, cave::contains);

        assertEquals(
                1,
                groups.size(),
                "Духи в одной пещере на расстоянии 9 связаны проходом: один план и один ведущий, а не два");
        assertEquals(
                member(2, new BlockCoord(9, 40, 0), 2, 4).id(),
                groups.get(0).leader(),
                "В связанной пещере ведущий — дух с киркой, даже если он дальше от входа");
    }

    @Test
    void form_CloseButWallBetween_TwoGroups() {
        Set<BlockCoord> cave = new HashSet<>();
        cave.add(new BlockCoord(0, 40, 0));
        cave.add(new BlockCoord(2, 40, 0));
        List<RescueGroups.Member> members =
                List.of(member(1, new BlockCoord(0, 40, 0), 0, 0), member(2, new BlockCoord(2, 40, 0), 0, 0));

        List<RescueGroups.Group> groups = RescueGroups.form(members, cave::contains);

        assertEquals(2, groups.size(), "Близость без прохода — не одна пещера: сквозь стену план не общий");
    }

    @Test
    void form_UnknownChunkBetween_DoesNotConnect() {
        Set<BlockCoord> cave = new HashSet<>();
        cave.add(new BlockCoord(0, 40, 0));
        cave.add(new BlockCoord(2, 40, 0));
        List<RescueGroups.Member> members =
                List.of(member(1, new BlockCoord(0, 40, 0), 0, 0), member(2, new BlockCoord(2, 40, 0), 0, 0));

        List<RescueGroups.Group> groups = RescueGroups.form(members, cell -> cell.x() != 1 && cave.contains(cell));

        assertEquals(2, groups.size(), "Незагруженный чанк — «неизвестно», а не проход: духи остаются в своих группах");
    }

    @Test
    void form_PickaxeOwner_LeadsEvenWithFewerBlocks() {
        List<RescueGroups.Member> members = List.of(member(1, PIT, 1, 0), member(2, PIT, 0, 64));

        List<RescueGroups.Group> groups = RescueGroups.form(members, OPEN);

        assertEquals(
                member(1, PIT, 1, 0).id(),
                groups.get(0).leader(),
                "Ведущий — владелец кирки, а не самый богатый блоками: передавать инструмент через группу не нужно");
    }

    @Test
    void form_Empty_NoGroups() {
        assertTrue(RescueGroups.form(List.of(), OPEN).isEmpty(), "Пустой список духов не даёт групп");
    }

    @Test
    void form_DuplicateSpirit_Rejected() {
        RescueGroups.Member member = member(1, PIT, 0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> RescueGroups.form(List.of(member, member), OPEN),
                "Один дух не может быть в двух группах: спасение адресное");
    }

    @Test
    void walkers_OnlyReachableAndOnSurface_GoHomeByThemselves() {
        RescueGroups.Member leader = member(1, PIT, 3, 0);
        RescueGroups.Member surfaced = member(2, new BlockCoord(11, 67, -5), 0, 0);
        RescueGroups.Member inShaft = member(3, new BlockCoord(12, 40, -5), 0, 0);
        RescueGroups.Group group = new RescueGroups.Group(leader.id(), List.of(leader, surfaced, inShaft));

        List<RescueGroups.Member> walkers = RescueGroups.walkers(
                group,
                member -> member.id().equals(surfaced.id()) || member.id().equals(inShaft.id()),
                member -> member.id().equals(surfaced.id()));

        assertEquals(
                List.of(surfaced),
                walkers,
                "Ведомый в шахте не получает walk(home): шахту закрывают за спиной ведущего, и путь, "
                        + "посчитанный по открытому проходу, кончается DeadEndRefusal. Идут сами только те, "
                        + "кто уже на поверхности; остальные ждут ведущего");
    }

    @Test
    void walkers_LeaderWithoutSurface_IsNotSentHomeAlone() {
        RescueGroups.Member leader = member(1, PIT, 3, 0);
        RescueGroups.Member follower = member(2, PIT, 0, 0);
        RescueGroups.Group group = new RescueGroups.Group(leader.id(), List.of(leader, follower));

        List<RescueGroups.Member> walkers = RescueGroups.walkers(
                group, member -> true, member -> member.id().equals(follower.id()));

        assertEquals(
                List.of(follower),
                walkers,
                "Ведущий в шахте не уходит пешком: путь по открытому проходу исчезнет после засыпки, "
                        + "а группа останется без того, кто прокладывает выход");
    }

    private static RescueGroups.Member member(int id, BlockCoord at, int toolTier, int blocks) {
        return new RescueGroups.Member(new UUID(0, id), at, toolTier, blocks);
    }
}
