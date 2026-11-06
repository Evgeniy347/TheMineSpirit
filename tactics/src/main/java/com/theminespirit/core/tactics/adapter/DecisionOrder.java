package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.FactChecks;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TargetSelector;
import java.util.Objects;
import java.util.Optional;

public record DecisionOrder(CombatMode mode, boolean zone, Optional<String> ward, Optional<CombatDirective> directive) {
    public DecisionOrder {
        FactChecks.required("DecisionOrder.mode", mode);
        FactChecks.required("DecisionOrder.ward", ward);
        FactChecks.required("DecisionOrder.directive", directive);
    }

    public static DecisionOrder of(
            CombatMode mode, boolean zone, Optional<String> ward, Optional<CombatDirective> directive) {
        return new DecisionOrder(mode, zone, ward, directive);
    }

    public String label() {
        StringBuilder text = new StringBuilder(mode.name());
        if (zone) {
            text.append(", зона охраны");
        }
        ward.ifPresent(id -> text.append(", подопечный ").append(id));
        directive.ifPresent(order -> text.append(", ").append(directiveLabel(order)));
        return text.toString();
    }

    private static String directiveLabel(CombatDirective directive) {
        if (directive.target().isPresent()) {
            return directive.mode() == CombatMode.PROTECT
                    ? "приказ защиты " + targetLabel(directive)
                    : "приказ атаки " + targetLabel(directive);
        }
        if (directive.retreat().filter(method -> method != RetreatMethod.NONE).isPresent()) {
            return "приказ отхода " + directive.retreat().get().name();
        }
        return "приказ " + directive.mode().name();
    }

    private static String targetLabel(CombatDirective directive) {
        TargetSelector.Target target = directive.target().get();
        return Objects.requireNonNull(target.name().orElse(target.kind().name()));
    }
}
