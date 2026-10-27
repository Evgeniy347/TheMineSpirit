package com.theminespirit.core.board.graph;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class MinecraftRecipeData {
    private static final String RECIPES_DIR = "data/minecraft/recipe";
    private static final String TAGS_DIR = "data/minecraft/tags/item";
    private static final String PROBE = RECIPES_DIR + "/stick.json";
    private static final Map<String, String> RECIPES = load(RECIPES_DIR, "stick.json");
    private static final Map<String, String> TAGS = load(TAGS_DIR, "planks.json");

    private MinecraftRecipeData() {}

    static Map<String, String> recipes() {
        return RECIPES;
    }

    static Map<String, String> tags() {
        return TAGS;
    }

    private static Map<String, String> load(String directory, String probeName) {
        ClassLoader loader = MinecraftRecipeData.class.getClassLoader();
        URL probe = loader.getResource(directory + "/" + probeName);
        if (probe == null) {
            probe = loader.getResource(PROBE);
        }
        try {
            if (probe != null && "jar".equals(probe.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) probe.openConnection();
                return fromJar(connection.getJarFile(), directory);
            }
            if (probe != null && "file".equals(probe.getProtocol())) {
                Path root = Path.of(probe.toURI()).getParent();
                return fromDirectory(root);
            }
            try (JarFile jar = new JarFile(jarPath().toFile())) {
                return fromJar(jar, directory);
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Не прочитать ванильные рецепты Minecraft", error);
        } catch (URISyntaxException error) {
            throw new IllegalStateException("Путь к ванильным рецептам Minecraft не разобран", error);
        }
    }

    private static Path jarPath() {
        String explicit = System.getProperty("theminespirit.minecraft.client");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        Path artifacts = Path.of(System.getProperty("user.home"), ".gradle", "caches", "neoformruntime", "artifacts");
        if (Files.isDirectory(artifacts)) {
            try (Stream<Path> files = Files.list(artifacts)) {
                return files.filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith("minecraft_") && name.endsWith("_client.jar");
                        })
                        .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                        .orElseThrow(() -> new IllegalStateException("Клиентский jar Minecraft не найден в " + artifacts
                                + ": задайте путь через -Dtheminespirit.minecraft.client"));
            } catch (IOException error) {
                throw new UncheckedIOException("Не прочитать кэш Minecraft в " + artifacts, error);
            }
        }
        throw new IllegalStateException(
                "Кэш Minecraft не найден: задайте путь к клиентскому jar через " + "-Dtheminespirit.minecraft.client");
    }

    private static Map<String, String> fromDirectory(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalStateException("Каталог рецептов Minecraft не найден: " + root);
        }
        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> sorted = stream.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path file : sorted) {
                files.put(
                        file.getFileName().toString().replace(".json", ""),
                        Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return Map.copyOf(files);
    }

    private static Map<String, String> fromJar(JarFile jar, String directory) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        String prefix = directory + "/";
        List<JarEntry> sorted = jar.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry ->
                        entry.getName().startsWith(prefix) && entry.getName().endsWith(".json"))
                .sorted(Comparator.comparing(JarEntry::getName))
                .toList();
        for (JarEntry entry : sorted) {
            try (InputStream stream = jar.getInputStream(entry)) {
                String name = entry.getName().substring(prefix.length());
                files.put(
                        name.substring(0, name.length() - ".json".length()),
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return Map.copyOf(files);
    }
}
