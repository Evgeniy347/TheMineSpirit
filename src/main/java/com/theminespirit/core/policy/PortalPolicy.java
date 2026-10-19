package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public final class PortalPolicy {
    private PortalPolicy() {}

    public static PolicyVerdict evaluate(LocalSurroundings surroundings, PolicyExemptions exemptions) {
        Objects.requireNonNull(surroundings, "Снимок не может быть null: по нему решается, стоит ли дух в портале");
        Objects.requireNonNull(exemptions, "Исключения не могут быть null: пустые задаются PolicyExemptions.empty()");
        boolean inPortal = surroundings.cellAt(0, 0, 0) == Cell.PORTAL || surroundings.cellAt(0, 1, 0) == Cell.PORTAL;
        if (!inPortal || exemptions.has(PolicyId.PORTAL)) {
            return new PolicyVerdict.Clear();
        }
        Optional<BlockCoord> exit = NearestExit.find(surroundings, surroundings);
        if (exit.isPresent()) {
            return new PolicyVerdict.Reflex(
                    PolicyId.PORTAL, new ReflexAction.StepOutOfPortal(exit.get()), "дух стоит в клетке портала");
        }
        return new PolicyVerdict.Reflex(
                PolicyId.PORTAL, new ReflexAction.StopMoving(), "дух стоит в клетке портала, выхода рядом нет");
    }

    public static boolean vetoesTarget(LocalSurroundings surroundings, BlockCoord target, PolicyExemptions exemptions) {
        Objects.requireNonNull(surroundings, "Снимок не может быть null: по нему решается, лежит ли цель в портале");
        Objects.requireNonNull(target, "Цель не может быть null: вето проверяет клетку, куда дух идёт");
        Objects.requireNonNull(exemptions, "Исключения не могут быть null: пустые задаются PolicyExemptions.empty()");
        if (exemptions.has(PolicyId.PORTAL)) {
            return false;
        }
        BlockCoord head = new BlockCoord(target.x(), target.y() + 1, target.z());
        return surroundings.cellAt(target) == Cell.PORTAL || surroundings.cellAt(head) == Cell.PORTAL;
    }
}
