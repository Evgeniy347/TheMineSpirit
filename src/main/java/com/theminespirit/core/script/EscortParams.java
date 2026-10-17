package com.theminespirit.core.script;

import com.theminespirit.core.tactics.TargetSelector;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EscortParams(String target, int radius, int untilTicks) {
    public EscortParams {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задана цель escort: без цели скрипт не знает, кого сопровождать и охранять");
        }
        if (radius < CatalogLimits.FOLLOW_RADIUS_MIN || radius > CatalogLimits.FOLLOW_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус сопровождения " + radius + " вне диапазона ["
                    + CatalogLimits.FOLLOW_RADIUS_MIN + ", " + CatalogLimits.FOLLOW_RADIUS_MAX
                    + "]: дух держится рядом с целью в этих пределах");
        }
        if (untilTicks < CatalogLimits.ESCORT_UNTIL_MIN || untilTicks > CatalogLimits.ESCORT_UNTIL_MAX) {
            throw new IllegalArgumentException("Срок сопровождения " + untilTicks + " вне диапазона ["
                    + CatalogLimits.ESCORT_UNTIL_MIN + ", " + CatalogLimits.ESCORT_UNTIL_MAX
                    + "]: ноль означает «до команды стоп», больше нуля — предел в тиках");
        }
    }

    public static EscortParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new EscortParams(
                ScriptArgs.requireString(params, "target"),
                ScriptArgs.optionalInt(params, "radius").orElse(CatalogLimits.FOLLOW_RADIUS_DEFAULT),
                ScriptArgs.optionalInt(params, "until").orElse(CatalogLimits.ESCORT_UNTIL_DEFAULT));
    }

    public boolean untilStop() {
        return untilTicks == CatalogLimits.ESCORT_UNTIL_DEFAULT;
    }

    public TargetSelector.Target selector() {
        String text = target.trim();
        if (text.startsWith("player:") || text.startsWith("spirit:")) {
            return TargetSelector.parse(text);
        }
        try {
            UUID.fromString(text);
            return TargetSelector.parse("spirit:" + text);
        } catch (IllegalArgumentException notUuid) {
            return TargetSelector.parse("player:" + text);
        }
    }
}
