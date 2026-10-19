package com.theminespirit.core.pickup;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Objects;
import java.util.Set;

/**
 * Магнит дропа (упрощение 44.1, согласовано владельцем 18.10): раз в {@code intervalTicks} свободные предметы в
 * радиусе {@code radius} сами попадают в инвентарь духа. Чужое не берётся: предмет, брошенный игроком недавно,
 * адресованный другому, забронированный другим духом, свой выброшенный и ещё не подбираемый по ванили.
 * Числа — горячая таблица {@code pickup/magnet.json}.
 */
public record MagnetRules(double radius, int intervalTicks, int playerDropDelayTicks) {
    public static final String RESOURCE = "data/theminespirit/pickup/magnet.json";
    private static final Set<String> FIELDS = Set.of("radius", "intervalTicks", "playerDropDelayTicks");

    public record Drop(
            int ageTicks,
            boolean pickupDelay,
            boolean thrownByPlayer,
            boolean thrownBySelf,
            boolean addressedToOther,
            boolean reservedByOther) {}

    public MagnetRules {
        if (radius <= 0 || intervalTicks < 1 || playerDropDelayTicks < 0) {
            throw new IllegalArgumentException("Магнит: radius = " + radius + ", intervalTicks = " + intervalTicks
                    + ", playerDropDelayTicks = " + playerDropDelayTicks
                    + ": радиус больше нуля, период хотя бы 1 тик, задержка не отрицательная");
        }
    }

    public boolean attracts(Drop drop) {
        Objects.requireNonNull(drop, "Предмет не может быть null: без него нечего притягивать");
        if (drop.pickupDelay() || drop.addressedToOther() || drop.reservedByOther()) {
            return false;
        }
        if (drop.thrownBySelf() && drop.ageTicks() < PickupTuning.OWN_DROP_SKIP_TICKS) {
            return false;
        }
        return !drop.thrownByPlayer() || drop.ageTicks() >= playerDropDelayTicks;
    }

    public static MagnetRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "magnet.json");
        try {
            return new MagnetRules(
                    JsonResource.decimal(root.get("radius"), origin, "radius"),
                    JsonResource.integer(root.get("intervalTicks"), origin, "intervalTicks"),
                    JsonResource.integer(root.get("playerDropDelayTicks"), origin, "playerDropDelayTicks"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static MagnetRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
