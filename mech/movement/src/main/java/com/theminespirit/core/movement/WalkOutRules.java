package com.theminespirit.core.movement;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.Set;
import java.util.function.Supplier;

public record WalkOutRules(int surfaceSpread) {
    public static final String RESOURCE = "data/theminespirit/movement/walkout.json";
    private static final int MAX_SURFACE_SPREAD = 64;
    private static final Set<String> FIELDS = Set.of("surfaceSpread");
    private static final TableSlot<WalkOutRules> SOURCE = new TableSlot<>(WalkOutRules::load);

    public WalkOutRules {
        if (surfaceSpread < PitWalkOut.EXIT_RADIUS || surfaceSpread > MAX_SURFACE_SPREAD) {
            throw new IllegalArgumentException("Область проверки поверхности " + surfaceSpread + " вне ["
                    + PitWalkOut.EXIT_RADIUS + ", " + MAX_SURFACE_SPREAD
                    + "]: уже радиуса выхода область не увидит край, а огромная превратит поиск в обход мира");
        }
    }

    public static WalkOutRules defaults() {
        return new WalkOutRules(32);
    }

    public static WalkOutRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "walkout.json");
        try {
            return new WalkOutRules(JsonResource.integer(root.get("surfaceSpread"), origin, "surfaceSpread"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static WalkOutRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static WalkOutRules current() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<WalkOutRules> supplier) {
        SOURCE.use(supplier);
    }
}
