package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.List;

public record DefenseSquad(String id, List<DefenseMember> members) {
    public DefenseSquad {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id отряда не может быть пустым: по нему адресуется решение");
        }
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("members отряда не может быть пустым: отряд без духов не решает");
        }
        members = List.copyOf(members);
    }

    public Point center() {
        double x = 0;
        double y = 0;
        double z = 0;
        for (DefenseMember member : members) {
            x += member.pos().x();
            y += member.pos().y();
            z += member.pos().z();
        }
        int size = members.size();
        return new Point(x / size, y / size, z / size);
    }

    public boolean hasBow() {
        return members.stream().anyMatch(DefenseMember::bow);
    }

    public boolean has(DefensePotion potion) {
        return members.stream().anyMatch(member -> member.has(potion));
    }

    public List<DefenderCapability> capabilities() {
        return members.stream().map(DefenseMember::capability).toList();
    }
}
