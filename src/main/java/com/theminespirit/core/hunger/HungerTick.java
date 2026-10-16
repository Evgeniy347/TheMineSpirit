package com.theminespirit.core.hunger;

import java.util.Objects;

public record HungerTick(SpiritHunger hunger, HungerEffect effect) {
    public HungerTick {
        Objects.requireNonNull(hunger, "Сытость после тика не может быть null: состояние голода обязано продолжиться");
        Objects.requireNonNull(effect, "Эффект тика не может быть null: адаптер по нему лечит или ранит духа");
    }
}
