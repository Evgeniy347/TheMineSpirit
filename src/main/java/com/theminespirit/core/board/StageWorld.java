package com.theminespirit.core.board;

import java.util.Objects;

public interface StageWorld {
    StageWorld ANY = new StageWorld() {
        @Override
        public boolean built(BoardCase boardCase, int level) {
            return true;
        }

        @Override
        public boolean builtPlan(String id, int level) {
            return true;
        }
    };

    boolean built(BoardCase boardCase, int level);

    boolean builtPlan(String id, int level);

    static StageWorld of(BoardCase boardCase, int level, boolean built) {
        Objects.requireNonNull(boardCase, "Кейс мира не может быть null: по нему видно, готова ли постройка");
        return new StageWorld() {
            @Override
            public boolean built(BoardCase checked, int checkedLevel) {
                return checked != boardCase || checkedLevel != level || built;
            }

            @Override
            public boolean builtPlan(String id, int checkedLevel) {
                return true;
            }
        };
    }
}
