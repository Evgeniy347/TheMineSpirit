package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TacticsProperties {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CORNERED = "cornered_fight";
    private static final Set<String> NO_ROUTE_FIGHT = CONFIG.rules().hard().stream()
            .filter(rule -> rule.noRouteOption().equals(Optional.of(TacticalOption.FIGHT))).map(HardRule::id)
            .collect(Collectors.toSet());

    record Case(Situation situation, TacticsMemory memory, long tick) {
    }

    @Provide
    Arbitrary<Case> cases() {
        return Situations.any().flatMap(TacticsProperties::withMemory);
    }

    @Provide
    Arbitrary<Case> criticalCases() {
        return hostile(Arbitraries.just(CombatMode.AGGRESSIVE), 0.2);
    }

    @Provide
    Arbitrary<Case> passiveCases() {
        return hostile(Arbitraries.just(CombatMode.PASSIVE), -1);
    }

    @Provide
    Arbitrary<Case> guardCases() {
        return hostile(Arbitraries.of(CombatMode.GUARD_AREA, CombatMode.DEFENSIVE, CombatMode.AGGRESSIVE), -1);
    }

    private static Arbitrary<Case> hostile(Arbitrary<CombatMode> modes, double hpFraction) {
        return Situations.anyWith(modes, 8).filter(situation -> !situation.enemies().isEmpty())
                .map(situation -> invincible(situation, hpFraction)).flatMap(TacticsProperties::withMemory);
    }

    private static Arbitrary<Case> withMemory(Situation situation) {
        Arbitrary<TacticsMemory> memory = Combinators.combine(
                Arbitraries.of(Optional.<TacticalOption>empty(), Optional.of(TacticalOption.FIGHT),
                        Optional.of(TacticalOption.RETREAT), Optional.of(TacticalOption.IGNORE)),
                Arbitraries.of(TacticalState.values()), Arbitraries.integers().between(0, 300),
                Arbitraries.integers().between(0, 300), Arbitraries.of(true, false),
                Arbitraries.of(true, false))
                .as((previous, state, decidedAgo, inState, degraded, anchored) -> memoryOf(situation, previous, state,
                        decidedAgo, inState, degraded, anchored));
        return memory.map(value -> new Case(situation, value, 1000));
    }

    private static TacticsMemory memoryOf(Situation situation, Optional<TacticalOption> previous,
            TacticalState state, int decidedAgo, int inState, boolean degraded, boolean anchored) {
        CombatMode ordered = situation.orders().mode();
        CombatMode mode = degraded && ordered != CombatMode.PASSIVE ? CombatMode.DEFENSIVE : ordered;
        Optional<Point> anchor = anchored && state != TacticalState.CALM ? Optional.of(situation.self().pos())
                : Optional.empty();
        return new TacticsMemory(previous, DecisionSource.SCORE, 1000 - decidedAgo, state, 1000 - inState, mode, ordered, anchor, 900,
                situation.self().hpFraction(), 1000);
    }

    private static Situation invincible(Situation situation, double hpFraction) {
        SelfFacts self = situation.self();
        double hp = hpFraction < 0 ? self.hp() : self.maxHp() * hpFraction;
        List<EnemyFact> hostile = situation.enemies().stream().map(enemy -> new EnemyFact(enemy.type(), enemy.id(),
                enemy.pos(), enemy.hpFraction(), enemy.armor(), true, true, enemy.count())).toList();
        return Situations.withEnemies(Situations.withSelf(situation, Situations.withStats(self, hp, 20, 1000)), hostile);
    }

    private static boolean anyTargeting(Situation situation) {
        return situation.enemies().stream().anyMatch(EnemyFact::targetingSelf);
    }

    private static boolean fightJustified(Situation situation, String rule) {
        if (rule.equals(CORNERED)) {
            return true;
        }
        return NO_ROUTE_FIGHT.contains(rule) && anyTargeting(situation);
    }

    private static TacticalOutcome run(Case c) {
        return new TacticsEngine(CONFIG).step(c.situation(), c.memory(), c.tick());
    }

    @Property
    void step_AnyAdmissibleFacts_ReturnsConsistentDecisionAndAction(@ForAll("cases") Case c) {
        TacticalOutcome outcome = run(c);

        assertEquals(outcome.decision(), outcome.action().option(),
                "Действие " + outcome.action() + " обязано согласоваться с решением " + outcome.decision()
                        + ": иначе адаптер получит «драться и бежать» одновременно");
        assertTrue(outcome.decision() == TacticalOption.RETREAT || outcome.retreatMethod() == RetreatMethod.NONE,
                "Способ отхода называется только при решении RETREAT: " + outcome.retreatMethod());
        assertTrue(outcome.decision() == TacticalOption.RETREAT || !outcome.callHelp(),
                "Зов на помощь - побочное действие отхода, при другом решении его нет");
    }

    @Property
    void step_AnyAdmissibleCase_RetreatAlwaysNamesAWay(@ForAll("cases") Case c) {
        TacticalOutcome outcome = run(c);

        assertTrue(outcome.decision() != TacticalOption.RETREAT || outcome.retreatMethod() != RetreatMethod.NONE,
                "Решение RETREAT без способа отхода (rule " + outcome.trace().rule() + ") отправило бы духа в никуда: "
                        + "если способов нет совсем, замена по правилам noRoute");
    }

    @Property
    void step_TwoEnginesSameInput_ReturnEqualOutcomesIncludingTrace(@ForAll("cases") Case c) {
        assertEquals(run(c), run(c),
                "Тактика детерминирована: те же факты и память дают побитово тот же результат вместе с trace");
    }

    @Property
    void step_ShuffledEnemies_ReturnSameOutcome(@ForAll("cases") Case c, @ForAll long seed) {
        List<EnemyFact> shuffled = new ArrayList<>(c.situation().enemies());
        Collections.shuffle(shuffled, new Random(seed));
        Situation original = c.situation();
        Situation permuted = new Situation(original.self(), shuffled, original.env(), original.orders(),
                original.ticksSinceHit(), original.ticksSinceStrike());

        assertEquals(run(c), run(new Case(permuted, c.memory(), c.tick())),
                "Порядок, в котором адаптер нашёл врагов, не должен менять решение");
    }

    @Property
    void step_CriticalHpWithEnemies_NeverFightsExceptCornered(@ForAll("criticalCases") Case c) {
        TacticalOutcome outcome = run(c);

        if (outcome.decision() == TacticalOption.FIGHT) {
            assertTrue(fightJustified(c.situation(), outcome.trace().rule()),
                    "При HP 20 % и врагах рядом бой допустим только когда загнан в угол (враг ближе 3 блоков) или отступать некуда, "
                            + "а враг целится в духа; решило правило " + outcome.trace().rule()
                            + ": иначе дух героически гибнет вместо отхода");
        }
    }

    @Property
    void step_PassiveMode_NeverFightsExceptCornered(@ForAll("passiveCases") Case c) {
        TacticalOutcome outcome = run(c);

        if (!c.situation().enemies().isEmpty() && outcome.decision() == TacticalOption.FIGHT) {
            assertTrue(fightJustified(c.situation(), outcome.trace().rule()),
                    "Пассивный режим не нападает: бой только если загнан в угол (враг ближе 3 блоков) или отступать некуда, "
                            + "а враг целится в духа; решило " + outcome.trace().rule());
        }
    }

    @Property
    void step_BeyondLeashInGuardModes_DoesNotFightUnlessEnemyWithinThree(@ForAll("guardCases") Case c) {
        TacticalOutcome outcome = run(c);
        Situation situation = c.situation();
        ModeGeometry geometry = ModeGeometry.of(situation, CONFIG.modes().of(outcome.mode()), c.memory().anchor());
        double nearest = situation.enemies().stream().mapToDouble(situation::distance).min().orElse(Double.MAX_VALUE);
        boolean guarded = outcome.mode() == CombatMode.GUARD_AREA || outcome.mode() == CombatMode.DEFENSIVE;

        if (guarded && geometry.leashLeft() < 0 && nearest > 3) {
            assertTrue(outcome.decision() != TacticalOption.FIGHT || (NO_ROUTE_FIGHT.contains(outcome.trace().rule())
                    && anyTargeting(situation)),
                    "За поводком (остаток " + geometry.leashLeft() + ") в режиме " + outcome.mode()
                            + " враг дальше 3 блоков не повод драться (кроме замены noRoute, когда враг целится в духа и бежать некуда); "
                            + "решило " + outcome.trace().rule() + ": дух не преследует за пределы зоны");
        }
    }

    @Property
    void step_ConfigWithShuffledKeysAndWhitespace_GivesSameOutcome(@ForAll("cases") Case c, @ForAll long seed) {
        TacticsConfig shuffled = shuffledConfig(seed);

        assertEquals(run(c), new TacticsEngine(shuffled).step(c.situation(), c.memory(), c.tick()),
                "Порядок ключей и пробелы в JSON не влияют на поведение: значение имеет только содержание файлов");
    }

    private static TacticsConfig shuffledConfig(long seed) {
        Random random = new Random(seed);
        return TacticsConfig.fromJson(rewrite("enemies.json", random), rewrite("weights.json", random),
                rewrite("rules.json", random), rewrite("modes.json", random));
    }

    private static String rewrite(String file, Random random) {
        try {
            JsonNode shuffled = shuffleKeys(MAPPER.readTree(TacticsTexts.read(file)), random);
            return random.nextBoolean() ? MAPPER.writeValueAsString(shuffled)
                    : MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(shuffled);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Тест не смог переписать " + file, e);
        }
    }

    private static JsonNode shuffleKeys(JsonNode node, Random random) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
                names.add(it.next());
            }
            Collections.shuffle(names, random);
            ObjectNode result = MAPPER.createObjectNode();
            names.forEach(name -> result.set(name, shuffleKeys(node.get(name), random)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            node.forEach(item -> result.add(shuffleKeys(item, random)));
            return result;
        }
        return node;
    }
}
