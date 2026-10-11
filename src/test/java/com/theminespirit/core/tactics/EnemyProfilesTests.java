package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnemyProfilesTests {
    private static final List<String> REQUIRED_TYPES = List.of("zombie", "husk", "drowned", "skeleton", "stray",
            "spider", "cave_spider", "creeper", "witch", "enderman", "slime", "phantom", "pillager", "vindicator",
            "ravager", "blaze", "wither_skeleton", "piglin_brute", "warden");
    private static final Pattern KEY = Pattern.compile("minecraft:[a-z_]+");
    private static String text;
    private static EnemyProfiles profiles;

    @BeforeAll
    static void loadDefaultFile() {
        text = TacticsTexts.read("enemies.json");
        profiles = EnemyProfilesParser.parse(ConfigNode.root("enemies.json", text));
    }

    private static EnemyProfile profile(String shortName) {
        return profiles.profile("minecraft:" + shortName);
    }

    @Test
    void defaultFile_Loaded_DescribesAllRequiredTypes() {
        for (String type : REQUIRED_TYPES) {
            assertTrue(profiles.known("minecraft:" + type),
                    "В enemies.json нет профиля " + type + ": такой враг оценивался бы как неизвестный и консервативный");
        }
    }

    @Test
    void defaultFile_Loaded_KeysAreUniqueAndMatchPattern() {
        long entries = text.lines().filter(line -> line.contains("\"type\"")).count();

        assertEquals(entries, profiles.byType().size() + 1L,
                "Каждая строка файла — отдельный тип (плюс default): повтор типа затёр бы профиль молча");
        for (String key : profiles.byType().keySet()) {
            assertTrue(KEY.matcher(key).matches(),
                    "Ключ «" + key + "» не подходит под minecraft:[a-z_]+: адаптер ищет профиль по id сущности");
        }
    }

    @Test
    void defaultFile_Loaded_NumbersMatchDesign() {
        List<Double> zombie = numbers(profile("zombie"));
        List<Double> ravager = numbers(profile("ravager"));
        List<Double> warden = numbers(profile("warden"));

        assertEquals(List.of(20.0, 3.0, 0.23), zombie, "Зомби: hp 20, dps 3, скорость 0.23 — исходная калибровка весов");
        assertEquals(List.of(100.0, 8.0, 0.3), ravager, "Разоритель: hp 100, dps 8, скорость 0.3");
        assertEquals(List.of(500.0, 30.0, 0.3), warden, "Страж: hp 500, dps 30, скорость 0.3");
        assertEquals(List.of(16.0, 2.0, 0.3), numbers(profile("spider")), "Паук: hp 16, dps 2, скорость 0.3");
    }

    private static List<Double> numbers(EnemyProfile profile) {
        return List.of(profile.hp(), profile.dps(), profile.speed());
    }

    @Test
    void defaultFile_Loaded_FlagsMatchBehaviour() {
        assertEquals(List.of(true, true, true, true, true),
                List.of(profile("drowned").swims(), profile("skeleton").ranged(), profile("creeper").explosive(),
                        profile("phantom").flies(), profile("blaze").flies() && profile("blaze").ranged()),
                "Свойства врагов: утопленник плавает, скелет стреляет, крипер взрывается, фантом и блейз летают, блейз стреляет");
        assertFalse(profile("zombie").ranged() || profile("zombie").swims() || profile("zombie").flies(),
                "Зомби — обычный ближний враг: иначе выбор отхода «в воду» ошибся бы на нём");
    }

    @Test
    void profile_UnknownType_ReturnsConservativeDefault() {
        EnemyProfile unknown = profiles.profile("minecraft:never_seen");

        assertEquals(List.of(20.0, 4.0, 1.0), numbers(unknown),
                "Неизвестный тип оценивается консервативно: hp 20, dps 4, скорость 1.0 (быстрый и опасный)");
    }

    static Stream<Arguments> brokenFiles() {
        String file = TacticsTexts.read("enemies.json");
        return Stream.of(
                Arguments.of("дубль типа", TacticsTexts.mutate(file, "minecraft:husk", "minecraft:zombie"),
                        "enemies[1].type"),
                Arguments.of("отрицательный hp", TacticsTexts.mutateFirst(file, "\"hp\": 20", "\"hp\": -5"),
                        "enemies[0].hp"),
                Arguments.of("hp строкой", TacticsTexts.mutateFirst(file, "\"hp\": 20", "\"hp\": \"20\""),
                        "enemies[0].hp"),
                Arguments.of("tier дробью", TacticsTexts.mutateFirst(file, "\"tier\": 1", "\"tier\": 1.5"),
                        "enemies[0].tier"),
                Arguments.of("ключ с заглавной", TacticsTexts.mutate(file, "minecraft:warden", "minecraft:Warden"),
                        "enemies[18].type"),
                Arguments.of("нет default", TacticsTexts.mutate(file, "\"default\"", "\"minecraft:fallback\""),
                        "default"),
                Arguments.of("лишнее поле", TacticsTexts.mutateFirst(file, "\"tier\": 1}", "\"tier\": 1, \"fast\": true}"),
                        "enemies[0]"),
                Arguments.of("нет поля dps", TacticsTexts.mutateFirst(file, "\"dps\": 3, ", ""), "enemies[0].dps"),
                Arguments.of("дубль ключа JSON", TacticsTexts.mutateFirst(file, "\"hp\": 20,", "\"hp\": 20, \"hp\": 21,"),
                        "Duplicate"),
                Arguments.of("пустой список", "{\"enemies\": []}", "enemies"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenFiles")
    void parse_BrokenFile_FailsWithPath(String name, String broken, String path) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> EnemyProfilesParser.parse(ConfigNode.root("enemies.json", broken)),
                "Сломанный enemies.json («" + name + "») обязан отклоняться при загрузке, а не молча менять оценку врагов");

        assertTrue(error.getMessage().contains("enemies.json") && error.getMessage().contains(path),
                "Сообщение называет файл и место сбоя (" + path + "), чтобы правка шла по подсказке, а получили: "
                        + error.getMessage());
    }
}
