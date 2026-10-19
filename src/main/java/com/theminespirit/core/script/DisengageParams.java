package com.theminespirit.core.script;

import com.theminespirit.core.tactics.TargetSelector;
import java.util.Map;
import java.util.Objects;

public record DisengageParams(String target, int distance) {
    public static final String DEFAULT_TARGET = "hostile";

    public DisengageParams {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задана цель disengage: без цели дух не знает, от кого отходить " + "и чей бой прекращать");
        }
        if (distance < CatalogLimits.DISENGAGE_DISTANCE_MIN || distance > CatalogLimits.DISENGAGE_DISTANCE_MAX) {
            throw new IllegalArgumentException("Дистанция выхода из боя " + distance + " вне диапазона ["
                    + CatalogLimits.DISENGAGE_DISTANCE_MIN + ", " + CatalogLimits.DISENGAGE_DISTANCE_MAX
                    + "]: disengage отходит недалеко, иначе это уже flee или retreat");
        }
        TargetSelector.parse(target);
    }

    public TargetSelector.Target selector() {
        return TargetSelector.parse(target);
    }

    public static DisengageParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new DisengageParams(
                ScriptArgs.optionalString(params, "target", DEFAULT_TARGET),
                ScriptArgs.optionalInt(params, "distance").orElse(CatalogLimits.DISENGAGE_DISTANCE_DEFAULT));
    }
}
