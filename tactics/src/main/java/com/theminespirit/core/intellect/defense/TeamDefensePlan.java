package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.List;

public record TeamDefensePlan(
        Point center, List<DefenseSquad> squads, List<SquadOrder> orders, List<MemberOrder> memberOrders) {
    public TeamDefensePlan {
        if (center == null) {
            throw new IllegalArgumentException("center не может быть null: без центра не с чем сверять сектора");
        }
        squads = squads == null ? List.of() : List.copyOf(squads);
        orders = orders == null ? List.of() : List.copyOf(orders);
        memberOrders = memberOrders == null ? List.of() : List.copyOf(memberOrders);
    }
}
