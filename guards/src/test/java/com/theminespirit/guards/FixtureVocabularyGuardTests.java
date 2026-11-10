package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.movement.BlockKind;
import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FixtureVocabularyGuardTests {
    private static final Set<String> KINDS =
            Arrays.stream(BlockKind.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    @Test
    void liveFixtures_UseOnlyBlockKindNames_AreReported() {
        List<String> problems = new ArrayList<>();
        for (Path root : fixtureRoots()) {
            for (Path fixture : fixtures(root)) {
                try {
                    problems.addAll(problemsOf(display(fixture), Files.readAllLines(fixture, StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    throw new UncheckedIOException("Сторож не смог прочитать живую фикстуру " + fixture, e);
                }
            }
        }
        assertEquals(
                List.of(),
                problems,
                "Живая фикстура — это словарь BlockKind: имена видов блоков задаёт только он (core/movement/"
                        + "BlockKind.java). Второй словарь расходится с моделью ямы молча. Нарушители:");
    }

    @Test
    void liveFixture_UnknownKind_IsReported() {
        List<String> problems = problemsOf("яма.live", List.of("0 0 0 SOFT", "1 0 0 NETHER"));

        assertEquals(
                List.of("яма.live:2: вид блока NETHER не из BlockKind"),
                problems,
                "Сторож ослеп: неизвестное имя в фикстуре обязано попадать в нарушители");
    }

    @Test
    void liveFixture_KnownKind_IsAccepted() {
        assertEquals(
                List.of(),
                problemsOf("яма.live", List.of("# снимок", "feet 0 0 0", "", "0 0 0 OBSTACLE")),
                "Сторож ложно срабатывает: вид из BlockKind допустим, комментарии, feet и пустые строки не в счёт");
    }

    @Test
    void pitWorld_AtMethod_ReturnsBlockKind() throws NoSuchMethodException {
        assertEquals(
                BlockKind.class,
                PitExitPlanner.World.class.getMethod("at", BlockCoord.class).getReturnType(),
                "Мир ямы обязан возвращать общий BlockKind: свой Kind рядом с фикстурой — второй словарь");
    }

    private static List<String> problemsOf(String source, List<String> lines) {
        List<String> problems = new ArrayList<>();
        int number = 0;
        for (String line : lines) {
            number++;
            if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                continue;
            }
            StringTokenizer parts = new StringTokenizer(line);
            if (parts.countTokens() != 4) {
                problems.add(source + ":" + number + ": строка не из четырёх полей: " + line);
                continue;
            }
            parts.nextToken();
            parts.nextToken();
            parts.nextToken();
            String kind = parts.nextToken();
            if (!KINDS.contains(kind)) {
                problems.add(
                        source + ":" + number + ": вид блока " + kind + " не из " + BlockKind.class.getSimpleName());
            }
        }
        return problems;
    }

    private static List<Path> fixtureRoots() {
        Set<Path> roots = new TreeSet<>();
        for (Path javaRoot : SourceFiles.sourceRoots()) {
            Path resources = javaRoot.getParent().resolve("resources");
            if (Files.isDirectory(resources)) {
                roots.add(resources);
            }
        }
        return List.copyOf(roots);
    }

    private static List<Path> fixtures(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".live")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог обойти ресурсы " + root, e);
        }
    }

    private static String display(Path path) {
        return path.toString().replace('\\', '/');
    }
}
