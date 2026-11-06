package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CapabilitiesJsonProperties {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();

    @Provide
    Arbitrary<ScriptSpec> scripts() {
        return Arbitraries.of(CATALOG.all());
    }

    @Property
    void toJson_AnyScriptOfDefaultCatalog_KeepsEveryParamAndRequiredList(@ForAll("scripts") ScriptSpec spec)
            throws JsonProcessingException {
        JsonNode root = MAPPER.readTree(CapabilitiesJson.toJson(ScriptCatalog.of(List.of(spec))));

        JsonNode script = root.get("scripts").get(0);
        JsonNode properties = script.get("params").get("properties");
        List<String> required = new ArrayList<>();
        script.get("params").get("required").forEach(name -> required.add(name.textValue()));
        List<String> expectedRequired = spec.params().stream()
                .filter(ParamSpec::required)
                .map(ParamSpec::name)
                .toList();
        assertEquals(
                spec.params().size(),
                properties.size(),
                "Каждый параметр скрипта '" + spec.id() + "' обязан попасть в properties: потерянный параметр ИИ "
                        + "не сможет передать");
        for (ParamSpec param : spec.params()) {
            assertTrue(
                    properties.has(param.name()),
                    "Параметр '" + param.name() + "' скрипта '" + spec.id() + "' отсутствует в JSON-схеме");
        }
        assertEquals(
                expectedRequired,
                required,
                "required скрипта '" + spec.id() + "' обязан совпадать с ParamSpec.required() и порядком параметров");
        assertEquals(spec.id(), script.get("id").textValue(), "id скрипта в JSON обязан совпадать с каталогом");
    }
}
