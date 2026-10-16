package com.theminespirit.core.board;

/**
 * Ситуация для модификаторов приоритета: чистые флаги, которые собирает адаптер
 * из мира и запасов. Ядро только складывает дельты, мир не читает.
 */
public record PriorityContext(boolean caughtAnimal, boolean penBuilt, boolean baitMissing,
        boolean speciesFull, boolean activeRepair) {

    public static PriorityContext none() {
        return new PriorityContext(false, false, false, false, false);
    }
}
