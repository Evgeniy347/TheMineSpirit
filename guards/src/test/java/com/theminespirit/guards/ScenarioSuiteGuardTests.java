package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ScenarioSuiteGuardTests {
    private static final Path BASELINE = Path.of("guards/src/test/resources/scenario-baseline.txt");
    private static final String SUITE_SUFFIX = "ScenarioTests.java";
    private static final String GUARD_SUFFIX = "GuardTests.java";
    private static final Pattern TEST_ANNOTATION =
            Pattern.compile("@(Test|Property|Example|ParameterizedTest|RepeatedTest)\\b");
    private static final String MECHANICS_PREFIX = "mech/";

    @Test
    void mechanicsWithScenario_NewTestFileWithoutJustification_IsReported() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("mech/building/com/theminespirit/core/building/FillScenarioTests.java", "@Test");
        files.put("mech/building/com/theminespirit/core/building/OldTests.java", "@Test");
        files.put("mech/building/com/theminespirit/core/building/NewTests.java", "@Test");
        files.put("mech/building/com/theminespirit/core/building/Helper.java", "final class Helper {}");

        assertEquals(
                List.of("mech/building/com/theminespirit/core/building/NewTests.java"),
                unjustified(files, Set.of("mech/building/com/theminespirit/core/building/OldTests.java")),
                "Сторож ослеп: новый тестовый файл рядом с сюитой обязан попадать в нарушители, а baseline, "
                        + "сюита (*ScenarioTests), сторож (*GuardTests) и helper — нет");
    }

    @Test
    void mechanicsWithScenario_ExistingFilesFromBaseline_AreAccepted() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("mech/mining/com/theminespirit/core/mining/MinePlanTests.java", "@Test");
        files.put("mech/mining/com/theminespirit/core/mining/MiningScenarioTests.java", "@Test");

        assertEquals(
                List.of(),
                unjustified(files, Set.of("mech/mining/com/theminespirit/core/mining/MinePlanTests.java")),
                "Сторож ложно срабатывает: старый файл из baseline и сама сюита — не нарушение");
    }

    @Test
    void mechanicsWithScenario_NewTestFile_NeedsJustificationOrSuite() {
        Set<String> baseline = baseline();
        List<String> offenders = new ArrayList<>();
        List<String> scenarios = new ArrayList<>();
        for (Path root : SourceFiles.moduleTestRoots()) {
            String module = moduleOf(root);
            if (!module.startsWith(MECHANICS_PREFIX)) {
                continue;
            }
            Map<Path, String> sources = SourceFiles.javaUnder(root);
            List<Path> suites = sources.keySet().stream()
                    .filter(file -> file.getFileName().toString().endsWith(SUITE_SUFFIX))
                    .toList();
            if (suites.isEmpty()) {
                continue;
            }
            scenarios.add(module + " → " + suites.get(0).getFileName());
            if (suites.size() > 1) {
                offenders.add(module + ": сюит " + suites.size() + " — оставь одну, остальное влей в неё");
            }
            Map<String, String> files = new LinkedHashMap<>();
            sources.forEach((file, text) ->
                    files.put(module + "/" + root.relativize(file).toString().replace('\\', '/'), text));
            offenders.addAll(unjustified(files, baseline));
        }

        assertEquals(
                List.of(),
                offenders,
                "В механике уже есть сценарная сюита (" + scenarios + "): новый тестовый файл — только с "
                        + "обоснованием. Сначала укрупни сюиту (добавь духа, клетку, шаг, проверку), чтобы она "
                        + "краснела на баге (docs/TESTING.md → «Крупные сценарии»). Если без отдельного файла "
                        + "нельзя — допиши путь в guards/src/test/resources/scenario-baseline.txt с причиной. "
                        + "Нарушители");
    }

    static List<String> unjustified(Map<String, String> files, Set<String> baseline) {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!TEST_ANNOTATION.matcher(entry.getValue()).find()) {
                continue;
            }
            if (name.endsWith(SUITE_SUFFIX) || name.endsWith(GUARD_SUFFIX) || baseline.contains(path)) {
                continue;
            }
            offenders.add(path);
        }
        return offenders;
    }

    private static Set<String> baseline() {
        try {
            Set<String> paths = new LinkedHashSet<>();
            for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
                String value = line.strip();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                int comment = value.indexOf('#');
                paths.add((comment >= 0 ? value.substring(0, comment) : value).strip());
            }
            return paths;
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Сторож не прочитал baseline " + BASELINE + ": без него правило не проверить", error);
        }
    }

    private static String moduleOf(Path root) {
        StringBuilder module = new StringBuilder();
        for (Path part : root) {
            String value = part.toString();
            if ("src".equals(value)) {
                break;
            }
            if (!module.isEmpty()) {
                module.append('/');
            }
            module.append(value);
        }
        return module.toString().replace('\\', '/');
    }
}
