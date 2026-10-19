package com.theminespirit.core.doors;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

public final class DoorTuning {
    public static final String RESOURCE = "data/theminespirit/doors/doors.json";

    private static final Set<String> FIELDS =
            Set.of("eveningDayFraction", "closeAfterOpenTicks", "playerNearbyRadius", "settlementRadius",
                    "recheckIntervalTicks");

    private DoorTuning() {
    }

    public static DoorRules loadDefault() {
        return DefaultHolder.INSTANCE;
    }

    public static DoorRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "doors.json");
        double eveningDayFraction = JsonResource.decimal(root.get("eveningDayFraction"), origin, "eveningDayFraction");
        int closeAfterOpenTicks = JsonResource.integer(root.get("closeAfterOpenTicks"), origin, "closeAfterOpenTicks");
        double playerNearbyRadius = JsonResource.decimal(root.get("playerNearbyRadius"), origin, "playerNearbyRadius");
        int settlementRadius = JsonResource.integer(root.get("settlementRadius"), origin, "settlementRadius");
        int recheckIntervalTicks = JsonResource.integer(root.get("recheckIntervalTicks"), origin, "recheckIntervalTicks");
        try {
            return new DoorRules(eveningDayFraction, closeAfterOpenTicks, playerNearbyRadius, settlementRadius,
                    recheckIntervalTicks);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static final class DoorRules {
        private final double eveningDayFraction;
        private final int closeAfterOpenTicks;
        private final double playerNearbyRadius;
        private final int settlementRadius;
        private final int recheckIntervalTicks;

        public DoorRules(double eveningDayFraction, int closeAfterOpenTicks, double playerNearbyRadius,
                int settlementRadius, int recheckIntervalTicks) {
            if (eveningDayFraction < 0.0 || eveningDayFraction > 1.0) {
                throw new IllegalArgumentException(
                        "eveningDayFraction = " + eveningDayFraction + ": порог вечера обязан лежать в 0..1");
            }
            if (closeAfterOpenTicks <= 0) {
                throw new IllegalArgumentException(
                        "closeAfterOpenTicks = " + closeAfterOpenTicks + ": ожидание закрытия обязано быть положительным");
            }
            if (playerNearbyRadius <= 0.0) {
                throw new IllegalArgumentException(
                        "playerNearbyRadius = " + playerNearbyRadius + ": радиус игрока рядом обязан быть положительным");
            }
            if (settlementRadius <= 0) {
                throw new IllegalArgumentException(
                        "settlementRadius = " + settlementRadius + ": радиус поселения обязан быть положительным");
            }
            if (recheckIntervalTicks <= 0) {
                throw new IllegalArgumentException(
                        "recheckIntervalTicks = " + recheckIntervalTicks + ": период пересмотра обязан быть положительным");
            }
            this.eveningDayFraction = eveningDayFraction;
            this.closeAfterOpenTicks = closeAfterOpenTicks;
            this.playerNearbyRadius = playerNearbyRadius;
            this.settlementRadius = settlementRadius;
            this.recheckIntervalTicks = recheckIntervalTicks;
        }

        public double eveningDayFraction() {
            return eveningDayFraction;
        }

        public int closeAfterOpenTicks() {
            return closeAfterOpenTicks;
        }

        public double playerNearbyRadius() {
            return playerNearbyRadius;
        }

        public int settlementRadius() {
            return settlementRadius;
        }

        public int recheckIntervalTicks() {
            return recheckIntervalTicks;
        }
    }

    private static final class DefaultHolder {
        private static final DoorRules INSTANCE = readDefault();

        private static DoorRules readDefault() {
            return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
        }
    }
}
