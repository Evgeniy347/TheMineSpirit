package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.coordinator.DefenseAssignment;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DefensePlan(Optional<Point> defendedPoint, List<DefenseAssignment> posts, List<DefenderOrder> orders) {
    public DefensePlan {
        Objects.requireNonNull(
                defendedPoint, "defendedPoint не может быть null: обороны нет — передайте Optional.empty()");
        Objects.requireNonNull(posts, "posts не может быть null: нет постов — пустой список");
        Objects.requireNonNull(orders, "orders не может быть null: нет приказов — пустой список");
        posts = List.copyOf(posts);
        orders = List.copyOf(orders);
    }
}
