package com.theminespirit.core.reload;

import java.util.Objects;

/**
 * Прочитанное содержимое файла таблицы: текст json, человекочитаемый источник (откуда именно, для лога и команды)
 * и признак «из датапака ли» (переопределён ли файл мода датапаком мира).
 */
public record LoadedContent(String json, String origin, TableStatus.Source source) {
    public LoadedContent {
        Objects.requireNonNull(json, "Текст таблицы не может быть null: разбирать нечего");
        Objects.requireNonNull(
                origin, "Источник таблицы не может быть null: сообщение об ошибке и /spirit tables его называют");
        Objects.requireNonNull(
                source, "Источник (мод/датапак) не может быть null: им отличают переопределение датапаком");
    }
}
