package com.theminespirit.core.script;

import com.theminespirit.core.tactics.TargetSelector;

final class PersonSelector {
    private PersonSelector() {}

    static void requirePerson(String raw, String field) {
        TargetSelector.Target parsed;
        try {
            parsed = TargetSelector.parse(raw);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Параметр '" + field + "' = '" + raw + "' не распознан: " + error.getMessage(), error);
        }
        if (parsed.kind() != TargetSelector.Kind.PLAYER && parsed.kind() != TargetSelector.Kind.SPIRIT) {
            throw new IllegalArgumentException(
                    "Параметр '" + field + "' = '" + raw + "' должен указывать на игрока или "
                            + "духа (player:<имя>, spirit:<uuid>), а вид " + parsed.kind()
                            + " сюда не годится: с ним не передать предмет и не встретиться");
        }
    }
}
