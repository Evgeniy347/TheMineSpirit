package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BreakSightGuardTests {
    private static final Path NEOFORGE = Path.of("src/main/java/com/theminespirit/neoforge");
    private static final Pattern DIRECT_DESTROY = Pattern.compile("\\b(?:destroyBlock|destroyBlockProgress)\\s*\\(");
    private static final Pattern UNLIMITED = Pattern.compile("\\.begin(?:AnyDistance|Chop)\\s*\\(");
    private static final List<String> UNLIMITED_ALLOWED =
            List.of("TreeChop.java", "HarvestSession.java", "FarmSession.java");
    private static final String BREAKER = "BlockBreaker.java";

    @Test
    void productionSources_BreakWithoutSight_AreReported() {
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(NEOFORGE)),
                "Ломка блока сквозь стену возвращается: дух обязан видеть цель из глаз, как игрок. Цена — дух "
                        + "рубит и копает через стены и потолки, игрок видит чит. Как надо: прямой вызов "
                        + "destroyBlock только в BlockBreaker, безлимит по расстоянию (beginAnyDistance/beginChop) — "
                        + "только из рубки и сбора урожая, и BlockBreaker.begin проверяет BreakSight. Нарушители");
    }

    @Test
    void patternSource_DirectDestroyOutsideBreaker_IsReported() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("neoforge/Other.java"), "class Other { void f() { level.destroyBlock(pos, false); } }");
        sources.put(
                Path.of("neoforge/" + BREAKER), breaker("BreakSight.visible(spirit, target, false);\nactive = true;"));

        assertEquals(
                List.of("neoforge/Other.java:1 destroyBlock("),
                violations(sources),
                "Сторож ослеп: прямой destroyBlock вне BlockBreaker обязан ловиться — иначе стену можно сломать мимо проверки");
    }

    @Test
    void patternSource_UnlimitedBreakOutsideAllowedFiles_IsReported() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("neoforge/Other.java"), "class Other { void f() { breaker.beginAnyDistance(pos); } }");
        sources.put(
                Path.of("neoforge/" + BREAKER), breaker("BreakSight.visible(spirit, target, false);\nactive = true;"));

        assertEquals(
                List.of("neoforge/Other.java:1 beginAnyDistance("),
                violations(sources),
                "Сторож ослеп: обходной путь безлимитной ломки вне разрешённого списка обязан ловиться");
    }

    @Test
    void patternSource_ChopAndHarvestUnlimited_AreAccepted() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(
                Path.of("neoforge/TreeChop.java"), "class TreeChop { void f() { breaker.beginChop(pos, false); } }");
        sources.put(
                Path.of("neoforge/HarvestSession.java"),
                "class HarvestSession { void f() { breaker.beginAnyDistance(pos); } }");
        sources.put(
                Path.of("neoforge/" + BREAKER), breaker("BreakSight.visible(spirit, target, false);\nactive = true;"));

        assertEquals(
                List.of(),
                violations(sources),
                "Сторож ложно срабатывает: рубка и сбор урожая — известные места безлимита, они разрешены");
    }

    @Test
    void patternSource_BreakerWithoutSightCheck_IsReported() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(Path.of("neoforge/" + BREAKER), breaker("active = true;"));

        assertEquals(
                List.of("neoforge/BlockBreaker.java: ломка начинается без BreakSight.visible"),
                violations(sources),
                "Сторож ослеп: BlockBreaker без проверки видимости обязан ловиться — иначе ломать сквозь стены снова можно");
    }

    private static String breaker(String body) {
        return "class BlockBreaker { void begin() {\n" + body + "\n} }";
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        sources.forEach((path, text) -> {
            String name = path.getFileName().toString();
            if (!name.equals(BREAKER) && !text.contains("BreakSight.visible(")) {
                collect(found, path, text, DIRECT_DESTROY, "destroyBlock(");
            }
            if (!UNLIMITED_ALLOWED.contains(name)) {
                collect(found, path, text, UNLIMITED, "begin");
            }
        });
        Path breaker = sources.keySet().stream()
                .filter(path -> path.getFileName().toString().equals(BREAKER))
                .findFirst()
                .orElse(null);
        if (breaker != null) {
            String text = sources.get(breaker);
            int sight = text.indexOf("BreakSight.visible(");
            int active = text.indexOf("active = true;");
            if (sight < 0 || active < 0 || sight > active) {
                found.add(breaker.toString().replace('\\', '/') + ": ломка начинается без BreakSight.visible");
            }
        }
        return found;
    }

    private static void collect(List<String> found, Path path, String text, Pattern pattern, String marker) {
        Matcher matcher = pattern.matcher(SourceFiles.withoutTextBlocks(text));
        while (matcher.find()) {
            String line = marker.equals("begin")
                    ? matcher.group().substring(matcher.group().indexOf('.') + 1)
                    : marker;
            found.add(
                    path.toString().replace('\\', '/') + ":" + SourceFiles.lineOf(text, matcher.start()) + " " + line);
        }
    }
}
