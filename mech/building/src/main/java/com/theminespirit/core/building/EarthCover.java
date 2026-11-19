package com.theminespirit.core.building;

import java.util.List;
import java.util.Objects;

public final class EarthCover {
    public static final String DIRT = "dirt";
    public static final String GRASS = "grass_block";

    private EarthCover() {}

    public static boolean earth(String block) {
        Objects.requireNonNull(block, "Блок земли не может быть null: без него не отличить грунт");
        String path = SchematicBlocks.path(block);
        return DIRT.equals(path) || GRASS.equals(path);
    }

    public static boolean covers(String actual, String wanted) {
        Objects.requireNonNull(actual, "Стоящий блок не может быть null: сверяется клетка мира");
        Objects.requireNonNull(wanted, "Блок схемы не может быть null: сверяется клетка схемы");
        return earth(actual) && earth(wanted) && !SchematicBlocks.path(actual).equals(SchematicBlocks.path(wanted));
    }

    public static String restored(String block) {
        Objects.requireNonNull(block, "Блок пролома не может быть null: без него неизвестно, что вернуть");
        if (!earth(block) || DIRT.equals(SchematicBlocks.path(block))) {
            return block;
        }
        return "minecraft:" + DIRT;
    }

    public static String normalized(String item) {
        Objects.requireNonNull(item, "Предмет земли не может быть null: без него не свести дёрн с землёй");
        String path = SchematicBlocks.path(item);
        return GRASS.equals(path) ? DIRT : path;
    }

    public static boolean same(String first, String second) {
        Objects.requireNonNull(first, "Первый предмет сверки не может быть null: иначе не с чем сравнивать");
        Objects.requireNonNull(second, "Второй предмет сверки не может быть null: иначе не с чем сравнивать");
        return normalized(first).equals(normalized(second));
    }

    public static List<String> ids(String item) {
        Objects.requireNonNull(item, "Предмет земли не может быть null: без него не выбрать, что брать");
        if (!earth(item)) {
            return List.of(item);
        }
        int colon = item.indexOf(':');
        String prefix = colon < 0 ? "minecraft:" : item.substring(0, colon + 1);
        return List.of(prefix + DIRT, prefix + GRASS);
    }
}
