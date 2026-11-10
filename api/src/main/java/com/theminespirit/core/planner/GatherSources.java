package com.theminespirit.core.planner;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.forestry.TreeSpecies;
import java.util.Objects;
import java.util.Optional;

public final class GatherSources {
    private static final String MINECRAFT = "minecraft:";

    private GatherSources() {}

    public static Optional<String> sourceBlock(String item) {
        Objects.requireNonNull(item, "Предмет не может быть null: без него источник не найти");
        String qualified = qualify(item);
        Optional<TreeSpecies> tree = TreeSpecies.ofSapling(qualified);
        if (tree.isPresent()) {
            return Optional.of(tree.orElseThrow().leavesId());
        }
        Optional<CropKind> crop = CropKind.ofProduce(item);
        if (crop.isPresent()) {
            return Optional.of(blockOf(crop.orElseThrow()));
        }
        return Optional.empty();
    }

    private static String blockOf(CropKind crop) {
        return crop.hasFruit() ? crop.fruitBlockId() : crop.blockId();
    }

    private static String qualify(String item) {
        return item.indexOf(':') < 0 ? MINECRAFT + item : item;
    }
}
