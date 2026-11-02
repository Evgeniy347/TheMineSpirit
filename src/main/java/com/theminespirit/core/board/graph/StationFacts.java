package com.theminespirit.core.board.graph;

import org.jspecify.annotations.Nullable;

public final class StationFacts {
    private StationFacts() {}

    public static GraphFacts.Station withContents(
            String id, GraphFacts.@Nullable Position position, int input, int fuel, int output) {
        if (input < 0 || fuel < 0 || output < 0) {
            throw new IllegalArgumentException("Печь " + id + ": вход " + input + ", топливо " + fuel + ", выход "
                    + output + " — количество не бывает отрицательным");
        }
        boolean busy = input > 0 && fuel > 0;
        return new GraphFacts.Station(
                id,
                id,
                position,
                busy,
                fuel > 0 ? "fuel" : null,
                input > 0 ? "input" : null,
                output > 0 ? "output" : null);
    }
}
