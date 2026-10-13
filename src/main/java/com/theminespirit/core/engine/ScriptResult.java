package com.theminespirit.core.engine;

import java.util.Map;

public sealed interface ScriptResult {
    record Text(String value) implements ScriptResult {
    }

    record Data(Map<String, Object> fields) implements ScriptResult {
    }


    default Object jsonValue() {
        return switch (this) {
            case Text text -> text.value();
            case Data data -> data.fields();
        };
    }
}
