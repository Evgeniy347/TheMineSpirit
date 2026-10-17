package com.theminespirit.core.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

public record ToolReserve(double fraction, int uses, boolean keepEnchantedLastUse) {
    public static final String RESOURCE = "data/theminespirit/mining/tool_replace.json";

    public static final double MAX_FRACTION = 0.5;
    public static final int MAX_USES = 64;

    private static final Set<String> FIELDS = Set.of("reserve_fraction", "reserve_uses", "keep_enchant_last_use");
    private static final ToolReserve DEFAULTS = new ToolReserve(0.05, 3, true);

    public ToolReserve {
        if (!(fraction >= 0.0) || fraction > MAX_FRACTION) {
            throw new IllegalArgumentException("Доля запаса прочности " + fraction + " вне [0, " + MAX_FRACTION
                    + "]: порог выше половины прочности заставил бы духа менять почти новый инструмент");
        }
        if (uses < 0 || uses > MAX_USES) {
            throw new IllegalArgumentException("Запас прочности в ударах " + uses + " вне [0, " + MAX_USES
                    + "]: столько ударов дух не тратит перед заменой инструмента");
        }
    }

    public static ToolReserve defaults() {
        return DEFAULTS;
    }

    public boolean spent(int remaining, int max, boolean enchanted) {
        if (max <= 0) {
            return false;
        }
        if (remaining <= 0) {
            return true;
        }
        if (enchanted && keepEnchantedLastUse && remaining <= 1) {
            return true;
        }
        int fractionCap = (int) Math.ceil(fraction * max);
        return remaining <= Math.max(uses, fractionCap);
    }

    public static ToolReserve load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static ToolReserve parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "tool_replace.json");
        double fraction = JsonResource.decimal(root.get("reserve_fraction"), origin, "reserve_fraction");
        int uses = JsonResource.integer(root.get("reserve_uses"), origin, "reserve_uses");
        boolean keepLast = booleanField(root.get("keep_enchant_last_use"), origin);
        try {
            return new ToolReserve(fraction, uses, keepLast);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static boolean booleanField(JsonNode node, String origin) {
        if (node == null || !node.isBoolean()) {
            throw new IllegalStateException("Файл '" + origin + "': у keep_enchant_last_use ожидается true или false: "
                    + "без флага неизвестно, тратить ли последний удар зачарованного инструмента");
        }
        return node.asBoolean();
    }
}
