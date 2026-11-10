package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.log.LogCause;
import com.theminespirit.core.log.LogSchema;
import com.theminespirit.core.log.LogType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LogdbSchemaGuardTests {
    private static final Path SCHEMA = Path.of("scripts/logdb/schema.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void javaTypes_AgainstSchemaJson_Match() throws IOException {
        JsonNode types = schema().get("types");
        for (LogType type : LogType.values()) {
            JsonNode value = types.get(type.json());
            assertTrue(
                    value != null,
                    "Тип '" + type.json() + "' есть в Java, но не описан в schema.json: загрузчик не найдёт таблицу");
            assertEquals(
                    type.table(),
                    value.asText(),
                    "Таблица типа '" + type.json() + "' расходится: Java и schema.json должны называть одно место");
        }
        assertEquals(LogType.values().length, types.size(), "В schema.json есть тип, которого нет в Java");
    }

    @Test
    void javaCauses_AgainstSchemaJson_Match() {
        Set<String> json = new TreeSet<>();
        schema().get("causes").forEach(node -> json.add(node.asText()));
        Set<String> java = new TreeSet<>();
        for (LogCause cause : LogCause.values()) {
            java.add(cause.code());
        }
        assertEquals(json, java, "Закрытый набор причин расходится: code причины обязан быть в LogCause и schema.json");
    }

    @Test
    void requiredAndAllowedFields_AgainstSchemaJson_Match() {
        JsonNode tables = schema().get("tables");
        Set<String> common = new TreeSet<>();
        schema().get("common").forEach(node -> common.add(node.asText()));
        common.remove("id");
        List<String> problems = new ArrayList<>();
        for (LogType type : LogType.values()) {
            JsonNode table = tables.get(type.table());
            Set<String> required = new TreeSet<>();
            Set<String> allowed = new LinkedHashSet<>(common);
            table.get("columns").fields().forEachRemaining(entry -> {
                allowed.add(entry.getKey());
                if (entry.getValue().path("required").asBoolean()) {
                    required.add(entry.getKey());
                }
            });
            LogSchema.Spec spec = LogSchema.of(type);
            if (!required.equals(new TreeSet<>(spec.required()))) {
                problems.add(type.table() + ": обязательные " + new TreeSet<>(spec.required()) + " ≠ " + required);
            }
            if (!allowed.equals(new TreeSet<>(spec.allowed()))) {
                problems.add(type.table() + ": допустимые " + new TreeSet<>(spec.allowed()) + " ≠ " + allowed);
            }
        }
        assertEquals(
                List.of(),
                problems,
                "Java-схема и schema.json разошлись: запись пройдёт проверку, а загрузчик её отвергнет. "
                        + "Правьте оба места одной миграцией. Расхождения");
    }

    @Test
    void pythonScripts_LoaderAndImport_Pass() throws Exception {
        Path root = Path.of("scripts/logdb");
        Path output = Files.createTempFile("logdb-guard", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(python(), "-m", "unittest", "tests.test_loader", "-q");
            builder.directory(root.toFile());
            builder.environment().put("PYTHONUTF8", "1");
            builder.redirectErrorStream(true);
            // lib: вывод — в файл, а не в трубу: под нагрузкой потомок python (logq) держит трубу дольше
            // lib: родителя, и чтение после waitFor ждало его вечно; голод машины выглядел как код теста.
            builder.redirectOutput(output.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            String text = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(finished, "Тесты загрузчика не уложились в 120 секунд: проверьте, не ждут ли они базу");
            assertEquals(
                    0,
                    process.exitValue(),
                    "Тесты загрузчика и скриптов упали: база логов не должна ломаться на обрывках, ротации и старых "
                            + "координатах. Вывод: " + text);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static String python() {
        List<String> candidates = new ArrayList<>();
        for (String variable : List.of("THEMINESPIRIT_PYTHON", "PYTHON")) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                candidates.add(value.trim());
            }
        }
        candidates.add("python3");
        candidates.add("python");
        List<String> tried = new ArrayList<>();
        for (String candidate : candidates) {
            Path found = locate(candidate);
            if (found == null) {
                continue;
            }
            tried.add(found.toString());
            if (found.toString().replace('\\', '/').contains("/WindowsApps/")) {
                continue;
            }
            return found.toString();
        }
        throw new IllegalStateException(
                "нет рабочего python 3 (искали " + candidates + ", нашли " + tried + "): тесты загрузчика логов "
                        + "не проверить. Как надо: поставить python 3 или указать THEMINESPIRIT_PYTHON; "
                        + "заглушки WindowsApps не годятся");
    }

    private static Path locate(String candidate) {
        if (candidate.contains("/") || candidate.contains("\\") || candidate.startsWith(".")) {
            Path direct = Path.of(candidate);
            return Files.isRegularFile(direct) ? direct : null;
        }
        String path = System.getenv().getOrDefault("PATH", "");
        for (String directory : path.split(File.pathSeparator, -1)) {
            if (directory.isBlank()) {
                continue;
            }
            for (String name : List.of(candidate, candidate + ".exe")) {
                Path found = Path.of(directory, name);
                if (Files.isRegularFile(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JsonNode schema() {
        try {
            return MAPPER.readTree(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Схема логов '" + SCHEMA + "' не читается: " + error.getMessage(), error);
        }
    }
}
