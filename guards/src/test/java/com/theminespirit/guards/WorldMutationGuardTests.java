package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldMutationGuardTests {
    private static final Path ROOT = SourceFiles.path("neoforge");
    private static final List<String> MUTATIONS =
            List.of(".setBlock(", ".setBlockAndUpdate(", ".destroyBlock(", ".removeBlock(");
    private static final Map<String, String> EXCEPTIONS = exceptions();

    @Test
    void spiritCode_BlockMutation_GoesThroughSpiritBlocks() {
        List<String> offenders = new ArrayList<>();
        try (var files = Files.walk(ROOT)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = "neoforge/" + ROOT.relativize(file).toString().replace('\\', '/');
                if (EXCEPTIONS.containsKey(relative)) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String mutation : MUTATIONS) {
                    if (text.contains(mutation)) {
                        offenders.add(relative + ": " + mutation);
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Сторож не прочитал исходники духов: " + error.getMessage(), error);
        }

        assertEquals(
                List.of(),
                offenders,
                "Изменение блока мимо SpiritBlocks делает яму нерасследуемой: в block_changes не будет ни автора, "
                        + "ни задания, ни основания. Как надо: зовите SpiritBlocks.place/restore/destroy; "
                        + "исключение — только явный список состояний (дверь, крышка, улей, возраст ягоды) с причиной. "
                        + "Нарушители");
    }

    @Test
    void mutationScan_DirectCall_IsReported() {
        Map<String, String> sources = Map.of("neoforge/Sample.java", "level.setBlock(pos, state, 3);");

        assertEquals(
                List.of("neoforge/Sample.java: .setBlock("),
                offenders(Map.of(), sources),
                "Сторож ослеп: прямой setBlock обязан попасть в нарушители");
    }

    @Test
    void mutationScan_ExplicitException_IsAccepted() {
        Map<String, String> sources = Map.of("neoforge/Sample.java", "level.setBlock(pos, state, 3);");

        assertEquals(
                List.of(),
                offenders(Map.of("neoforge/Sample.java", "крышка сундука"), sources),
                "Сторож ложно срабатывает: явное исключение с причиной обязано приниматься");
    }

    @Test
    void mutationScan_DestroyProgress_IsNotWorldChange() {
        Map<String, String> sources = Map.of("neoforge/Sample.java", "level.destroyBlockProgress(id, pos, 3);");

        assertEquals(
                List.of(),
                offenders(Map.of(), sources),
                "destroyBlockProgress рисует трещины, а не меняет мир: сторож не должен его запрещать");
    }

    private static List<String> offenders(Map<String, String> exceptions, Map<String, String> sources) {
        List<String> offenders = new ArrayList<>();
        sources.forEach((file, text) -> {
            if (exceptions.containsKey(file)) {
                return;
            }
            for (String mutation : MUTATIONS) {
                if (text.contains(mutation)) {
                    offenders.add(file + ": " + mutation);
                    break;
                }
            }
        });
        return List.copyOf(offenders);
    }

    private static Map<String, String> exceptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("neoforge/SpiritBlocks.java", "единственная точка изменения блока: здесь и пишется block_changes");
        map.put(
                "neoforge/blocks/OpenScript.java",
                "открытие двери: меняется только OPEN, блок не появляется и не исчезает");
        map.put("neoforge/doors/CloseDoorScript.java", "закрытие двери: меняется только OPEN, блок не исчезает");
        map.put(
                "neoforge/inventory/ContainerLid.java",
                "крышка сундука/бочки: визуальное состояние, не добыча и не стройка");
        map.put("neoforge/husbandry/HiveSession.java", "улей и костёр: сбор мёда и поджиг, блок остаётся на месте");
        map.put("neoforge/husbandry/HiveHarvest.java", "сбор мёда: меняется только состояние улья");
        map.put("neoforge/husbandry/HiveSmoke.java", "поджиг костра: меняется только состояние костра");
        map.put("neoforge/FarmActions.java", "возраст куста ягод после сбора: состояние растения, а не новый блок");
        map.put(
                "neoforge/demo/DemoSession.java",
                "демо-сцена: строит и убирает декорации, к работе поселения не относится");
        map.put("neoforge/demo/DemoStock.java", "демо-сцена: сундуки материалов и табличка, к поселению не относятся");
        return Map.copyOf(map);
    }
}
