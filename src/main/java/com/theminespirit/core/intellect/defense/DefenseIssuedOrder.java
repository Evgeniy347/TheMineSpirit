package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.CombatDirective;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DefenseIssuedOrder(UUID defender, CombatDirective directive, Optional<String> forceTargetId) {
    public DefenseIssuedOrder {
        Objects.requireNonNull(defender, "defender не может быть null: приказ адресуется конкретному духу");
        Objects.requireNonNull(directive, "directive не может быть null: тактика исполняет только явный приказ");
        Objects.requireNonNull(forceTargetId,
                "forceTargetId не может быть null: нет навязанной цели — Optional.empty()");
    }
}
