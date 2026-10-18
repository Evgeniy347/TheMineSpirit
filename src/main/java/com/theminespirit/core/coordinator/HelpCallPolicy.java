package com.theminespirit.core.coordinator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HelpCallPolicy {
    public List<AssistOrder> answer(List<HelpCall> calls, List<SpiritSnapshot> spirits, double helpRadius,
            int maxHelpers) {
        if (calls == null || spirits == null) {
            throw new IllegalArgumentException("HelpCallPolicy.answer: calls и spirits обязательны — "
                    + "без зова или без духов отвечать некому, пустые списки допустимы");
        }
        if (helpRadius < 0) {
            throw new IllegalArgumentException("HelpCallPolicy.answer: helpRadius = " + helpRadius
                    + ": радиус отклика не может быть отрицательным");
        }
        if (maxHelpers < 0) {
            throw new IllegalArgumentException("HelpCallPolicy.answer: maxHelpers = " + maxHelpers
                    + ": предел помощников не может быть отрицательным");
        }
        if (maxHelpers == 0) {
            return List.of();
        }
        List<AssistOrder> orders = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        for (HelpCall call : calls.stream()
                .sorted(Comparator.comparingLong(HelpCall::raisedAt).thenComparing(call -> call.caller()))
                .toList()) {
            List<SpiritSnapshot> helpers = spirits.stream()
                    .filter(spirit -> !spirit.id().equals(call.caller()))
                    .filter(spirit -> !used.contains(spirit.id()))
                    .filter(spirit -> !spirit.attacked())
                    .filter(spirit -> spirit.pos().distanceTo(call.pos()) <= helpRadius)
                    .sorted(Comparator.comparingDouble((SpiritSnapshot spirit) -> spirit.pos().distanceTo(call.pos()))
                            .thenComparing(SpiritSnapshot::id))
                    .limit(maxHelpers)
                    .toList();
            for (SpiritSnapshot helper : helpers) {
                used.add(helper.id());
                orders.add(new AssistOrder(helper.id(), call.caller(), true));
            }
        }
        return List.copyOf(orders);
    }
}
