package com.theminespirit.core.town;

import java.util.Objects;

public record TownMapFinding(String object, String kind, String message) {
    public TownMapFinding {
        Objects.requireNonNull(object, "Объект проверки не может быть null: ошибку называют по объекту");
        Objects.requireNonNull(kind, "Вид ошибки не может быть null: по нему клиент выбирает реакцию");
        Objects.requireNonNull(message, "Текст ошибки не может быть null: без него не понять, что править");
    }
}
