package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record ToolsConfig(List<String> set, String tier, List<String> repairTiers) {
    public static final List<String> KINDS = List.of("pickaxe", "axe", "shovel", "sword");
    public static final List<String> TIERS = List.of("wooden", "stone", "golden", "iron", "diamond", "netherite");
    public static final List<String> TARGET_TIERS = List.of("stone", "iron", "diamond");

    private static final Set<String> FIELDS = Set.of("set", "tier", "repairTiers");
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final ToolsConfig DEFAULTS =
            new ToolsConfig(List.of("pickaxe", "axe", "shovel"), "stone", List.of("iron", "diamond"));

    public ToolsConfig {
        Objects.requireNonNull(set, "Набор инструментов не может быть null: пустой задают пустым списком");
        set = List.copyOf(set);
        if (set.isEmpty()) {
            throw new IllegalArgumentException(
                    "Набор инструментов пуст: поселение без набора не крафтит ни одного инструмента");
        }
        for (String kind : set) {
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException("Вид инструмента '" + kind + "' неизвестен: допустимы " + KINDS);
            }
        }
        if (new HashSet<>(set).size() != set.size()) {
            throw new IllegalArgumentException("Набор инструментов повторяет вид: " + set);
        }
        if (!TARGET_TIERS.contains(tier)) {
            throw new IllegalArgumentException("Уровень набора '" + tier + "' неизвестен: допустимы " + TARGET_TIERS);
        }
        Objects.requireNonNull(
                repairTiers, "Уровни ремонта не могут быть null: ремонт без уровней задают пустым списком");
        repairTiers = List.copyOf(repairTiers);
        for (String repairTier : repairTiers) {
            if (!TIERS.contains(repairTier)) {
                throw new IllegalArgumentException(
                        "Уровень ремонта '" + repairTier + "' неизвестен: допустимы " + TIERS);
            }
        }
    }

    public static ToolsConfig defaults() {
        return DEFAULTS;
    }

    public static ToolsConfig fromJson(@Nullable JsonNode node, String origin) {
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение об ошибке называет файл");
        if (node == null || node.isNull()) {
            return DEFAULTS;
        }
        if (!node.isObject()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': поле 'tools' — не объект, ожидается " + "«set → tier → repairTiers»");
        }
        JsonResource.checkFields(node, FIELDS, origin, "поля 'tools'");
        List<String> set =
                node.has("set") ? JsonResource.strings(node.get("set"), origin, "поля 'tools.set'") : DEFAULTS.set();
        String tier =
                node.has("tier") ? JsonResource.text(node.get("tier"), origin, "поля 'tools.tier'") : DEFAULTS.tier();
        List<String> repairTiers = node.has("repairTiers")
                ? JsonResource.strings(node.get("repairTiers"), origin, "поля 'tools.repairTiers'")
                : DEFAULTS.repairTiers();
        try {
            return new ToolsConfig(set, tier, repairTiers);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public ToolFate fate(String itemId, boolean damagedEnoughToRepair) {
        Objects.requireNonNull(itemId, "Предмет не может быть null: судьба решается по имени инструмента");
        if (!damagedEnoughToRepair) {
            return ToolFate.KEEP;
        }
        Optional<String> kind = kindOf(itemId);
        if (kind.isEmpty() || !KINDS.contains(kind.orElseThrow())) {
            return ToolFate.KEEP;
        }
        Optional<String> itemTier = tierOf(itemId);
        return itemTier.isPresent() && repairTiers.contains(itemTier.orElseThrow())
                ? ToolFate.REPAIR
                : ToolFate.REPLACE;
    }

    public static Optional<String> kindOf(String itemId) {
        return partAfterLastUnderscore(itemId);
    }

    public static Optional<String> tierOf(String itemId) {
        String path = stripPrefix(itemId);
        int split = path.lastIndexOf('_');
        if (split <= 0) {
            return Optional.empty();
        }
        String tier = path.substring(0, split);
        return TIERS.contains(tier) ? Optional.of(tier) : Optional.empty();
    }

    public static int rank(String tier) {
        int rank = TIERS.indexOf(tier);
        if (rank < 0) {
            throw new IllegalArgumentException("Уровень инструмента '" + tier + "' неизвестен: допустимы " + TIERS);
        }
        return rank;
    }

    public static String itemId(String tier, String kind) {
        return MINECRAFT_PREFIX + tier + "_" + kind;
    }

    private static Optional<String> partAfterLastUnderscore(String itemId) {
        String path = stripPrefix(itemId);
        int split = path.lastIndexOf('_');
        if (split < 0 || split == path.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(path.substring(split + 1));
    }

    private static String stripPrefix(String itemId) {
        return itemId.startsWith(MINECRAFT_PREFIX) ? itemId.substring(MINECRAFT_PREFIX.length()) : itemId;
    }
}
