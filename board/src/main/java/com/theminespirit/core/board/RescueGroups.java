package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RescueGroups {
    private static final int MAX_SEARCH_CELLS = 4096;

    @FunctionalInterface
    public interface Passage {
        boolean open(BlockCoord cell);
    }

    public record Member(UUID id, BlockCoord at, int toolTier, int blocks) {
        public Member {
            Objects.requireNonNull(id, "Дух группы не может быть null: группа адресная");
            Objects.requireNonNull(at, "Точка духа не может быть null: по ней видно, в одной ли пещере духи");
            if (toolTier < 0) {
                throw new IllegalArgumentException("Уровень инструмента " + toolTier + " отрицателен");
            }
            if (blocks < 0) {
                throw new IllegalArgumentException("Запас блоков " + blocks + " отрицателен");
            }
        }
    }

    public record Group(UUID leader, List<Member> members) {
        public Group {
            Objects.requireNonNull(leader, "Ведущий группы не может быть null: ему прокладывать путь");
            members = List.copyOf(Objects.requireNonNull(members, "Участники группы задаются списком"));
            if (members.isEmpty()) {
                throw new IllegalArgumentException("Группа без участников: ведущего не из кого выбрать");
            }
            if (members.stream().noneMatch(member -> member.id().equals(leader))) {
                throw new IllegalArgumentException("Ведущий " + leader + " не входит в свою группу");
            }
        }

        public List<Member> followers() {
            return members.stream()
                    .filter(member -> !member.id().equals(leader))
                    .toList();
        }
    }

    private RescueGroups() {}

    public static List<Group> form(List<Member> members, Passage passage) {
        Objects.requireNonNull(members, "Список духов задаётся списком, пустой — пустым");
        Objects.requireNonNull(
                passage, "Проходимость мира не может быть null: без неё не видно, кто с кем в одной пещере");
        Set<UUID> seen = new HashSet<>();
        for (Member member : members) {
            if (!seen.add(member.id())) {
                throw new IllegalArgumentException("Дух " + member.id() + " указан дважды: группа не определена");
            }
        }
        Map<BlockCoord, List<Member>> waiting = new HashMap<>();
        for (Member member : members) {
            waiting.computeIfAbsent(member.at(), at -> new ArrayList<>()).add(member);
        }
        List<Group> groups = new ArrayList<>();
        Set<UUID> grouped = new HashSet<>();
        for (Member start : members) {
            if (grouped.contains(start.id())) {
                continue;
            }
            List<Member> group = new ArrayList<>();
            collect(start, waiting, grouped, group, passage);
            groups.add(groupOf(group));
        }
        groups.sort(Comparator.comparing(group -> group.leader().toString()));
        return List.copyOf(groups);
    }

    public static Member leader(List<Member> members) {
        Objects.requireNonNull(members, "Участники задаются списком: без них нет ведущего");
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Список участников пуст: ведущего не из кого выбрать");
        }
        return members.stream()
                .max(Comparator.comparingInt(Member::toolTier)
                        .thenComparingInt(Member::blocks)
                        .thenComparing(member -> member.id().toString()))
                .orElseThrow();
    }

    private static Group groupOf(List<Member> members) {
        Member leader = leader(members);
        List<Member> sorted = new ArrayList<>();
        sorted.add(leader);
        members.stream()
                .filter(member -> !member.id().equals(leader.id()))
                .sorted(Comparator.comparing(member -> member.id().toString()))
                .forEach(sorted::add);
        return new Group(leader.id(), List.copyOf(sorted));
    }

    private static void collect(
            Member start,
            Map<BlockCoord, List<Member>> waiting,
            Set<UUID> grouped,
            List<Member> group,
            Passage passage) {
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(start.at());
        queue.add(start.at());
        while (!queue.isEmpty()) {
            BlockCoord here = queue.poll();
            for (Member member : waiting.getOrDefault(here, List.of())) {
                if (grouped.add(member.id())) {
                    group.add(member);
                }
            }
            if (seen.size() >= MAX_SEARCH_CELLS) {
                continue;
            }
            for (BlockCoord next : neighbours(here)) {
                if (seen.contains(next) || !passage.open(next)) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
    }

    private static List<BlockCoord> neighbours(BlockCoord cell) {
        return List.of(
                new BlockCoord(cell.x() + 1, cell.y(), cell.z()),
                new BlockCoord(cell.x() - 1, cell.y(), cell.z()),
                new BlockCoord(cell.x(), cell.y() + 1, cell.z()),
                new BlockCoord(cell.x(), cell.y() - 1, cell.z()),
                new BlockCoord(cell.x(), cell.y(), cell.z() + 1),
                new BlockCoord(cell.x(), cell.y(), cell.z() - 1));
    }
}
