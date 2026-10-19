package com.theminespirit.core.mining;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public enum OreKind {
    ANY(),
    COAL("minecraft:coal_ore", "minecraft:deepslate_coal_ore"),
    IRON("minecraft:iron_ore", "minecraft:deepslate_iron_ore"),
    COPPER("minecraft:copper_ore", "minecraft:deepslate_copper_ore"),
    GOLD("minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore"),
    REDSTONE("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"),
    LAPIS("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore"),
    DIAMOND("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"),
    EMERALD("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"),
    QUARTZ("minecraft:nether_quartz_ore"),
    NETHERITE("minecraft:ancient_debris");

    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    private static final List<String> NAMES = Arrays.stream(values())
            .map(kind -> kind.name().toLowerCase(Locale.ROOT))
            .toList();
    private static final Set<String> ALL_BLOCKS = collectAllBlocks();

    private final Set<String> ownBlocks;

    OreKind(String... blockIds) {
        this.ownBlocks = Set.of(blockIds);
    }

    public static List<String> names() {
        return NAMES;
    }

    public static Set<String> blocks(OreKind kind) {
        Objects.requireNonNull(kind, "Вид руды не может быть null: по нему берётся набор блоков");
        return kind == ANY ? ALL_BLOCKS : kind.ownBlocks;
    }

    public static OreKind parse(String kind) {
        Objects.requireNonNull(kind, "Вид руды не может быть null: без него неизвестно, какую руду добывать");
        try {
            return valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Неизвестный вид руды '" + kind + "': допустимы " + NAMES
                            + ", сверьте значение с каталогом /v1/capabilities",
                    error);
        }
    }

    public static Optional<OreKind> ofBlock(String blockId) {
        Objects.requireNonNull(blockId, "id блока не может быть null: по нему ищется вид руды");
        String canonical = canonicalId(blockId);
        for (OreKind kind : values()) {
            if (kind != ANY && kind.ownBlocks.contains(canonical)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private static String canonicalId(String blockId) {
        return blockId.indexOf(':') < 0 ? MINECRAFT_NAMESPACE + blockId : blockId;
    }

    private static Set<String> collectAllBlocks() {
        Set<String> union = new LinkedHashSet<>();
        for (OreKind kind : values()) {
            union.addAll(kind.ownBlocks);
        }
        return Set.copyOf(union);
    }
}
