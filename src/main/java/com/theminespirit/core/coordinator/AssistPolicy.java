package com.theminespirit.core.coordinator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AssistPolicy {
    AssistPolicy() {
    }

    public List<UUID> assist(UUID attacked, List<SpiritSnapshot> spirits, List<DefenseAssignment> posts,
            int maxHelpers) {
        if (attacked == null) {
            throw new IllegalArgumentException("attacked не может быть null: помощь неизвестному духу не нужна");
        }
        if (spirits == null) {
            throw new IllegalArgumentException("spirits не может быть null: нет снимков — пустой список");
        }
        if (posts == null) {
            throw new IllegalArgumentException("posts не может быть null: нет обороны — пустой список");
        }
        if (maxHelpers < 0) {
            throw new IllegalArgumentException(
                    "maxHelpers = " + maxHelpers + ": предел помощников не может быть отрицательным");
        }
        if (maxHelpers == 0) {
            return List.of();
        }
        Map<UUID, SpiritSnapshot> byId = new HashMap<>();
        for (SpiritSnapshot spirit : spirits) {
            byId.putIfAbsent(spirit.id(), spirit);
        }
        SpiritSnapshot ward = byId.get(attacked);
        if (ward == null) {
            return List.of();
        }
        Set<UUID> onDuty = posts.stream().map(DefenseAssignment::defender).collect(Collectors.toSet());
        return spirits.stream()
                .filter(spirit -> onDuty.contains(spirit.id()))
                .filter(spirit -> !spirit.id().equals(attacked))
                .filter(spirit -> !spirit.attacked() && spirit.threatsNear() == 0)
                .sorted(Comparator.comparingDouble((SpiritSnapshot spirit) -> spirit.pos().distanceTo(ward.pos()))
                        .thenComparing(spirit -> spirit.id()))
                .limit(maxHelpers)
                .map(SpiritSnapshot::id)
                .toList();
    }
}
