package com.theminespirit.neoforge.board;

final class BreachPost {
    record Facts(boolean occupied, boolean open, boolean reachable, boolean knownUnreachable) {}

    enum Act {
        CLEAR,
        SKIP,
        POST,
        REMEMBER_UNREACHABLE
    }

    private BreachPost() {}

    static Act decide(Facts facts) {
        if (facts.occupied()) {
            return Act.CLEAR;
        }
        if (facts.open()) {
            return Act.SKIP;
        }
        if (facts.reachable()) {
            return Act.POST;
        }
        return facts.knownUnreachable() ? Act.SKIP : Act.REMEMBER_UNREACHABLE;
    }
}
