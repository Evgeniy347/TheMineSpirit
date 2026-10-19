package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RetreatSelectorTests {
    private static RetreatSelector selector(TacticsConfig config) {
        return new RetreatSelector(config.rules().retreat(), new RuleCounter());
    }

    private static SituationBuilder world() {
        return SituationBuilder.create();
    }

    private static Arguments row(String name, Situation situation, RetreatMethod method, String entry) {
        return Arguments.of(name, situation, method, entry);
    }

    static Stream<Arguments> table() {
        return Stream.of(
                row(
                        "утопленник: вода не спасает, ведро есть",
                        world().enemy("drowned", 6).bucket(true).water(3).build(),
                        RetreatMethod.AWAY,
                        "retreat_away"),
                row(
                        "утопленник и укрытие рядом: идём в укрытие",
                        world().enemy("drowned", 6)
                                .bucket(true)
                                .water(3)
                                .cover(5)
                                .build(),
                        RetreatMethod.COVER,
                        "retreat_cover"),
                row(
                        "скелеты: укрытие, а не баррикада и не столб",
                        world().enemies("skeleton", 12, 2).cover(5).blocks(20).build(),
                        RetreatMethod.COVER,
                        "retreat_cover"),
                row(
                        "скелеты без укрытия и с блоками: отход не строит, бежать некуда",
                        world().enemies("skeleton", 12, 2).blocks(20).build(),
                        RetreatMethod.NONE,
                        ""),
                row(
                        "крипер и блоки в инвентаре: не столб, а бег прочь",
                        world().enemy("creeper", 4).blocks(10).build(),
                        RetreatMethod.AWAY,
                        "retreat_away"),
                row(
                        "фантом и блоки в инвентаре: не баррикада",
                        world().enemy("phantom", 10).blocks(10).build(),
                        RetreatMethod.NONE,
                        ""),
                row(
                        "зомби вплотную и блоки: строить нельзя, бежать поздно",
                        world().enemy("zombie", 2).blocks(7).build(),
                        RetreatMethod.NONE,
                        ""),
                row(
                        "зомби и ведро воды рядом: вода из ведра",
                        world().enemy("zombie", 5).bucket(true).build(),
                        RetreatMethod.WATER,
                        "retreat_water_bucket"),
                row(
                        "зомби и водоём рядом: вода из водоёма полезнее",
                        world().enemy("zombie", 5).bucket(true).water(4).build(),
                        RetreatMethod.WATER,
                        "retreat_water"),
                row(
                        "водоём дальше 8 блоков не годится",
                        world().enemy("zombie", 5).water(8.5).build(),
                        RetreatMethod.AWAY,
                        "retreat_away"),
                row(
                        "близкий дом и скорость выше: дом",
                        world().enemy("zombie", 5).home(20).cover(5).build(),
                        RetreatMethod.HOME,
                        "retreat_home"),
                row(
                        "дом дальше 80 блоков: недоступен",
                        world().enemy("zombie", 5).home(100).cover(10).build(),
                        RetreatMethod.COVER,
                        "retreat_cover"),
                row(
                        "дом при более медленном духе: недоступен",
                        world().enemy("zombie", 5).speed(0.2).home(20).cover(10).build(),
                        RetreatMethod.COVER,
                        "retreat_cover"),
                row(
                        "путь домой недостижим",
                        world().enemy("zombie", 5).cover(10).build(),
                        RetreatMethod.COVER,
                        "retreat_cover"),
                row(
                        "лук и скорость выше: отступление с обстрелом",
                        world().enemy("zombie", 8).ammo(10).build(),
                        RetreatMethod.KITE,
                        "retreat_kite"),
                row(
                        "лук, но враг быстрее: обстрел невозможен",
                        world().enemy("zombie", 8).ammo(10).speed(0.2).build(),
                        RetreatMethod.NONE,
                        ""),
                row(
                        "близкий дом полезнее обстрела",
                        world().enemy("zombie", 8).ammo(10).home(10).build(),
                        RetreatMethod.HOME,
                        "retreat_home"),
                row(
                        "стрелок: укрытие полезнее далёкого дома",
                        world().enemy("skeleton", 12).home(40).cover(6).build(),
                        RetreatMethod.COVER,
                        "retreat_cover"),
                row(
                        "стрелок: близкий дом всё же лучше укрытия",
                        world().enemy("skeleton", 12).home(20).cover(6).build(),
                        RetreatMethod.HOME,
                        "retreat_home"),
                row(
                        "ничего нет, кроме бега прочь",
                        world().enemy("zombie", 5).build(),
                        RetreatMethod.AWAY,
                        "retreat_away"),
                row(
                        "союзник рядом: сбор к нему",
                        world().enemy("zombie", 5).help(true).build(),
                        RetreatMethod.RALLY,
                        "retreat_rally"),
                row(
                        "враг быстрее и ничего нет: бежать бессмысленно",
                        world().enemy("zombie", 5).speed(0.2).build(),
                        RetreatMethod.NONE,
                        ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void evaluate_EnemyAndAvailableWays_ChoosesExpectedMethod(
            String name, Situation situation, RetreatMethod method, String entry) {
        RetreatChoice choice = selector(TacticsConfig.loadDefault())
                .evaluate(Assessments.facts(situation))
                .choice();

        assertEquals(
                List.of(method, entry),
                List.of(choice.method(), choice.rule().orElse("")),
                "Ситуация «" + name + "»: способ отхода выбирается по условиям requires и наибольшей полезности; "
                        + "ошибка здесь отправила бы духа туда, где враг его достанет");
    }

    @Test
    void choice_EqualUtility_FollowsMethodOrderHomeCoverKite() {
        String rules = TacticsTexts.mutate(
                TacticsTexts.read("rules.json"), "\"utility\": {\"base\": 0.4},", "\"utility\": {\"base\": 0.7},");
        TacticsConfig tied = TacticsConfig.fromJson(
                TacticsTexts.read("enemies.json"),
                TacticsTexts.read("weights.json"),
                rules,
                TacticsTexts.read("modes.json"));
        Situation situation = world().enemy("zombie", 8).ammo(10).cover(5).build();

        RetreatChoice choice =
                selector(tied).evaluate(Assessments.facts(situation)).choice();

        assertEquals(
                RetreatMethod.COVER,
                choice.method(),
                "При равной полезности (укрытие 0.7 и обстрел 0.7) выигрывает способ раньше в порядке HOME, COVER, KITE: "
                        + "иначе выбор зависел бы от порядка строк в файле");
    }

    @Test
    void routes_SeveralWaysOfSameMethod_CountsMethodOnce() {
        Situation situation = world().enemy("zombie", 5)
                .bucket(true)
                .water(3)
                .cover(4)
                .home(10)
                .build();

        RetreatOptions options = selector(TacticsConfig.loadDefault()).evaluate(Assessments.facts(situation));

        assertEquals(
                4,
                options.routes(),
                "Водоём и ведро — один способ WATER: доступность отхода считается по способам (дом, укрытие, вода, бег прочь), а не по строкам");
    }

    @Test
    void routes_NothingApplies_IsZero() {
        RetreatOptions options = selector(TacticsConfig.loadDefault())
                .evaluate(
                        Assessments.facts(world().enemy("zombie", 5).speed(0.2).build()));

        assertEquals(
                0,
                options.routes(),
                "Без способов отхода routes = 0: жёсткое правило «отступать некуда» опирается на это число");
    }

    @Test
    void choice_RallyAvailable_ChosenEvenWhenAnotherRouteHasHigherUtility() {
        Situation situation = world().enemy("zombie", 5).home(5).help(true).build();

        RetreatChoice choice = selector(TacticsConfig.loadDefault())
                .evaluate(Assessments.facts(situation))
                .choice();

        assertEquals(
                RetreatMethod.RALLY,
                choice.method(),
                "При доступном сборе дух идёт к союзнику, а не в дом с большей полезностью: зов сильнее укрытия, "
                        + "иначе товарищ остаётся без помощи");
    }

    @Test
    void callHelp_HelpAvailable_IsAddedRegardlessOfMethod() {
        RetreatSelector selector = selector(TacticsConfig.loadDefault());
        Situation withHelp = world().enemy("zombie", 5).help(true).build();
        Situation withoutHelp = world().enemy("zombie", 5).help(false).build();

        assertEquals(
                List.of(true, false),
                List.of(
                        selector.callHelp(Assessments.facts(withHelp)),
                        selector.callHelp(Assessments.facts(withoutHelp))),
                "Зов на помощь добавляется к любому способу отхода, даже к NONE, когда помощь доступна");
    }
}
