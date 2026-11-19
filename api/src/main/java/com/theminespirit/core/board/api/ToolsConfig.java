package com.theminespirit.core.board.api;

import com.theminespirit.core.base.NamespacedId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ToolsConfig(List<String> set, String tier, List<String> repairTiers) {
    public static final String PICKAXE = "_pickaxe";
    public static final String AXE = "_axe";
    public static final List<String> KINDS = List.of("pickaxe", "axe", "shovel", "sword");
    public static final List<String> TIERS = List.of("wooden", "stone", "golden", "iron", "diamond", "netherite");
    public static final List<String> TARGET_TIERS = List.of("stone", "iron", "diamond");

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
        return NamespacedId.MINECRAFT + tier + "_" + kind;
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
        return itemId.startsWith(NamespacedId.MINECRAFT) ? itemId.substring(NamespacedId.MINECRAFT.length()) : itemId;
    }
}
