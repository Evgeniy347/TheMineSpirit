package com.theminespirit.core.perception;

import java.util.Locale;

public enum LocateType {
    BLOCK(Sighting.BLOCK),
    TREE(Sighting.TREE),
    CONTAINER(Sighting.CONTAINER);

    private final String sightingKind;

    LocateType(String sightingKind) {
        this.sightingKind = sightingKind;
    }

    public String scriptId() {
        return "locate_" + name().toLowerCase(Locale.ROOT);
    }

    public String sightingKind() {
        return sightingKind;
    }
}
