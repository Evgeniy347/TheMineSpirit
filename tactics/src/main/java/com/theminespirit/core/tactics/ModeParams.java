package com.theminespirit.core.tactics;

public record ModeParams(
        boolean initiates,
        RadiusBase radiusBase,
        double engageRadius,
        double pursuitRadius,
        AnchorKind anchor,
        boolean leash,
        boolean returnToAnchor) {
    public static final double MAX_RADIUS = 256;

    public ModeParams {
        FactChecks.required("ModeParams.radiusBase", radiusBase);
        FactChecks.required("ModeParams.anchor", anchor);
        FactChecks.range("ModeParams.engageRadius", engageRadius, 0, MAX_RADIUS);
        FactChecks.range("ModeParams.pursuitRadius", pursuitRadius, 0, MAX_RADIUS);
    }

    public double engage(double zoneRadius) {
        return engageRadius + (radiusBase == RadiusBase.ZONE ? zoneRadius : 0);
    }

    public double pursuit(double zoneRadius) {
        return pursuitRadius + (radiusBase == RadiusBase.ZONE ? zoneRadius : 0);
    }
}
