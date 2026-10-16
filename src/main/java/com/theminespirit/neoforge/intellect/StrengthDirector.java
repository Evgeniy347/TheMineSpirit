package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.intellect.StrengthVerdict;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;

public final class StrengthDirector {
    private StrengthDirector() {
    }

    public static void apply(SpiritEntity spirit, StrengthVerdict verdict, TaskStarter starter, long tick,
            boolean homed) {
        if (spirit == null || verdict == null) {
            throw new IllegalArgumentException(
                    "Вердикт без духа не применить: бой ведут конкретные духи");
        }
        if (starter == null) {
            throw new IllegalArgumentException(
                    "Вердикт без стартера задач не применить: поход за оружием идёт через общий откат провалов");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Вердикт в тике " + tick + ": время не может быть отрицательным");
        }
        if (spirit.isRemoved()) {
            return;
        }
        switch (verdict.action()) {
            case FLEE, HEAL -> {
            }
            case ARM -> {
                if (!homed) {
                    return;
                }
                var current = spirit.currentTaskScript();
                if (current.isPresent()
                        && (current.get().equals("withdraw") || current.get().equals("equip"))) {
                    return;
                }
                starter.start(spirit, "withdraw", new SequenceNode(List.of(
                        new ScriptCall("withdraw", Map.of("container", "nearest", "item", "*_sword",
                                "count", 1, "radius", 8)),
                        new ScriptCall("equip", Map.of("item", "minecraft:iron_sword")))), tick);
            }
            case FIGHT -> {
                if (!homed) {
                    return;
                }
                if (spirit.tactics().activeDirective().isEmpty() && spirit.currentTaskScript().isEmpty()) {
                    spirit.tactics().applyDirective(
                            CombatDirective.attack(TargetSelector.parse("hostile")));
                }
            }
        }
    }
}
