package com.theminespirit.core.mining;

public final class MiningTuning {
    private MiningTuning() {}

    // Ванильные жилы меньше, крупные медные — около 20 блоков
    public static final int VEIN_MAX_BLOCKS = 64;
    public static final int MAX_APPROACH_FAILURES = 3;

    // Факелы в шахте: интервал постановки считается в шагах спуска
    public static final int DESCENT_TORCH_INTERVAL_MIN = 1;
    public static final int DESCENT_TORCH_INTERVAL_MAX = 16;
}
