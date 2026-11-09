package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlotBreakPathGuardTests {
    private static final Set<String> REMOVALS =
            Set.of("destroyBlock(", "removeBlock(", "Blocks.AIR.defaultBlockState()");
    private static final String SINGLE_POINT = "neoforge/BlockBreaker.java";
    private static final String SPIRIT_POINT = "neoforge/SpiritBlocks.java";
    private static final String DEMO = "neoforge/demo/DemoSession.java";
    private static final Set<String> ALLOWED = Set.of(
            SINGLE_POINT,
            "neoforge/BreakDestroy.java",
            SPIRIT_POINT,
            DEMO,
            "neoforge/PillarDismantler.java",
            "neoforge/FarmNetherWartScript.java",
            "neoforge/StripLogScript.java");

    @Test
    void productionSources_DirectBlockRemoval_OnlyKnownSites() {
        List<String> offenders = new ArrayList<>();
        for (Path root : SourceFiles.moduleMainRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            Map<Path, String> sources = SourceFiles.javaUnder(root);
            for (Map.Entry<Path, String> source : sources.entrySet()) {
                String file = logical(source.getKey());
                Set<String> found = removals(source.getValue());
                if (found.isEmpty() || file.startsWith("gametest/")) {
                    continue;
                }
                if (!ALLOWED.contains(file)) {
                    offenders.add(file + ": " + found);
                    continue;
                }
                if (!SINGLE_POINT.equals(file)
                        && !SPIRIT_POINT.equals(file)
                        && !DEMO.equals(file)
                        && !source.getValue().contains("BreakNotice.broken(")) {
                    offenders.add(file + ": " + found + " без BreakNotice.broken — поломка мимо журнала");
                }
            }
        }

        assertEquals(
                List.of(),
                offenders,
                "Дух меняет мир только через BlockBreaker: иначе защита плана и журнал [plot-break] видят не все "
                        + "поломки, и постройка тихо теряет блоки (observe23: дом 100→94). Прямые destroyBlock/"
                        + "removeBlock/воздух разрешены только BlockBreaker (его BreakDestroy), демо-сцене и известным "
                        + "временным путям, и каждый "
                        + "такой путь обязан писать в BreakNotice. Нарушители");
    }

    @Test
    void scan_UnknownDirectRemoval_IsReported() {
        assertEquals(
                Set.of("destroyBlock("),
                removals("class Sample { void run() { level.destroyBlock(pos, false, spirit); } }"),
                "Сторож ослеп: прямой вызов destroyBlock обязан попасть в нарушители");
    }

    @Test
    void scan_AllowedRemovalWithoutJournal_IsReported() {
        List<String> offenders = scan(
                "neoforge/PillarDismantler.java",
                "class Sample { void run() { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); } }");

        assertEquals(
                List.of("neoforge/PillarDismantler.java: [Blocks.AIR.defaultBlockState()] без BreakNotice.broken — "
                        + "поломка мимо журнала"),
                offenders,
                "Сторож ослеп: разрешённый путь без записи в журнал обязан попасть в нарушители");
    }

    @Test
    void scan_AllowedRemovalWithJournal_IsAccepted() {
        assertEquals(
                List.of(),
                scan(
                        "neoforge/PillarDismantler.java",
                        "class Sample { void run() { BreakNotice.broken(server, pos, state, spirit, \"X\", basis); "
                                + "level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); } }"),
                "Сторож ложно срабатывает: путь с записью в журнал законен");
    }

    private static List<String> scan(String file, String text) {
        Set<String> found = removals(text);
        if (found.isEmpty() || !ALLOWED.contains(file)) {
            return List.of();
        }
        if (SINGLE_POINT.equals(file)
                || SPIRIT_POINT.equals(file)
                || DEMO.equals(file)
                || text.contains("BreakNotice.broken(")) {
            return List.of();
        }
        return List.of(file + ": " + found + " без BreakNotice.broken — поломка мимо журнала");
    }

    private static Set<String> removals(String text) {
        Set<String> found = new LinkedHashSet<>();
        for (String removal : REMOVALS) {
            if (text.contains(removal)) {
                found.add(removal);
            }
        }
        return found;
    }

    private static String logical(Path source) {
        String normalized = source.toString().replace('\\', '/');
        for (Path root : SourceFiles.moduleMainRoots()) {
            String prefix = root.toString().replace('\\', '/') + "/";
            if (normalized.startsWith(prefix)) {
                String relative = normalized.substring(prefix.length());
                String packageRoot = "com/theminespirit/";
                return relative.startsWith(packageRoot) ? relative.substring(packageRoot.length()) : relative;
            }
        }
        throw new IllegalStateException(
                "Сторож не нашёл корень исходников для " + source + ": проверьте moduleMainRoots");
    }
}
