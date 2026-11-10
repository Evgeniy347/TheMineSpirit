package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RescueGroups {
    public static final int RADIUS = 8;

    public record Member(UUID id, BlockCoord at, int toolTier, int blocks) {
        public Member {
            Objects.requireNonNull(id, "Дух группы не может быть null: группа адресная");
            Objects.requireNonNull(at, "Точка духа не может быть null: по ней видно, рядом ли духи");
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

    public static List<Group> form(List<Member> members) {
        return form(members, RADIUS);
    }

    public static List<Group> form(List<Member> members, int radius) {
        Objects.requireNonNull(members, "Список духов задаётся списком, пустой — пустым");
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус группы " + radius + " отрицателен");
        }
        Set<UUID> seen = new HashSet<>();
        for (Member member : members) {
            if (!seen.add(member.id())) {
                throw new IllegalArgumentException("Дух " + member.id() + " указан дважды: группа не определена");
            }
        }
        List<Group> groups = new ArrayList<>();
        Set<UUID> grouped = new HashSet<>();
        for (Member start : members) {
            if (grouped.contains(start.id())) {
                continue;
            }
            List<Member> group = new ArrayList<>();
            List<Member> queue = new ArrayList<>();
            queue.add(start);
            grouped.add(start.id());
            for (int index = 0; index < queue.size(); index++) {
                Member here = queue.get(index);
                group.add(here);
                for (Member other : members) {
                    if (!grouped.contains(other.id()) && near(here.at(), other.at(), radius)) {
                        grouped.add(other.id());
                        queue.add(other);
                    }
                }
            }
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

    private static boolean near(BlockCoord a, BlockCoord b, int radius) {
        return Math.abs(a.x() - b.x()) <= radius
                && Math.abs(a.y() - b.y()) <= radius
                && Math.abs(a.z() - b.z()) <= radius;
    }
}
