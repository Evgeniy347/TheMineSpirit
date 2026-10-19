package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DocsMapGuardTests {
    private static final Path DOCS = Path.of("docs");
    private static final Path SCRIPTS = Path.of("scripts");
    private static final Path README = Path.of("README.md");
    private static final List<Path> INSTRUCTION_ROOTS = List.of(Path.of("AGENTS.md"), README, DOCS);
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");

    @Test
    void docsAndScripts_ReadmeMap_KnowsEveryFile() {
        List<String> missing = unmentioned(readmeMaps(), mappedFiles());

        assertEquals(List.of(), missing,
                "Файл документации или скрипт не упомянут в карте README: о нём не узнают ни игрок, ни агент — "
                        + "файл считается мусором и пропадает из поля зрения. Как надо: добавьте путь в README.md, "
                        + "в README своего каталога (docs/api/, docs/tactics/, docs/research/<тема>/) или, для "
                        + "скриптов, в строку каталога с косой чертой на конце (scripts/lead/). Правило: карта "
                        + "README знает каждый документ и скрипт. Не упомянуты");
    }

    @Test
    void links_RelativeTargetsInInstructions_Exist() {
        List<String> broken = brokenLinks(linksFromInstructions(), path -> Files.exists(Path.of(path)));

        assertEquals(List.of(), broken,
                "Ссылка `[..](путь)` в инструкции ведёт на несуществующий файл: читатель проваливается в пустоту, "
                        + "а инструкция обещает документ, которого нет. Как надо: починить путь или удалить ссылку. "
                        + "Правило: ссылки AGENTS.md, README.md и docs ведут на существующие файлы. Битые ссылки");
    }

    @Test
    void unmentioned_FileMissingFromMap_IsReported() {
        Map<String, String> maps = Map.of("README.md", "тут [a](docs/a.md)");

        assertEquals(List.of("docs/b.md"), unmentioned(maps, List.of("docs/a.md", "docs/b.md")),
                "Сторож ослеп: файл, пути которого нет в карте README, обязан попасть в пропажу");
    }

    @Test
    void unmentioned_ScriptsUnderMentionedDir_AreAccepted() {
        Map<String, String> maps = Map.of("README.md", "каталог scripts/lead/ и docs/api/");

        assertEquals(List.of("docs/api/plan.md"),
                unmentioned(maps, List.of("scripts/lead/chkq.sh", "docs/api/plan.md")),
                "Сторож ослеп: каталог закрывает скрипты, но не документ — документ обязан быть назван сам");
    }

    @Test
    void brokenLinks_ExistingAndExternalTargets_AreAccepted() {
        List<String> links = List.of("docs/a.md#якорь", "https://example.org/x", "#локальный");

        assertEquals(List.of(), brokenLinks(links, path -> path.equals("docs/a.md")),
                "Сторож ложно срабатывает: якорь, внешняя ссылка и локальный якорь не проверяются как пути, "
                        + "а существующий путь не битый");
    }

    @Test
    void brokenLinks_MissingFile_IsReported() {
        assertEquals(List.of("docs/missing.md"), brokenLinks(List.of("docs/missing.md"), path -> false),
                "Сторож ослеп: относительная ссылка на несуществующий файл обязана попасть в битые");
    }

    @Test
    void linksIn_TableLink_IsFound() {
        assertEquals(List.of("docs/api/README.md"), linksIn("[docs/api/README.md](docs/api/README.md)"),
                "Сторож ослеп: ссылка в таблице обязана находиться");
    }

    private static Map<String, String> readmeMaps() {
        Map<String, String> maps = new TreeMap<>();
        maps.put(README.toString(), read(README));
        try (Stream<Path> files = Files.walk(DOCS)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("README.md")).toList()) {
                maps.put(file.toString(), read(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог собрать карты README", e);
        }
        return maps;
    }

    private static List<String> mappedFiles() {
        List<String> paths = new ArrayList<>();
        try (Stream<Path> files = Files.walk(DOCS)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md")).toList()) {
                paths.add(file.toString().replace('\\', '/'));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог собрать документы", e);
        }
        try (Stream<Path> files = Files.walk(SCRIPTS)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                paths.add(file.toString().replace('\\', '/'));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог собрать скрипты", e);
        }
        return paths;
    }

    private static List<String> unmentioned(Map<String, String> maps, List<String> files) {
        List<String> missing = new ArrayList<>();
        for (String file : new TreeSet<>(files)) {
            boolean mentioned = maps.entrySet().stream().anyMatch(entry -> mentions(entry.getKey(), entry.getValue(), file));
            if (!mentioned) {
                missing.add(file);
            }
        }
        return missing;
    }

    private static boolean mentions(String mapPath, String mapText, String file) {
        Path mapDir = Path.of(mapPath).getParent();
        String relative = mapDir == null ? file : mapDir.relativize(Path.of(file)).toString().replace('\\', '/');
        if (mapText.contains(relative)) {
            return true;
        }
        String directory = file.substring(0, file.lastIndexOf('/') + 1);
        return file.startsWith("scripts/") && mapText.contains(directory);
    }

    private static List<String> linksFromInstructions() {
        List<String> links = new ArrayList<>();
        try {
            for (Path root : INSTRUCTION_ROOTS) {
                if (Files.isRegularFile(root)) {
                    links.addAll(resolvedLinks(root));
                    continue;
                }
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".md")).toList()) {
                        links.addAll(resolvedLinks(file));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог собрать ссылки", e);
        }
        return links;
    }

    private static List<String> resolvedLinks(Path file) {
        List<String> resolved = new ArrayList<>();
        for (String link : linksIn(read(file))) {
            if (link.startsWith("http") || link.startsWith("mailto") || link.startsWith("tel") || link.startsWith("#")) {
                resolved.add(link);
                continue;
            }
            Path parent = file.getParent();
            resolved.add((parent == null ? Path.of(link) : parent.resolve(link)).normalize().toString().replace('\\', '/'));
        }
        return resolved;
    }

    private static List<String> brokenLinks(List<String> links, Predicate<String> exists) {
        List<String> broken = new ArrayList<>();
        for (String link : new TreeSet<>(links)) {
            String target = link.split("#", 2)[0].trim();
            if (target.isEmpty() || target.startsWith("http") || target.startsWith("mailto") || target.startsWith("tel")) {
                continue;
            }
            if (!exists.test(target)) {
                broken.add(link);
            }
        }
        return broken;
    }

    private static List<String> linksIn(String text) {
        List<String> links = new ArrayList<>();
        Matcher matcher = LINK.matcher(text);
        while (matcher.find()) {
            links.add(matcher.group(1).trim());
        }
        return links;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + path, e);
        }
    }
}
