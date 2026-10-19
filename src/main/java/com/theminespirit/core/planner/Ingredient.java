package com.theminespirit.core.planner;

public record Ingredient(String item, int count) {
    public Ingredient {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Имя предмета ингредиента пусто: без имени резолвер не найдёт предмет");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Количество ингредиента должно быть больше 0, а получено " + count
                    + ": нулевое количество вырождает рецепт");
        }
    }
}
