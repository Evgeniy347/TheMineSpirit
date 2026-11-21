package com.theminespirit.core.script;

import static com.theminespirit.core.script.ScriptCatalogTestsSupport.*;

import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.List;
import java.util.Map;

class ScriptCatalogFixturesData {
    static ScriptExpectation spec(String id, List<String> required, Map<String, long[]> bounds) {
        return new ScriptExpectation(id, required, bounds);
    }

    static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream()
                .filter(param -> param.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте " + spec.id() + " нет параметра " + name));
    }
}
