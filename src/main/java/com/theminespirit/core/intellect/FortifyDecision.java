package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FortifyDecision {
    private FortifyDecision() {
    }

    public static FortifyMode decide(SettlementView view, List<EnemyFact> enemies, boolean passageNearby,
            boolean retreatDifficult) {
        Objects.requireNonNull(view, "Решение укрепления без снимка не считается: без духов строить некому");
        Objects.requireNonNull(enemies, "Решение укрепления без врагов не считается: пустой список — нет угроз");
        if (enemies.size() < FortifyTuning.FORTIFY_ENEMY_MIN) {
            return FortifyMode.NONE;
        }
        if (!retreatDifficult) {
            return FortifyMode.NONE;
        }
        if (view.spirits().isEmpty()) {
            return FortifyMode.NONE;
        }
        return passageNearby ? FortifyMode.BARRICADE : FortifyMode.SHELTER;
    }

    public static Optional<FortifyRoster> roster(FortifyMode mode, List<SpiritSnapshot> spirits,
            List<EnemyFact> enemies) {
        Objects.requireNonNull(mode, "Ростер укрепления без режима не собирается: режим входит в приказ");
        Objects.requireNonNull(spirits, "Ростер укрепления без духов не собирается: строить и крыть некому");
        Objects.requireNonNull(enemies, "Ростер укрепления без врагов не собирается: пустой список — нет угроз");
        if (mode == FortifyMode.NONE) {
            return Optional.empty();
        }
        Point threat = threatCenter(spirits, enemies);
        List<SpiritSnapshot> candidates = new ArrayList<>();
        for (SpiritSnapshot spirit : spirits) {
            if (spirit.role() != SpiritRole.DEFENDER) {
                candidates.add(spirit);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        candidates.sort(Comparator.comparingDouble((SpiritSnapshot spirit) -> spirit.pos().distanceTo(threat))
                .thenComparing(SpiritSnapshot::id));
        SpiritSnapshot builder = candidates.get(0);
        List<UUID> guards = spirits.stream().map(SpiritSnapshot::id).filter(id -> !id.equals(builder.id())).sorted()
                .toList();
        return Optional.of(new FortifyRoster(builder.id(), guards, Optional.of(builder.pos()), mode));
    }

    private static Point threatCenter(List<SpiritSnapshot> spirits, List<EnemyFact> enemies) {
        if (enemies.isEmpty()) {
            return spirits.stream().map(SpiritSnapshot::pos).min(Comparator.comparingDouble(Point::x)
                    .thenComparingDouble(Point::y).thenComparingDouble(Point::z)).orElseGet(() -> new Point(0, 64, 0));
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (EnemyFact enemy : enemies) {
            x += enemy.pos().x();
            y += enemy.pos().y();
            z += enemy.pos().z();
        }
        return new Point(x / enemies.size(), y / enemies.size(), z / enemies.size());
    }
}
