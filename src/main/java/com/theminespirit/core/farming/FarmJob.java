package com.theminespirit.core.farming;

import java.util.Objects;

public enum FarmJob {
    TILL,
    PLANT,
    HARVEST,
    BONE_MEAL;

    public boolean eligible(FarmTile tile, CropKind filter, boolean watered) {
        Objects.requireNonNull(tile, "Клетка не может быть null: без неё неизвестно, подходит ли она под работу");
        Objects.requireNonNull(filter, "Фильтр вида не может быть null: «любой вид» задают CropKind.ANY");
        return switch (this) {
            case TILL -> isTillable(tile.ground()) && isClearForTill(tile.above()) && watered;
            case PLANT -> tile.ground() == Ground.FARMLAND && tile.above() == Above.AIR;
            case HARVEST -> tile.above() == Above.CROP && tile.crop().isPresent()
                    && matches(tile.crop().orElseThrow().kind(), filter)
                    && tile.crop().orElseThrow().isMature();
            case BONE_MEAL -> tile.above() == Above.CROP && tile.crop().isPresent()
                    && matches(tile.crop().orElseThrow().kind(), filter)
                    && !tile.crop().orElseThrow().isMature();
        };
    }

    private static boolean isTillable(Ground ground) {
        return ground == Ground.DIRT || ground == Ground.GRASS || ground == Ground.PATH;
    }

    private static boolean isClearForTill(Above above) {
        return above == Above.AIR || above == Above.WEED;
    }

    private static boolean matches(CropKind kind, CropKind filter) {
        return filter == CropKind.ANY || kind == filter;
    }
}
