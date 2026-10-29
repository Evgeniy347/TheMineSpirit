package com.theminespirit.core.husbandry;

import java.util.Locale;

public final class SpeciesIds {
    private SpeciesIds() {}

    public static Species of(String ticketId) {
        String[] parts = ticketId.split(":", 3);
        String prefix = parts.length > 1 && "capture".equals(parts[0]) ? parts[1] : ticketId.split("[_:/]", 2)[0];
        String name = prefix.toLowerCase(Locale.ROOT);
        for (Species species : Species.all()) {
            if (species.name().toLowerCase(Locale.ROOT).equals(name)) {
                return species;
            }
        }
        return Species.COW;
    }
}
