package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;

public interface FarmView {
    FarmTile tileAt(BlockCoord ground);

    default int columnHeight(BlockCoord ground) {
        return 1;
    }

    default Optional<BlockCoord> fruitAt(BlockCoord ground, CropKind kind) {
        return Optional.empty();
    }

    default boolean hasFruitSpace(BlockCoord ground) {
        return true;
    }

    default Optional<BlockCoord> attachedCrop(BlockCoord support, CropKind kind) {
        return Optional.empty();
    }

    default Optional<BlockCoord> attachedSpot(BlockCoord support, CropKind kind) {
        return Optional.empty();
    }
}
