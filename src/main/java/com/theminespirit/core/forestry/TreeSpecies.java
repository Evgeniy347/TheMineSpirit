package com.theminespirit.core.forestry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TreeSpecies(String id, String logId, String leavesId, String saplingId) {
    public static final String ANY = "any";

    public static final List<TreeSpecies> ALL = List.of(
            new TreeSpecies("oak", "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:oak_sapling"),
            new TreeSpecies("spruce", "minecraft:spruce_log", "minecraft:spruce_leaves", "minecraft:spruce_sapling"),
            new TreeSpecies("birch", "minecraft:birch_log", "minecraft:birch_leaves", "minecraft:birch_sapling"),
            new TreeSpecies("jungle", "minecraft:jungle_log", "minecraft:jungle_leaves", "minecraft:jungle_sapling"),
            new TreeSpecies("acacia", "minecraft:acacia_log", "minecraft:acacia_leaves", "minecraft:acacia_sapling"),
            new TreeSpecies(
                    "dark_oak", "minecraft:dark_oak_log", "minecraft:dark_oak_leaves", "minecraft:dark_oak_sapling"),
            new TreeSpecies(
                    "mangrove", "minecraft:mangrove_log", "minecraft:mangrove_leaves", "minecraft:mangrove_propagule"),
            new TreeSpecies("cherry", "minecraft:cherry_log", "minecraft:cherry_leaves", "minecraft:cherry_sapling"));

    public TreeSpecies {
        Objects.requireNonNull(id, "id породы не может быть null: по нему имя приходит из плана");
        Objects.requireNonNull(logId, "logId породы не может быть null: по нему ищутся брёвна дерева");
        Objects.requireNonNull(
                leavesId, "leavesId породы не может быть null: по листьям дерево отличается от постройки");
        Objects.requireNonNull(saplingId, "saplingId породы не может быть null: саженцем порода восстанавливается");
    }

    public static List<TreeSpecies> parse(String text) {
        Objects.requireNonNull(text, "Имя породы не может быть null: параметр species приходит строкой из плана");
        if (ANY.equals(text)) {
            return ALL;
        }
        for (TreeSpecies species : ALL) {
            if (species.id.equals(text)) {
                return List.of(species);
            }
        }
        throw new IllegalArgumentException("Неизвестная порода '" + text + "': допустимы " + names()
                + ", выберите породу из списка или '" + ANY + "'");
    }

    public static Optional<TreeSpecies> ofLog(String blockId) {
        Objects.requireNonNull(blockId, "Идентификатор блока не может быть null: без него породу не сопоставить");
        return ALL.stream().filter(species -> species.logId.equals(blockId)).findFirst();
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>(ALL.size() + 1);
        names.add(ANY);
        for (TreeSpecies species : ALL) {
            names.add(species.id);
        }
        return List.copyOf(names);
    }
}
