package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ApiModuleGuardTests {
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);
    private static final List<String> ALLOWED_PREFIXES = List.of("java.", "com.fasterxml.", "org.jspecify.");
    private static final List<String> CONTRACTS = List.of(
            "core/board/BoardCase.java",
            "core/board/StuckCause.java",
            "core/board/ConfigStamp.java",
            "core/board/Point.java",
            "core/observation/BlockCoord.java",
            "core/observation/IdFilter.java",
            "core/observation/ScanRadius.java",
            "core/observation/ObservationLimits.java",
            "core/perception/CellReport.java",
            "core/perception/HazardFacts.java",
            "core/script/ScriptSpec.java",
            "core/script/ScriptRequirement.java",
            "core/script/ParamSpec.java",
            "core/script/ParamType.java",
            "core/script/ConditionSpec.java",
            "core/script/CatalogLimits.java",
            "core/script/ScriptCatalog.java",
            "core/script/ConditionCatalog.java",
            "core/script/SpecCatalog.java",
            "core/plan/PlanValidator.java",
            "core/task/TaskRecord.java",
            "core/task/TaskState.java",
            "core/policy/PolicyExemptions.java",
            "core/policy/PolicyId.java",
            "core/mining/SafetyView.java",
            "core/mining/Cell.java",
            "core/mining/MiningSafety.java",
            "core/mining/BreakSafety.java",
            "core/mining/OreKind.java",
            "core/mining/MineOres.java",
            "core/mining/LeftOreRules.java",
            "core/husbandry/Species.java",
            "core/husbandry/PetKind.java",
            "core/husbandry/FeedTable.java",
            "core/building/WoodSpecies.java",
            "core/forestry/ChopTuning.java",
            "core/forestry/TreeView.java",
            "core/policy/StrayRules.java",
            "core/coordinator/WorkKind.java",
            "core/coordinator/WorkTarget.java",
            "core/coordinator/WorkReservations.java",
            "core/movement/GotoTuning.java",
            "core/movement/BodyStep.java",
            "core/tactics/adapter/Visibility.java",
            "core/farming/CropKind.java",
            "core/farming/HarvestMethod.java",
            "core/forestry/TreeSpecies.java",
            "core/inventory/JsonResource.java",
            "core/reload/TableHolder.java",
            "core/reload/TableStatus.java",
            "core/tactics/Point.java",
            "core/Lookup.java");

    @Test
    void apiContracts_LiveInApiModule_NotInRoot() {
        List<String> missing = new ArrayList<>();
        for (String logical : CONTRACTS) {
            Path file = SourceFiles.path(logical);
            if (!file.normalize().startsWith(SourceFiles.API)) {
                missing.add(logical + " → " + file);
            }
        }

        assertEquals(
                List.of(),
                missing,
                "Контракты шага 3 обязаны физически лежать в api/src/main/java: иначе :api не собирается сам, "
                        + "а корень снова тянет всё. Как надо: перенеси класс в :api, пакет и имя не меняй. "
                        + "Нарушители (логический путь → фактический):");
    }

    @Test
    void apiSources_Imports_OnlyJdkJacksonAndApi() {
        Map<Path, String> sources = SourceFiles.javaUnder(SourceFiles.apiPackage());
        Set<String> apiClasses = apiClasses(sources);

        assertEquals(
                List.of(),
                violations(sources, apiClasses),
                "Модуль :api обязан собираться без остального проекта: "
                        + "разрешены JDK, Jackson, jspecify и классы самого :api. Импорт чужого класса делает :api "
                        + "зависимым от корня и возвращает цикл. Как надо: перенеси нужный контракт в :api или "
                        + "оставь реализацию у владельца за интерфейсом из :api. Нарушители:");
    }

    @Test
    void apiSources_ForbiddenImport_IsReported() {
        Map<Path, String> sample = Map.of(Path.of("api/Example.java"), """
                package com.theminespirit.core;
                import net.minecraft.world.level.Level;
                class Example {}
                """);
        Map<Path, String> neighbor = Map.of(Path.of("api/Neighbour.java"), """
                package com.theminespirit.core;
                import com.theminespirit.core.observation.BlockCoord;
                class Neighbour {}
                """);
        Set<String> apiClasses = Set.of("com.theminespirit.core.observation.BlockCoord");

        assertEquals(
                List.of("api/Example.java: net.minecraft.world.level.Level"),
                violations(sample, apiClasses),
                "Сторож ослеп: импорт Minecraft в :api обязан попасть в нарушители");
        assertEquals(
                List.of(),
                violations(neighbor, apiClasses),
                "Сторож ложно срабатывает: импорт класса самого :api допустим");
    }

    @Test
    void apiTestSources_Tests_LiveInApiModule() {
        assertTrue(
                Files.isDirectory(SourceFiles.apiTestPackage()),
                "У модуля :api обязаны быть свои тесты (api/src/test/java): иначе перенесённые контракты "
                        + "проверяются только через корень и :api нельзя гонять отдельно");
        assertTrue(
                SourceFiles.allTests().keySet().stream()
                        .anyMatch(path -> path.normalize().startsWith(SourceFiles.API_TEST)),
                "Сторож не нашёл ни одного теста :api: перенос тестов молча отключил проверки");
    }

    private static Set<String> apiClasses(Map<Path, String> sources) {
        Set<String> classes = new LinkedHashSet<>();
        sources.forEach((file, text) -> {
            Matcher pkg = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE)
                    .matcher(text);
            assertTrue(pkg.find(), "Файл " + file + " без package: :api не может быть частью пакета");
            classes.add(pkg.group(1) + "." + file.getFileName().toString().replace(".java", ""));
        });
        return classes;
    }

    private static List<String> violations(Map<Path, String> sources, Set<String> apiClasses) {
        List<String> found = new ArrayList<>();
        sources.forEach((file, text) -> {
            Matcher matcher = IMPORT.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.endsWith(".*")) {
                    name = name.substring(0, name.length() - 2);
                }
                if (allowed(name, apiClasses)) {
                    continue;
                }
                found.add(SourceFiles.logical(file) + ": " + name);
            }
        });
        return found;
    }

    private static boolean allowed(String name, Set<String> apiClasses) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        for (String apiClass : apiClasses) {
            if (name.equals(apiClass) || name.startsWith(apiClass + ".")) {
                return true;
            }
        }
        return false;
    }
}
