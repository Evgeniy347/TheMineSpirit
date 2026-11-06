package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.tactics.TacticsConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class DefenseDataGuardTests {
    @Test
    void prioritiesFile_NonEmpty_HasContent() {
        String json = prioritiesJson();

        assertTrue(
                !json.isBlank(),
                "priorities.json пуст: оборона останется без таблицы приоритетов, и цели выберутся случайно");
    }

    @Test
    void prioritiesData_ArcherOrder_MatchesOwnerSpec() {
        DefenseProfile profile = DefenseProfileParser.fromJson(prioritiesJson());

        assertEquals(
                List.of(TargetClass.CREEPER, TargetClass.SKELETON, TargetClass.RANGED, TargetClass.MELEE),
                profile.orderFor(DefenderRole.ARCHER, true),
                "Порядок лучника из постановки: крипер, скелет, дальние, ближние. Смена порядка — решение "
                        + "владельца: иначе лучник бьёт ближнего вместо крипера");
        assertEquals(
                List.of(TargetClass.MELEE, TargetClass.RANGED),
                profile.orderFor(DefenderRole.MELEE, true),
                "Порядок мечника из постановки: ближние, дальние. Смена порядка — решение владельца: иначе "
                        + "мечник подойдёт к взрыву вместо лучника");
        assertEquals(
                List.of(TargetClass.SKELETON, TargetClass.MELEE, TargetClass.RANGED),
                profile.orderFor(DefenderRole.MELEE, false),
                "Порядок мечника без лучника из постановки: скелет, ближние, дальние. Смена порядка — решение "
                        + "владельца: иначе обстрел останется без ответа");
    }

    @Test
    void prioritiesData_EveryEnemyType_HasExplicitClass() {
        DefenseProfile profile = DefenseProfileParser.fromJson(prioritiesJson());
        Set<String> enemyTypes =
                new TreeSet<>(TacticsConfig.loadDefault().enemies().byType().keySet());
        enemyTypes.remove("default");

        List<String> silent = missingExplicit(enemyTypes, profile.classes());

        assertEquals(
                List.of(),
                silent,
                "Каждый тип из enemies.json обязан иметь явный класс в priorities.json: иначе новый враг молча "
                        + "упадёт в fallback, и его приоритет никто не перепроверит. Без класса");
    }

    @Test
    void prioritiesData_PriorityClasses_AllReal() {
        List<String> names = rawPriorityClasses(prioritiesJson());

        assertTrue(
                !names.isEmpty(),
                "Сторож не нашёл классов в priorities.json: смена схемы молча делает его зелёным на пустом списке");
        assertEquals(
                List.of(),
                unknownClasses(names),
                "Каждый класс в списках приоритета — реальный TargetClass: иначе опечатка даст цели чужой "
                        + "приоритет. Неизвестные");
    }

    @Test
    void missingExplicit_TypeWithoutClass_IsReported() {
        List<String> silent = missingExplicit(
                Set.of("minecraft:zombie", "minecraft:creeper"), Map.of("minecraft:zombie", TargetClass.MELEE));

        assertEquals(
                List.of("minecraft:creeper"),
                silent,
                "Сторож ослеп: тип без явного класса обязан попадать в список молчаливых");
    }

    @Test
    void missingExplicit_AllTypesCovered_ReportsNothing() {
        assertEquals(
                List.of(),
                missingExplicit(Set.of("minecraft:zombie"), Map.of("minecraft:zombie", TargetClass.MELEE)),
                "Сторож ложно срабатывает: накрытые типы не молчаливые");
    }

    @Test
    void unknownClasses_BadName_IsReported() {
        assertEquals(
                List.of("BOSS"),
                unknownClasses(List.of("MELEE", "BOSS")),
                "Сторож ослеп: имя мимо TargetClass обязано находиться");
    }

    @Test
    void unknownClasses_AllReal_ReportsNothing() {
        assertEquals(
                List.of(),
                unknownClasses(List.of("MELEE", "CREEPER")),
                "Сторож ложно срабатывает: реальные классы не нарушения");
    }

    static List<String> missingExplicit(Set<String> enemyTypes, Map<String, TargetClass> classes) {
        List<String> silent = new ArrayList<>();
        for (String type : new TreeSet<>(enemyTypes)) {
            if (!classes.containsKey(type)) {
                silent.add(type);
            }
        }
        return silent;
    }

    static List<String> unknownClasses(List<String> names) {
        Set<String> real = new HashSet<>();
        for (TargetClass cls : TargetClass.values()) {
            real.add(cls.name());
        }
        List<String> unknown = new ArrayList<>();
        for (String name : names) {
            if (!real.contains(name)) {
                unknown.add(name);
            }
        }
        return unknown;
    }

    private static String prioritiesJson() {
        try (InputStream stream =
                DefenseDataGuardTests.class.getResourceAsStream("/data/theminespirit/tactics/priorities.json")) {
            if (stream == null) {
                throw new IllegalStateException("Ресурс /data/theminespirit/tactics/priorities.json не найден "
                        + "в classpath: проверьте, что файл лежит в src/main/resources");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("priorities.json не прочитан: проверьте права и кодировку UTF-8", e);
        }
    }

    private static List<String> rawPriorityClasses(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            List<String> names = new ArrayList<>();
            for (String section : List.of("priority", "priorityWithoutArcher")) {
                JsonNode table = root.get(section);
                if (table == null || !table.isObject()) {
                    throw new IllegalStateException("Секция " + section + " не найдена или не объект: "
                            + "смена схемы молча делает проверку пустой");
                }
                for (Iterator<Map.Entry<String, JsonNode>> roles = table.fields(); roles.hasNext(); ) {
                    for (JsonNode item : roles.next().getValue()) {
                        names.add(item.asText());
                    }
                }
            }
            return names;
        } catch (IOException e) {
            throw new IllegalStateException("priorities.json не разобран сторожем: проверьте синтаксис", e);
        }
    }
}
