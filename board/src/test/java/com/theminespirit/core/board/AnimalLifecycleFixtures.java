package com.theminespirit.core.board;

final class AnimalLifecycleFixtures {
    private AnimalLifecycleFixtures() {}

    static void apply(AnimalLifecycle lifecycle, String event) {
        switch (event) {
            case "penBuilt" -> lifecycle.penBuilt("capture_1");
            case "lured" -> lifecycle.lured();
            case "animalDied" -> lifecycle.animalDied();
            default -> throw new IllegalArgumentException("Неизвестное событие: " + event);
        }
    }
}
