package com.theminespirit.core.planner.api;

import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.forestry.api.TreeSpecies;
import java.util.Objects;
import java.util.Optional;

public final class GatherSources {

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
        return item.indexOf(':') < 0 ? NamespacedId.MINECRAFT + item : item;
    }
}
