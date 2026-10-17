package com.theminespirit.core.farming;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public enum CropKind {
    ANY(),
    WHEAT("minecraft:wheat", 7, "wheat_seeds", "wheat", HarvestMethod.BREAK),
    CARROT("minecraft:carrots", 7, "carrot", "carrot", HarvestMethod.BREAK),
    POTATO("minecraft:potatoes", 7, "potato", "potato", HarvestMethod.BREAK),
    BEETROOT("minecraft:beetroots", 3, "beetroot_seeds", "beetroot", HarvestMethod.BREAK),
    SWEET_BERRY_BUSH("minecraft:sweet_berry_bush", 3, "sweet_berries", "sweet_berries",
            HarvestMethod.PICK);

    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    private static final List<String> NAMES = Arrays.stream(values())
            .map(kind -> kind.name().toLowerCase(Locale.ROOT))
            .toList();
    private static final List<String> PLANTABLE_NAMES = Arrays.stream(values())
            .filter(kind -> kind != ANY)
            .map(kind -> kind.name().toLowerCase(Locale.ROOT))
            .toList();

    private final String blockId;
    private final int maxAge;
    private final String plantItem;
    private final String produceItem;
    private final HarvestMethod harvestMethod;

    CropKind() {
        this.blockId = null;
        this.maxAge = -1;
        this.plantItem = null;
        this.produceItem = null;
        this.harvestMethod = HarvestMethod.BREAK;
    }

    CropKind(String blockId, int maxAge, String plantItem, String produceItem, HarvestMethod harvestMethod) {
        this.blockId = blockId;
        this.maxAge = maxAge;
        this.plantItem = plantItem;
        this.produceItem = produceItem;
        this.harvestMethod = harvestMethod;
    }

    public static List<String> names() {
        return NAMES;
    }

    public static List<String> plantableNames() {
        return PLANTABLE_NAMES;
    }

    public String blockId() {
        if (this == ANY) {
            throw new IllegalArgumentException(
                    "У any нет блока посева: any означает «любой вид», а не конкретный блок грядки");
        }
        return blockId;
    }

    public int maxAge() {
        if (this == ANY) {
            throw new IllegalArgumentException(
                    "У any нет максимального возраста: any означает «любой вид», зрелость считается по виду клетки");
        }
        return maxAge;
    }

    public String plantItem() {
        if (this == ANY) {
            throw new IllegalArgumentException(
                    "У any нет посадочного предмета: any означает «любой вид», сажают конкретный вид");
        }
        return plantItem;
    }

    public String produceItem() {
        if (this == ANY) {
            throw new IllegalArgumentException(
                    "У any нет урожая: any означает «любой вид», урожай даёт конкретный вид");
        }
        return produceItem;
    }

    public HarvestMethod harvestMethod() {
        if (this == ANY) {
            throw new IllegalArgumentException(
                    "У any нет способа сбора: any означает «любой вид», а собирают конкретный");
        }
        return harvestMethod;
    }

    public static CropKind parse(String kind) {
        Objects.requireNonNull(kind, "Вид культуры не может быть null: без него неизвестно, что сажать и собирать");
        try {
            return valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Неизвестный вид культуры '" + kind + "': допустимы " + NAMES
                    + ", сверьте значение с каталогом /v1/capabilities", error);
        }
    }

    public static Optional<CropKind> ofBlock(String blockId) {
        Objects.requireNonNull(blockId, "id блока не может быть null: по нему ищется вид культуры");
        String canonical = canonicalId(blockId);
        for (CropKind kind : values()) {
            if (kind != ANY && kind.blockId.equals(canonical)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private static String canonicalId(String blockId) {
        return blockId.indexOf(':') < 0 ? MINECRAFT_NAMESPACE + blockId : blockId;
    }
}
