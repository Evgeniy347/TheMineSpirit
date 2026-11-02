package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theminespirit.core.inventory.JsonResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class GraphFragments {
    static final String DIRECTORY = "data/theminespirit/board/graph.d";
    private static final String WAYS = "ways";

    record Fragment(String json, String origin) {
        Fragment {
            JsonResource.parse(json, origin);
        }
    }

    private GraphFragments() {}

    static SupplyGraph load() {
        return load(
                JsonResource.readClasspath(SupplyGraph.RESOURCE),
                SupplyGraph.RESOURCE,
                JsonResource.readClasspath(SupplyGraph.RECIPES),
                SupplyGraph.RECIPES,
                classpath());
    }

    static SupplyGraph load(
            String manualJson,
            String manualOrigin,
            String generatedJson,
            String generatedOrigin,
            List<Fragment> fragments) {
        ObjectNode merged = ((ObjectNode) JsonResource.parse(manualJson, manualOrigin)).deepCopy();
        mergeGenerated(merged, JsonResource.parse(generatedJson, generatedOrigin));
        List<String> origins = new ArrayList<>(List.of(manualOrigin, generatedOrigin));
        for (Fragment fragment : fragments) {
            merge(merged, JsonResource.parse(fragment.json(), fragment.origin()), fragment.origin());
            origins.add(fragment.origin());
        }
        return SupplyGraph.parse(merged, String.join(" + ", origins));
    }

    static List<Fragment> classpath() {
        List<Fragment> fragments = new ArrayList<>();
        ClassLoader loader = GraphFragments.class.getClassLoader();
        try {
            List<URL> roots = new ArrayList<>();
            loader.getResources(DIRECTORY).asIterator().forEachRemaining(roots::add);
            roots.sort(Comparator.comparing(URL::toString));
            for (URL root : roots) {
                if ("file".equals(root.getProtocol())) {
                    fragments.addAll(fromDirectory(Path.of(root.toURI())));
                } else if ("jar".equals(root.getProtocol())) {
                    fragments.addAll(fromJar((JarURLConnection) root.openConnection()));
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Каталог фрагментов графа '" + DIRECTORY + "' не прочитан: проверьте, что он лежит в ресурсах мода",
                    error);
        } catch (URISyntaxException error) {
            throw new IllegalStateException(
                    "Путь к каталогу фрагментов графа '" + DIRECTORY
                            + "' не разобран: проверьте имя каталога в ресурсах",
                    error);
        }
        return List.copyOf(fragments);
    }

    static List<Fragment> fromDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> new Fragment(read(path), path.toString()))
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Каталог фрагментов графа '" + directory + "' не прочитан: проверьте права и кодировку файлов",
                    error);
        }
    }

    private static List<Fragment> fromJar(JarURLConnection connection) throws IOException {
        List<Fragment> fragments = new ArrayList<>();
        JarFile jar = connection.getJarFile();
        List<String> names = jar.stream()
                .map(entry -> entry.getName())
                .filter(name -> name.startsWith(DIRECTORY + "/") && name.endsWith(".json"))
                .filter(name -> !name.substring(DIRECTORY.length() + 1).contains("/"))
                .sorted()
                .toList();
        for (String name : names) {
            try (var stream = jar.getInputStream(jar.getJarEntry(name))) {
                fragments.add(new Fragment(new String(stream.readAllBytes(), StandardCharsets.UTF_8), name));
            }
        }
        return fragments;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Фрагмент графа '" + path + "' не прочитан: проверьте права и кодировку", error);
        }
    }

    private static void mergeGenerated(ObjectNode target, JsonNode generated) {
        mergeObjects(target, generated, "items");
        mergeObjects(target, generated, "buildings");
        mergeObjects(target, generated, "groups");
        mergeWays(target, generated);
    }

    private static void mergeObjects(ObjectNode target, JsonNode generated, String field) {
        JsonNode source = generated.path(field);
        if (!source.isObject()) {
            return;
        }
        JsonNode existing = target.get(field);
        ObjectNode section = existing != null && existing.isObject() ? (ObjectNode) existing : target.putObject(field);
        source.properties().forEach(entry -> {
            if (!section.has(entry.getKey())) {
                section.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    private static void mergeWays(ObjectNode target, JsonNode generated) {
        JsonNode source = generated.path(WAYS);
        if (!source.isArray() || source.isEmpty()) {
            return;
        }
        Set<String> manualMakes = new HashSet<>();
        for (JsonNode way : target.path(WAYS)) {
            manualMakes.add(way.path("makes").asText());
        }
        JsonNode existing = target.get(WAYS);
        ArrayNode ways = existing != null && existing.isArray() ? (ArrayNode) existing : target.putArray(WAYS);
        for (JsonNode way : source) {
            if (!manualMakes.contains(way.path("makes").asText())) {
                ways.add(way.deepCopy());
            }
        }
    }

    private static void merge(ObjectNode target, JsonNode fragment, String origin) {
        fragment.properties().forEach(entry -> {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                mergeObject(target, field, value, origin);
            } else if (value.isArray()) {
                mergeArray(target, field, value, origin);
            } else {
                JsonNode existing = target.get(field);
                if (existing == null) {
                    target.set(field, value.deepCopy());
                } else if (!existing.equals(value)) {
                    throw conflict(origin, field);
                }
            }
        });
    }

    private static void mergeObject(ObjectNode target, String field, JsonNode source, String origin) {
        JsonNode existing = target.get(field);
        ObjectNode section = existing != null && existing.isObject() ? (ObjectNode) existing : target.putObject(field);
        source.properties().forEach(entry -> {
            if (section.has(entry.getKey())) {
                throw conflict(origin, field + "." + entry.getKey());
            }
            section.set(entry.getKey(), entry.getValue().deepCopy());
        });
    }

    private static void mergeArray(ObjectNode target, String field, JsonNode source, String origin) {
        JsonNode existing = target.get(field);
        ArrayNode list = existing != null && existing.isArray() ? (ArrayNode) existing : target.putArray(field);
        Set<String> ids = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (JsonNode node : list) {
            String id = node.path("id").asText("");
            if (!id.isEmpty()) {
                ids.add(id);
            }
            if (node.isTextual()) {
                texts.add(node.asText());
            }
        }
        for (JsonNode node : source) {
            String id = node.path("id").asText("");
            if (!id.isEmpty() && !ids.add(id)) {
                throw conflict(origin, field + "." + id);
            }
            if (node.isTextual() && !texts.add(node.asText())) {
                throw conflict(origin, field + "." + node.asText());
            }
            list.add(node.deepCopy());
        }
    }

    private static IllegalStateException conflict(String origin, String id) {
        return new IllegalStateException("Файл '" + origin + "': id '" + id
                + "' уже есть в графе — фрагменты добавляют только новые узлы, иначе разные файлы спорят за один id");
    }
}
