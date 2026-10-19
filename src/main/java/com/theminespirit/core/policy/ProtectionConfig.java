package com.theminespirit.core.policy;

import com.theminespirit.core.reload.DataTables;
import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Set;

public final class ProtectionConfig {
    public static final String RESOURCE = "data/theminespirit/policy/protection.json";

    private static final Set<String> FIELDS = Set.of("deny", "natural", "buildingThreshold", "maxUnprotectVolume");

    private ProtectionConfig() {
    }

    public static BlockProtection load() {
        return DataTables.INSTANCE.protection().current();
    }

    public static BlockProtection readClasspath() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static BlockProtection parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "protection.json");
        List<String> deny = JsonResource.strings(root.get("deny"), origin, "deny");
        List<String> natural = JsonResource.strings(root.get("natural"), origin, "natural");
        int threshold = JsonResource.integer(root.get("buildingThreshold"), origin, "buildingThreshold");
        int maxUnprotectVolume = JsonResource.integer(root.get("maxUnprotectVolume"), origin, "maxUnprotectVolume");
        IdFilter denyFilter = filter(deny, origin, "deny");
        IdFilter naturalFilter = filter(natural, origin, "natural");
        BlockProtection protection;
        try {
            protection = new BlockProtection(denyFilter, naturalFilter, threshold, maxUnprotectVolume);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
        rejectOverlap(denyFilter, naturalFilter, origin);
        return protection;
    }

    private static IdFilter filter(List<String> patterns, String origin, String what) {
        try {
            return IdFilter.of(patterns);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': в списке " + what + " неверный шаблон: " + error.getMessage(), error);
        }
    }

    private static void rejectOverlap(IdFilter deny, IdFilter natural, String origin) {
        for (String pattern : deny.patterns()) {
            boolean sameText = natural.patterns().contains(pattern);
            if (sameText || (!IdFilter.isPattern(pattern) && natural.matches(pattern))) {
                throw overlap(origin, pattern);
            }
        }
        for (String pattern : natural.patterns()) {
            if (!IdFilter.isPattern(pattern) && deny.matches(pattern)) {
                throw overlap(origin, pattern);
            }
        }
    }

    private static IllegalStateException overlap(String origin, String pattern) {
        return new IllegalStateException("Файл '" + origin + "': шаблон '" + pattern + "' попал и в deny, и в natural: блок не может быть "
                + "одновременно защищённым и природным. Уберите шаблон из одного списка");
    }
}
