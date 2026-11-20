package com.theminespirit.core.town;

import com.theminespirit.core.building.api.WoodSpecies;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OrchardSites {
    public static final String TEMPLATE = "orchard_extra";
    public static final String BUILDING_PREFIX = "orchard_";
    public static final String MAIN_PLACEMENT = "orchard";
    public static final String MAIN_BUILDING = "orchard_oak";
    private static final int FIRST_NUMBER = 1;

    private OrchardSites() {}

    public static Optional<String> speciesOfBuilding(String building) {
        if (building == null || !building.startsWith(BUILDING_PREFIX)) {
            return Optional.empty();
        }
        String species = building.substring(BUILDING_PREFIX.length());
        if (parseNumber(species).isPresent()) {
            species = species.substring(0, species.lastIndexOf('_'));
        }
        return WoodSpecies.ALL.contains(species) ? Optional.of(species) : Optional.empty();
    }

    public static boolean isExtra(String building) {
        return speciesOfBuilding(building).isPresent();
    }

    public static boolean isSite(String placementId) {
        return placementId != null && isExtra(placementId);
    }

    public static String baseBuilding(String building) {
        String species = speciesOfBuilding(building)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Постройка '" + building + "' не сад: базовую постройку берут только у orchard_<порода>"));
        return BUILDING_PREFIX + species;
    }

    public static int number(String building) {
        return parseNumber(building.substring(BUILDING_PREFIX.length())).orElse(FIRST_NUMBER);
    }

    public static String numbered(String baseBuilding, int number) {
        String species = speciesOfBuilding(baseBuilding)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Постройка '" + baseBuilding + "' не сад: номер дают только месту orchard_<порода>"));
        if (number < FIRST_NUMBER) {
            throw new IllegalArgumentException("Номер сада " + number + ": счёт идёт с первого");
        }
        return number == FIRST_NUMBER ? BUILDING_PREFIX + species : BUILDING_PREFIX + species + "_" + number;
    }

    public static boolean wantAnother(boolean built, int saplingStock, int saplingsPerPlot) {
        if (saplingsPerPlot < 1) {
            throw new IllegalArgumentException("Саженцев на сад " + saplingsPerPlot + ": нужно хотя бы один");
        }
        return built && saplingStock >= saplingsPerPlot;
    }

    public static Optional<Placement> placement(TownPlan plan, String building) {
        Objects.requireNonNull(plan, "План не может быть null: место сада берут из него");
        return isExtra(building) ? plan.placement(building) : Optional.empty();
    }

    public static List<String> plotIds(String building, boolean mainPlaced, List<OrchardPlot> extraOrchards) {
        Objects.requireNonNull(building, "Постройка сада не может быть null: по ней ищут участки");
        if (!isExtra(building)) {
            throw new IllegalArgumentException(
                    "Постройка '" + building + "' не сад: участки ищут только у orchard_<порода>");
        }
        List<String> ids = new ArrayList<>();
        if (MAIN_BUILDING.equals(building) && mainPlaced) {
            ids.add(MAIN_PLACEMENT);
        }
        for (OrchardPlot plot : extraOrchards) {
            if (building.equals(baseBuilding(plot.building()))) {
                ids.add(plot.building());
            }
        }
        return List.copyOf(ids);
    }

    private static Optional<Integer> parseNumber(String tail) {
        int cut = tail.lastIndexOf('_');
        if (cut <= 0 || cut == tail.length() - 1) {
            return Optional.empty();
        }
        String digits = tail.substring(cut + 1);
        if (digits.chars().anyMatch(ch -> ch < '0' || ch > '9')) {
            return Optional.empty();
        }
        int value = Integer.parseInt(digits);
        return value >= 2 ? Optional.of(value) : Optional.empty();
    }
}
