package com.theminespirit.core.board;

public record PriorityContext(
        boolean caughtAnimal, boolean penBuilt, boolean baitMissing, boolean speciesFull, boolean activeRepair) {

    public static PriorityContext none() {
        return new PriorityContext(false, false, false, false, false);
    }
}
