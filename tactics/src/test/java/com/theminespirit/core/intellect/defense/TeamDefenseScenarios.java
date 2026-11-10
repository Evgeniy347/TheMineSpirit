package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TacticsConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TeamDefenseScenarios {
    static final Point CENTER = new Point(0, 64, 0);
    static final double STAGE_ONE = 45;
    static final double STAGE_TWO = 70;
    static final double STAGE_THREE = 90;
    static final EnemyProfiles PROFILES = TacticsConfig.loadDefault().enemies();
    static final DefenseProfile PROFILE = DefenseProfile.loadDefault();
    static final DefenseWeights WEIGHTS = DefenseWeights.loadDefault();

    private TeamDefenseScenarios() {}

    record SquadSpec(
            String name,
            double x,
            double z,
            int size,
            double hp,
            int armor,
            boolean bow,
            double dps,
            double food,
            Set<DefensePotion> potions) {}

    record MobSpec(String type, int count, double x, double z) {}

    record Case(
            String id,
            String situation,
            List<SquadSpec> squads,
            List<MobSpec> mobs,
            Map<String, SquadDecision> decisions,
            Map<String, Set<CombatStance>> stances) {}

    static SquadSpec squad(
            String name, double x, double z, int size, double hp, int armor, boolean bow, double dps, double food) {
        return new SquadSpec(name, x, z, size, hp, armor, bow, dps, food, Set.of());
    }

    static SquadSpec pair(double x, double z) {
        return squad("p", x, z, 2, 1.0, 10, false, 6.0, 1.0);
    }

    static SquadSpec pair(double x, double z, double hp, int armor, double dps) {
        return squad("p", x, z, 2, hp, armor, false, dps, 1.0);
    }

    static SquadSpec pair(double x, double z, double hp, int armor, double dps, DefensePotion... potions) {
        return new SquadSpec("p", x, z, 2, hp, armor, false, dps, 1.0, Set.of(potions));
    }

    static SquadSpec hungryPair(double x, double z, double food) {
        return squad("h", x, z, 2, 1.0, 10, false, 6.0, food);
    }

    static SquadSpec archerPair(double x, double z) {
        return squad("a", x, z, 2, 1.0, 10, true, 3.0, 1.0);
    }

    static SquadSpec single(double x, double z, double hp, int armor, boolean bow, double dps) {
        return squad("s", x, z, 1, hp, armor, bow, dps, 1.0);
    }

    static SquadSpec single(
            double x, double z, double hp, int armor, boolean bow, double dps, DefensePotion... potions) {
        return new SquadSpec("s", x, z, 1, hp, armor, bow, dps, 1.0, Set.of(potions));
    }

    static SquadSpec singleFood(double x, double z, double food) {
        return squad("f", x, z, 1, 1.0, 10, false, 6.0, food);
    }

    static List<SquadSpec> ring(double radius, int count) {
        List<SquadSpec> squads = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double angle = -Math.PI / 2 + index * 2 * Math.PI / count;
            squads.add(pair(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
        return squads;
    }

    static MobSpec mob(String type, int count, double x, double z) {
        return new MobSpec(type, count, x, z);
    }

    static MobSpec zombies(int count, double x, double z) {
        return mob("minecraft:zombie", count, x, z);
    }

    static MobSpec spiders(int count, double x, double z) {
        return mob("minecraft:spider", count, x, z);
    }

    static MobSpec skeletons(int count, double x, double z) {
        return mob("minecraft:skeleton", count, x, z);
    }

    static MobSpec creepers(int count, double x, double z) {
        return mob("minecraft:creeper", count, x, z);
    }

    static TeamDefensePlan plan(Case scenario) {
        List<SquadSpec> specs = new ArrayList<>(scenario.squads());
        specs.sort(Comparator.comparingDouble(SquadSpec::x).thenComparingDouble(SquadSpec::z));
        List<DefenseMember> members = new ArrayList<>();
        for (int squad = 0; squad < specs.size(); squad++) {
            SquadSpec spec = specs.get(squad);
            for (int index = 0; index < spec.size(); index++) {
                members.add(member(spec, squad, index));
            }
        }
        List<EnemyFact> enemies = new ArrayList<>();
        for (int index = 0; index < scenario.mobs().size(); index++) {
            MobSpec mob = scenario.mobs().get(index);
            enemies.add(new EnemyFact(
                    mob.type(), "mob-" + index, new Point(mob.x(), 64, mob.z()), 1.0, 0, true, true, mob.count()));
        }
        return TeamDefensePlanner.plan(CENTER, members, enemies, PROFILES, PROFILE, WEIGHTS);
    }

    private static DefenseMember member(SquadSpec spec, int squad, int index) {
        UUID id = UUID.nameUUIDFromBytes((spec.name() + "-" + squad + "-" + index).getBytes(StandardCharsets.UTF_8));
        double offset = spec.size() == 1 ? 0 : (index - (spec.size() - 1) / 2.0) * 0.8;
        return new DefenseMember(
                id,
                new Point(spec.x() + offset, 64, spec.z()),
                spec.hp(),
                spec.armor(),
                spec.bow(),
                spec.dps(),
                spec.food(),
                spec.potions());
    }

    static Case caseOf(
            String id, String situation, List<SquadSpec> squads, List<MobSpec> mobs, String... expectations) {
        return new Case(id, situation, squads, mobs, decisions(expectations), Map.of());
    }

    static Case stanceCase(
            String id,
            String situation,
            List<SquadSpec> squads,
            List<MobSpec> mobs,
            CombatStance expected,
            String... expectations) {
        Map<String, Set<CombatStance>> stances = new LinkedHashMap<>();
        Map<String, SquadDecision> decisions = decisions(expectations);
        for (Map.Entry<String, SquadDecision> entry : decisions.entrySet()) {
            if (entry.getValue() != SquadDecision.STAY && entry.getValue() != SquadDecision.EAT) {
                stances.put(entry.getKey(), Set.of(expected));
            }
        }
        return new Case(id, situation, squads, mobs, decisions, stances);
    }

    private static Map<String, SquadDecision> decisions(String... expectations) {
        Map<String, SquadDecision> decisions = new LinkedHashMap<>();
        for (int index = 0; index + 1 < expectations.length; index += 2) {
            decisions.put(expectations[index], SquadDecision.valueOf(expectations[index + 1]));
        }
        return decisions;
    }

    static SquadOrder orderOf(TeamDefensePlan plan, String squad) {
        return plan.orders().stream()
                .filter(order -> order.squad().equals(squad))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Отряд '" + squad + "' не найден в плане: имена отрядов строятся по месту"));
    }

    static void verify(Case scenario) {
        TeamDefensePlan plan = plan(scenario);
        assertEquals(
                scenario.squads().size(),
                plan.orders().size(),
                "Сценарий " + scenario.id() + " (" + scenario.situation() + "): у каждого отряда ровно один "
                        + "приказ, иначе часть духов останется без решения");
        int members = scenario.squads().stream().mapToInt(SquadSpec::size).sum();
        assertEquals(
                members,
                plan.memberOrders().size(),
                "Сценарий " + scenario.id() + ": каждый дух получает приказ, иначе команда распадается");
        assertEquals(
                members,
                plan.memberOrders().stream().map(MemberOrder::member).distinct().count(),
                "Сценарий " + scenario.id() + ": один дух получает один приказ, иначе приказы противоречат друг другу");
        scenario.decisions().forEach((squad, decision) -> {
            SquadOrder order = orderOf(plan, squad);
            assertEquals(
                    decision,
                    order.decision(),
                    "Сценарий " + scenario.id() + " (" + scenario.situation() + "): отряд " + squad + " должен решить "
                            + decision + ", а решил " + order.decision());
            switch (decision) {
                case STAY -> {
                    assertEquals(
                            0,
                            order.targets(),
                            "Сценарий " + scenario.id() + ": стоящий отряд не берёт цель, иначе он уходит с поста");
                    assertEquals(
                            plan.squads().stream()
                                    .filter(value -> value.id().equals(squad))
                                    .findFirst()
                                    .orElseThrow()
                                    .center(),
                            order.post(),
                            "Сценарий " + scenario.id() + ": стоящий отряд остаётся на своей точке");
                }
                case ENGAGE ->
                    assertTrue(
                            order.targets() > 0,
                            "Сценарий " + scenario.id()
                                    + ": дерущийся отряд обязан иметь цель, иначе он стоит без дела");
                case REINFORCE ->
                    assertTrue(
                            order.targets() > 0,
                            "Сценарий " + scenario.id() + ": идущий на помощь отряд обязан видеть цель");
                case RETREAT -> {
                    assertEquals(
                            0,
                            order.targets(),
                            "Сценарий " + scenario.id() + ": отходящий отряд не берёт цель, иначе гибнет на месте");
                    assertEquals(
                            CENTER,
                            order.post(),
                            "Сценарий " + scenario.id() + ": отход ведёт к центру обороны, а не в бой");
                }
                case EAT ->
                    assertEquals(
                            0,
                            order.targets(),
                            "Сценарий " + scenario.id() + ": голодный отряд сперва ест, а не берёт цель");
                case SPREAD ->
                    assertTrue(
                            order.sectors().size() >= 2,
                            "Сценарий " + scenario.id() + ": рассредоточение накрывает хотя бы два сектора");
            }
        });
        scenario.stances().forEach((squad, expected) -> {
            SquadOrder order = orderOf(plan, squad);
            Set<CombatStance> actual = new HashSet<>(order.memberStances().values());
            assertEquals(
                    expected,
                    actual,
                    "Сценарий " + scenario.id() + " (" + scenario.situation() + "): стойки отряда " + squad
                            + " должны быть " + expected + ", а вышли " + actual);
        });
    }
}
