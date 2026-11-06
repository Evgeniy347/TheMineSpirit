package com.theminespirit.core.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.Set;
import java.util.function.Supplier;

public record DescentLevels(int widenWidth, int widenHeight, int stairWidth) {

    public static final String RESOURCE = "data/theminespirit/mining/descent_levels.json";
    public static final int MIN_WIDEN_WIDTH = 3;

    private static final Set<String> FIELDS = Set.of("widenWidth", "widenHeight", "stairWidth");
    private static final DescentLevels DEFAULTS = new DescentLevels(3, 6, 2);

    private static final TableSlot<DescentLevels> SOURCE = new TableSlot<>(DescentLevels::load);

    public DescentLevels {
        if (widenWidth < MIN_WIDEN_WIDTH || widenWidth % 2 == 0) {
            throw new IllegalArgumentException("Ширина расширения " + widenWidth + " обязана быть нечётной и не меньше "
                    + MIN_WIDEN_WIDTH + ": уровень 2 расширяет проход минимум до трёх блоков в ширину");
        }
        if (widenHeight < 1) {
            throw new IllegalArgumentException("Высота расширения " + widenHeight + " меньше единицы: расширять нечем");
        }
        if (stairWidth < 1 || stairWidth > widenWidth - 1) {
            throw new IllegalArgumentException("Ширина ступенек " + stairWidth + " обязана быть от 1 до "
                    + (widenWidth - 1) + ": полосу под будущие рельсы оставляют свободной");
        }
    }

    public static void useSource(Supplier<DescentLevels> supplier) {
        SOURCE.use(supplier);
    }

    public static DescentLevels defaults() {
        return DEFAULTS;
    }

    public static DescentLevels loadDefault() {
        return SOURCE.current();
    }

    public static DescentLevels load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static DescentLevels parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "descent_levels.json");
        int widenWidth = JsonResource.integer(root.get("widenWidth"), origin, "widenWidth");
        int widenHeight = JsonResource.integer(root.get("widenHeight"), origin, "widenHeight");
        int stairWidth = JsonResource.integer(root.get("stairWidth"), origin, "stairWidth");
        try {
            return new DescentLevels(widenWidth, widenHeight, stairWidth);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}
