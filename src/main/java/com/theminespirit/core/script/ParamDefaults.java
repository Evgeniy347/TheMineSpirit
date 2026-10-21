package com.theminespirit.core.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Подставляет умолчания каталога в параметры скрипта. Нужно вызовам мимо валидатора плана
 * (доска, снабжение, вложенные раннеры): без него необязательный параметр с умолчанием
 * доходит до скрипта отсутствующим и падает «Не задан обязательный параметр».
 */
public final class ParamDefaults {
    private ParamDefaults() {}

    public static Map<String, Object> fill(ScriptCatalog catalog, String script, Map<String, Object> params) {
        Objects.requireNonNull(catalog, "catalog не может быть null: умолчания берутся из описания скрипта");
        Objects.requireNonNull(script, "script не может быть null: по id выбирается описание");
        Objects.requireNonNull(params, "params не может быть null: пустые параметры задают пустой картой");
        ScriptSpec spec = catalog.find(script)
                .orElseThrow(() -> new IllegalArgumentException("Скрипт '" + script
                        + "' отсутствует в каталоге: умолчания неоткуда взять, сверьте /v1/capabilities"));
        Map<String, Object> filled = new LinkedHashMap<>(params);
        for (ParamSpec param : spec.params()) {
            if (!param.required() && param.defaultValue() != null && !filled.containsKey(param.name())) {
                filled.put(param.name(), param.defaultValue());
            }
        }
        return Map.copyOf(filled);
    }
}
