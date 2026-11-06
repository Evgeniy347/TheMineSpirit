package com.theminespirit.core.board.graph;

import com.theminespirit.core.husbandry.PetKind;
import com.theminespirit.core.husbandry.Species;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class FarmWays {
    private FarmWays() {}

    public static @Nullable Species species(String way) {
        if (way == null) {
            return null;
        }
        for (Species species : Species.all()) {
            String name = species.name().toLowerCase(Locale.ROOT);
            if (way.equals(name) || way.endsWith("_" + name)) {
                return species;
            }
        }
        return null;
    }

    public static @Nullable PetKind pet(String way) {
        if (way == null) {
            return null;
        }
        for (PetKind kind : PetKind.values()) {
            if (way.equals(kind.id()) || way.endsWith("_" + kind.id()) || way.contains("_" + kind.id() + "_")) {
                return kind;
            }
        }
        return null;
    }

    public static @Nullable String bed(String way) {
        if (way == null) {
            return null;
        }
        int start = way.indexOf("craft_");
        int end = way.indexOf("_bed");
        if (start != 0 || end <= start) {
            return null;
        }
        String color = way.substring("craft_".length(), end);
        return color + "_bed";
    }
}
