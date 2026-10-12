package com.theminespirit.core.tactics;

public enum TacticalAction {
    ENGAGE(TacticalOption.FIGHT),
    SHOOT(TacticalOption.FIGHT),
    DEFEND_AREA(TacticalOption.FIGHT),
    PROTECT(TacticalOption.FIGHT),
    RETREAT(TacticalOption.RETREAT),
    CONTINUE(TacticalOption.IGNORE);

    private final TacticalOption option;

    TacticalAction(TacticalOption option) {
        this.option = option;
    }

    public TacticalOption option() {
        return option;
    }
}
