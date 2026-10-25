package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;

public final class FenceGateRule {
    public static final int MIN_SPAN = 10;
    public static final int MAX_SPAN = 20;

    private FenceGateRule() {}

    public static List<Integer> gates(int sideLength) {
        if (sideLength < 1) {
            throw new IllegalArgumentException(
                    "Длина стороны " + sideLength + " меньше 1: на такой стороне калитку не поставить");
        }
        int span = sideLength - 1;
        if (span < 2 * MIN_SPAN) {
            return List.of();
        }
        int spans = Math.max(2, (span + MAX_SPAN - 1) / MAX_SPAN);
        int gates = spans - 1;
        List<Integer> offsets = new ArrayList<>(gates);
        for (int index = 1; index <= gates; index++) {
            offsets.add((int) ((long) index * sideLength / spans));
        }
        return List.copyOf(offsets);
    }
}
