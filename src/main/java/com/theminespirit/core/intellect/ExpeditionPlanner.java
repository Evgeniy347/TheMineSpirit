package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ExpeditionPlanner {
    public Optional<Expedition> plan(SettlementView view, List<SpiritSnapshot> candidates, Point target) {
        if (view == null) {
            throw new IllegalArgumentException("Экспедиция без обстановки не планируется: нужда — из запасов");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("Экспедиция без кандидатов не планируется: команда — из духов");
        }
        if (target == null) {
            throw new IllegalArgumentException("Экспедиция без цели не планируется: шахта обязана быть известна");
        }
        if (!view.needsOre(IntellectTuning.ORE_TARGET, IntellectTuning.IRON_TARGET,
                IntellectTuning.WEAPON_TARGET)) {
            return Optional.empty();
        }
        List<SpiritSnapshot> suitable = new ArrayList<>();
        for (SpiritSnapshot candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("Кандидат null в списке: команда — из конкретных духов");
            }
            if (candidate.role() == SpiritRole.WORKER) {
                suitable.add(candidate);
            }
        }
        suitable.sort(Comparator.comparingDouble((SpiritSnapshot spirit) -> spirit.pos().distanceTo(target))
                .thenComparing(SpiritSnapshot::id));
        if (suitable.size() < IntellectTuning.TEAM_MIN) {
            return Optional.empty();
        }
        List<UUID> team = suitable.stream().limit(IntellectTuning.TEAM_MAX).map(SpiritSnapshot::id).toList();
        return Optional.of(new Expedition(team, target, view.tick()));
    }
}
