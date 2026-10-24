package com.theminespirit.core.forestry;

public final class ChopTuning {
    public static final int MAX_LOGS = 128;
    public static final int MAX_SPREAD = 8;
    public static final int MIN_NATURAL_LEAVES = 3;
    public static final int TRUNK_BASE_MAX_LOGS = 4;
    public static final int TRUNK_BASE_SPREAD = 1;
    public static final int SEARCH_MAX_LOGS = 256;
    public static final int SEARCH_RESTARTS_MAX = 2;
    public static final double STAND_DISTANCE = 2.0;
    // Рубка — вплотную к стволу (INC-49): дух доходит до самой стоянки у ствола, а не останавливается в 2 блоках.
    public static final double CHOP_ARRIVAL = 0.6;
    public static final int COLLECT_RADIUS = 6;
    public static final int MAX_REJECTED_TREES = 8;

    private ChopTuning() {}
}
