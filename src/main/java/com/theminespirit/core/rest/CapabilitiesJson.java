package com.theminespirit.core.rest;

import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptRequirement;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapabilitiesJson {
    private static final String TYPE = "type";

    private CapabilitiesJson() {}

    public static String toJson(ScriptCatalog catalog) {
        List<Map<String, Object>> scripts = new ArrayList<>();
        for (ScriptSpec spec : catalog.all()) {
            scripts.add(script(spec));
        }
        return RestJson.write(Map.of("scripts", scripts));
    }

    private static Map<String, Object> script(ScriptSpec spec) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ParamSpec param : spec.params()) {
            properties.put(param.name(), schema(param));
            if (param.required()) {
                required.add(param.name());
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(TYPE, "object");
        params.put("properties", properties);
        params.put("required", required);
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("id", spec.id());
        script.put("summary", spec.summary());
        script.put("params", params);
        script.put("preconditions", spec.preconditions());
        script.put(
                "requires",
                spec.requires().stream().map(CapabilitiesJson::requirement).toList());
        script.put("effects", spec.effects());
        return script;
    }

    private static Map<String, Object> requirement(ScriptRequirement requirement) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("kind", requirement.kind());
        json.put("tool", requirement.tool());
        json.put("param", requirement.param());
        json.put("tiers", requirement.tiers());
        json.put("fallback", requirement.fallback());
        return json;
    }

    private static Map<String, Object> schema(ParamSpec param) {
        Map<String, Object> schema = new LinkedHashMap<>();
        switch (param.type()) {
            case INT -> {
                schema.put(TYPE, "integer");
                putBounds(schema, param, true);
            }
            case DOUBLE -> {
                schema.put(TYPE, "number");
                putBounds(schema, param, false);
            }
            case STRING -> schema.put(TYPE, "string");
            case STRING_LIST -> {
                schema.put(TYPE, "array");
                schema.put("items", Map.of(TYPE, "string"));
            }
            case BOOL -> schema.put(TYPE, "boolean");
            case ENUM -> {
                schema.put(TYPE, "string");
                schema.put("enum", param.values());
            }
        }
        if (param.defaultValue() != null) {
            schema.put("default", param.defaultValue());
        }
        return schema;
    }

    private static void putBounds(Map<String, Object> schema, ParamSpec param, boolean whole) {
        if (param.min() != null) {
            schema.put("minimum", whole ? (Object) param.min().longValue() : param.min());
        }
        if (param.max() != null) {
            schema.put("maximum", whole ? (Object) param.max().longValue() : param.max());
        }
    }
}
