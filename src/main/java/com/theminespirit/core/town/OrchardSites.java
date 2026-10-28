package com.theminespirit.core.town;

import com.theminespirit.core.building.WoodSpecies;
import java.util.Optional;

public final class OrchardSites {
    public static final String TEMPLATE = "orchard_extra";
    public static final String BUILDING_PREFIX = "orchard_";
    private static final String MAIN_SPECIES = WoodSpecies.OAK;

    private OrchardSites() {}

    public static Optional<String> speciesOfBuilding(String building) {
        if (building == null || !building.startsWith(BUILDING_PREFIX)) {
            return Optional.empty();
        }
        String species = building.substring(BUILDING_PREFIX.length());
        return WoodSpecies.ALL.contains(species) ? Optional.of(species) : Optional.empty();
    }

    public static boolean isExtra(String building) {
        return speciesOfBuilding(building)
                .filter(species -> !MAIN_SPECIES.equals(species))
                .isPresent();
    }

    public static boolean isSite(String placementId) {
        return placementId != null && isExtra(placementId);
    }

    public static String siteId(String building) {
        if (!isExtra(building)) {
            throw new IllegalArgumentException("Постройка '" + building
                    + "' не сад отдельной породы: место сада берут у orchard_<порода>, кроме основной породы");
        }
        return building;
    }

    public static Optional<Placement> placement(TownPlan plan, String building) {
        return isExtra(building) ? plan.placement(building) : Optional.empty();
    }
}
