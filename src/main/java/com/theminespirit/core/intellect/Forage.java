package com.theminespirit.core.intellect;

public record Forage(String containerFood, int prey, String preySpecies) {
    public static final Forage NONE = new Forage("", 0, "");

    public Forage {
        if (containerFood == null) {
            throw new IllegalArgumentException("Еда склада без имени не описывается: отсутствие задают пустой строкой");
        }
        if (prey < 0) {
            throw new IllegalArgumentException("Дичи = " + prey + ": число голов не может быть отрицательным");
        }
        if (preySpecies == null) {
            throw new IllegalArgumentException("Вид дичи без имени не описывается: отсутствие задают пустой строкой");
        }
        if (prey > 0 && preySpecies.isBlank()) {
            throw new IllegalArgumentException("Дичи " + prey + " без вида: охота не знает, на кого идти");
        }
        if (prey == 0 && !preySpecies.isBlank()) {
            throw new IllegalArgumentException("Вид дичи '" + preySpecies + "' без голов: снимок противоречив");
        }
    }

    public boolean hasContainerFood() {
        return !containerFood.isBlank();
    }

    public boolean hasPrey() {
        return prey > 0;
    }
}
