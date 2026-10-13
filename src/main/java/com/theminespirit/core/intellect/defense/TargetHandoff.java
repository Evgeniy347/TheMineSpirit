package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TargetHandoff {
    private TargetHandoff() {
    }

    public static Optional<UUID> handOff(UUID self, EnemyFact target, List<DefenderCapability> allies,
            Set<UUID> busy, DefenseProfile profile) {
        if (self == null) {
            throw new IllegalArgumentException("self не может быть null: уходящий из боя обязан быть известен");
        }
        if (target == null) {
            throw new IllegalArgumentException("target не может быть null: передавать нечего");
        }
        if (allies == null) {
            throw new IllegalArgumentException("allies не может быть null: нет союзников — пустой список");
        }
        if (busy == null) {
            throw new IllegalArgumentException("busy не может быть null: нет занятых — пустое множество");
        }
        if (profile == null) {
            throw new IllegalArgumentException(
                    "profile не может быть null: пригодность роли считается по таблице приоритетов");
        }
        TargetClass cls = profile.classify(target.type());
        boolean archerPresent = allies.stream()
                .anyMatch(ally -> ally != null && ally.bow() && !busy.contains(ally.id())
                        && ally.hpFraction() >= DefenseTuning.LOW_HP_FRACTION);
        UUID best = null;
        double bestDistance = 0;
        for (DefenderCapability ally : allies) {
            if (ally == null) {
                throw new IllegalArgumentException("allies содержит null: союзник без снимка цель не примет");
            }
            if (ally.id().equals(self) || busy.contains(ally.id())
                    || ally.hpFraction() < DefenseTuning.LOW_HP_FRACTION) {
                continue;
            }
            if (!profile.orderFor(ally.role(), archerPresent).contains(cls)) {
                continue;
            }
            double distance = ally.pos().distanceTo(target.pos());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && ally.id().compareTo(best) < 0)) {
                best = ally.id();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
