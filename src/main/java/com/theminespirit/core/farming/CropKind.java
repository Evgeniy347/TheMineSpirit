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
            HarvestMethod.PICK),
    SUGAR_CANE("minecraft:sugar_cane", 15, "sugar_cane", "sugar_cane", HarvestMethod.BREAK_COLUMN),
    BAMBOO("minecraft:bamboo", 1, "bamboo", "bamboo", HarvestMethod.BREAK_COLUMN),
    CACTUS("minecraft:cactus", 15, "cactus", "cactus", HarvestMethod.BREAK_COLUMN),
    MELON("minecraft:melon_stem", 7, "melon_seeds", "melon_slice", HarvestMethod.BREAK_FRUIT,
            "minecraft:melon"),
    PUMPKIN("minecraft:pumpkin_stem", 7, "pumpkin_seeds", "pumpkin", HarvestMethod.BREAK_FRUIT,
            "minecraft:pumpkin"),
    COCOA("minecraft:cocoa", 2, "cocoa_beans", "cocoa_beans", HarvestMethod.BREAK, null,
            "minecraft:jungle_log");

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
    private final String fruitBlockId;
    private final String supportBlockId;

    CropKind() {
        this(null, -1, null, null, HarvestMethod.BREAK, null, null);
    }

    CropKind(String blockId, int maxAge, String plantItem, String produceItem, HarvestMethod harvestMethod) {
        this(blockId, maxAge, plantItem, produceItem, harvestMethod, null, null);
    }

    CropKind(String blockId, int maxAge, String plantItem, String produceItem, HarvestMethod harvestMethod,
            String fruitBlockId) {
        this(blockId, maxAge, plantItem, produceItem, harvestMethod, fruitBlockId, null);
    }

    CropKind(String blockId, int maxAge, String plantItem, String produceItem, HarvestMethod harvestMethod,
            String fruitBlockId, String supportBlockId) {
        this.blockId = blockId;
        this.maxAge = maxAge;
        this.plantItem = plantItem;
        this.produceItem = produceItem;
        this.harvestMethod = harvestMethod;
        this.fruitBlockId = fruitBlockId;
        this.supportBlockId = supportBlockId;
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

    public boolean hasFruit() {
        return fruitBlockId != null;
    }

    public String fruitBlockId() {
        if (fruitBlockId == null) {
            throw new IllegalArgumentException("У вида " + this + " нет плода на соседней клетке: "
                    + "плод есть только у культур метода BREAK_FRUIT, сверь harvestMethod в CropKind");
        }
        return fruitBlockId;
    }

    public boolean hasSupport() {
        return supportBlockId != null;
    }

    public String supportBlockId() {
        if (supportBlockId == null) {
            throw new IllegalArgumentException("У вида " + this + " нет блока опоры: "
                    + "на опоре растут только культуры вроде какао, сверь supportBlockId в CropKind");
        }
        return supportBlockId;
    }

    public static Optional<CropKind> ofSupportBlock(String blockId) {
        Objects.requireNonNull(blockId, "id блока не может быть null: по нему ищется вид культуры на опоре");
        String canonical = canonicalId(blockId);
        for (CropKind kind : values()) {
            if (kind.supportBlockId != null && kind.supportBlockId.equals(canonical)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
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
