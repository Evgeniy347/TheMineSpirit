package com.theminespirit.core.tactics;

import java.util.Arrays;
import java.util.Optional;

public enum Comparison {
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    EQ("=="),
    NE("!="),
    IN("in");

    private final String symbol;

    Comparison(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean compare(double actual, double expected) {
        return switch (this) {
            case LT -> actual < expected;
            case LE -> actual <= expected;
            case GT -> actual > expected;
            case GE -> actual >= expected;
            case EQ -> actual == expected;
            case NE -> actual != expected;
            case IN -> throw new IllegalStateException("Оператор in применяется только к перечислениям");
        };
    }

    public static Optional<Comparison> bySymbol(String symbol) {
        return Arrays.stream(values()).filter(op -> op.symbol.equals(symbol)).findFirst();
    }
}
