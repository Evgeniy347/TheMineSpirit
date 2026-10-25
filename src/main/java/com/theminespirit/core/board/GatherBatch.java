package com.theminespirit.core.board;

import java.util.List;

public final class GatherBatch {
    public static final int SPLIT_MIN = 6;

    private GatherBatch() {}

    public static List<Integer> split(int batch, int craftOutput, int helpers) {
        if (batch < SPLIT_MIN || helpers < 2) {
            return batch > 0 ? List.of(batch) : List.of();
        }
        int output = Math.max(craftOutput, 1);
        int crafts = (batch + output - 1) / output;
        int first = Math.min(batch, (crafts + 1) / 2 * output);
        int second = batch - first;
        return second > 0 ? List.of(first, second) : List.of(batch);
    }

    public static int round(int shortage, int craftOutput) {
        if (shortage <= 0) {
            return 0;
        }
        if (craftOutput <= 1) {
            return shortage;
        }
        long batches = (shortage + (long) craftOutput - 1) / craftOutput;
        return (int) Math.min(batches * craftOutput, Integer.MAX_VALUE);
    }
}
