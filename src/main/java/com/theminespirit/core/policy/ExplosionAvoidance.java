package com.theminespirit.core.policy;

import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExplosionAvoidance {
    public static final double CREEPER_HOLD = 7.0;
    public static final double CHARGED_CREEPER_HOLD = 10.0;
    public static final double PRIMED_TNT_HOLD = 8.0;
    public static final double SWELLING_EXTRA = 3.0;

    private ExplosionAvoidance() {
    }

    public enum ThreatType {
        CREEPER(CREEPER_HOLD),
        CHARGED_CREEPER(CHARGED_CREEPER_HOLD),
        PRIMED_TNT(PRIMED_TNT_HOLD);

        private final double hold;

        ThreatType(double hold) {
            this.hold = hold;
        }

        public double hold() {
            return hold;
        }
    }

    public record ThreatFact(ThreatType type, BlockCoord pos, boolean swelling) {
        public ThreatFact {
            Objects.requireNonNull(type, "Тип угрозы не может быть null: по нему выбирается расстояние держания");
            Objects.requireNonNull(pos, "Позиция угрозы не может быть null: от неё считается расстояние");
        }

        public double holdDistance() {
            return swelling ? type.hold() + SWELLING_EXTRA : type.hold();
        }
    }

    public static PolicyVerdict evaluate(BlockCoord self, List<ThreatFact> threats, SafetyView view) {
        Objects.requireNonNull(self, "Клетка духа не может быть null: от неё считается расстояние до угроз");
        Objects.requireNonNull(threats, "Список угроз не может быть null: пустой список задаётся List.of()");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему планируется бегство");
        List<FleePlanner.Threat> planned = new ArrayList<>(threats.size());
        ThreatFact closest = null;
        double closestSlack = 0;
        boolean urgent = false;
        for (ThreatFact fact : threats) {
            FleePlanner.Threat threat = new FleePlanner.Threat(fact.pos(), fact.holdDistance());
            planned.add(threat);
            double slack = threat.slack(self);
            if (slack < 0 && fact.swelling()) {
                urgent = true;
            }
            if (slack < closestSlack) {
                closest = fact;
                closestSlack = slack;
            }
        }
        if (closest == null) {
            return new PolicyVerdict.Clear();
        }
        FleePlanner.Plan plan = FleePlanner.plan(self, planned, view);
        String urgency = urgent ? "срочно, угроза набухает: " : "";
        return new PolicyVerdict.Reflex(PolicyId.EXPLOSIVE, plan.action(),
                urgency + closest.type() + " в " + closest.pos().x() + " " + closest.pos().y() + " " + closest.pos().z()
                        + ", держать " + closest.holdDistance() + " блоков");
    }
}
