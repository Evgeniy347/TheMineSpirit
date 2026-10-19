package com.theminespirit.core.tactics;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ScenarioFile {
    private static final String RESOURCE = "/tactics/scenarios.json";
    private static final long DEFAULT_TICK = 1000;

    private ScenarioFile() {}

    public record Scenario(
            String name,
            Situation situation,
            TacticsMemory memory,
            long tick,
            Expectation expect,
            Optional<String> why,
            boolean boundary,
            Optional<String> pair) {}

    public record Expectation(
            Optional<TacticalOption> decision,
            Optional<TacticalAction> action,
            Optional<RetreatMethod> retreat,
            Optional<String> rule,
            Optional<CombatMode> mode,
            Optional<TacticalState> state,
            Optional<Boolean> callHelp,
            Optional<Boolean> returnTo) {}

    public static String text() {
        try (InputStream stream = ScenarioFile.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Нет ресурса " + RESOURCE + ": сценарии тактики лежат в src/test/resources");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Не прочитан " + RESOURCE, e);
        }
    }

    public static List<Scenario> load() {
        return parse(text());
    }

    static List<Scenario> parse(String json) {
        ConfigNode root = ConfigNode.root("scenarios.json", json).fields(new String[] {"scenarios"});
        List<Scenario> scenarios = new ArrayList<>();
        for (ConfigNode item : root.get("scenarios").nonEmptyItems()) {
            item.fields(
                    new String[] {"name", "expect"},
                    "self",
                    "enemies",
                    "env",
                    "orders",
                    "memory",
                    "sinceHit",
                    "sinceStrike",
                    "tick",
                    "why",
                    "boundary",
                    "pair");
            scenarios.add(scenario(item));
        }
        return scenarios;
    }

    private static Scenario scenario(ConfigNode item) {
        SituationBuilder builder = SituationBuilder.create();
        if (item.has("self")) {
            self(item.get("self"), builder);
        }
        if (item.has("enemies")) {
            List<ConfigNode> enemies = item.get("enemies").items();
            for (int index = 0; index < enemies.size(); index++) {
                enemy(enemies.get(index), builder, index);
            }
        }
        if (item.has("env")) {
            env(item.get("env"), builder);
        }
        if (item.has("orders")) {
            orders(item.get("orders"), builder);
        }
        if (item.has("sinceHit")) {
            builder.sinceHit(item.get("sinceHit").integer(0, Situation.MAX_TICKS));
        }
        if (item.has("sinceStrike")) {
            builder.sinceStrike(item.get("sinceStrike").integer(0, Situation.MAX_TICKS));
        }
        Situation situation = builder.build();
        long tick = item.has("tick") ? item.get("tick").integer(0, Integer.MAX_VALUE) : DEFAULT_TICK;
        TacticsMemory memory = item.has("memory")
                ? memory(item.get("memory"), situation, tick)
                : TacticsMemory.initial(
                        situation.orders().mode(), tick, situation.self().hpFraction());
        return new Scenario(
                item.get("name").text(),
                situation,
                memory,
                tick,
                expectation(item.get("expect")),
                item.has("why") ? Optional.of(item.get("why").text()) : Optional.empty(),
                item.has("boundary") && item.get("boundary").bool(),
                item.has("pair") ? Optional.of(item.get("pair").text()) : Optional.empty());
    }

    private static void self(ConfigNode node, SituationBuilder builder) {
        node.fields(
                new String[] {},
                "hp",
                "hpFraction",
                "armor",
                "dps",
                "speed",
                "food",
                "blocks",
                "ammo",
                "bucket",
                "allies",
                "heal",
                "at");
        if (node.has("hp")) {
            builder.hp(node.get("hp").number(0, 20));
        }
        if (node.has("hpFraction")) {
            builder.hpFraction(node.get("hpFraction").number(0, 1));
        }
        if (node.has("armor")) {
            builder.armor(node.get("armor").number(0, 20));
        }
        if (node.has("dps")) {
            builder.dps(node.get("dps").number(0, 1000));
        }
        if (node.has("speed")) {
            builder.speed(node.get("speed").number(0, 2));
        }
        if (node.has("food")) {
            builder.food(node.get("food").number(0, 1));
        }
        if (node.has("blocks")) {
            builder.blocks(node.get("blocks").integer(0, 2304));
        }
        if (node.has("ammo")) {
            builder.ammo(node.get("ammo").integer(0, 2304));
        }
        if (node.has("bucket")) {
            builder.bucket(node.get("bucket").bool());
        }
        if (node.has("allies")) {
            builder.allies(node.get("allies").integer(0, 64));
        }
        if (node.has("heal")) {
            builder.healItems(node.get("heal").integer(0, 2304));
        }
        if (node.has("at")) {
            List<ConfigNode> at = node.get("at").items();
            builder.pos(at.get(0).number(-1000, 1000), at.get(1).number(-1000, 1000));
        }
    }

    private static void enemy(ConfigNode node, SituationBuilder builder, int index) {
        node.fields(new String[] {"type", "distance"}, "count", "hp", "armor", "visible", "targeting");
        String type = node.get("type").text();
        double distance = node.get("distance").number(0, 1000);
        int count = node.has("count") ? node.get("count").integer(1, 100) : 1;
        double hp = node.has("hp") ? node.get("hp").number(0, 1) : ScenarioDefaults.ENEMY_HP_FRACTION;
        double armor = node.has("armor") ? node.get("armor").number(0, 20) : ScenarioDefaults.ENEMY_ARMOR;
        boolean visible = !node.has("visible") || node.get("visible").bool();
        boolean targeting = !node.has("targeting") || node.get("targeting").bool();
        String qualified = type.contains(":") ? type : "minecraft:" + type;
        for (int i = 0; i < count; i++) {
            builder.enemy(new EnemyFact(
                    qualified, type + "-" + index + "-" + i, new Point(distance, 0, 0), hp, armor, visible, targeting));
        }
    }

    private static void env(ConfigNode node, SituationBuilder builder) {
        node.fields(new String[] {}, "lava", "drop", "light", "time", "home", "cover", "water");
        if (node.has("lava")) {
            builder.lava(node.get("lava").number(0, EnvFacts.FAR_DISTANCE));
        }
        if (node.has("drop")) {
            builder.drop(node.get("drop").number(0, 400));
        }
        if (node.has("light")) {
            builder.light(node.get("light").integer(0, EnvFacts.MAX_LIGHT));
        }
        if (node.has("time")) {
            builder.time(node.get("time").enumValue(TimeOfDay.class));
        }
        if (node.has("home")) {
            builder.home(node.get("home").number(0, EnvFacts.FAR_DISTANCE));
        }
        if (node.has("cover")) {
            builder.cover(node.get("cover").number(0, EnvFacts.FAR_DISTANCE));
        }
        if (node.has("water")) {
            builder.water(node.get("water").number(0, EnvFacts.FAR_DISTANCE));
        }
    }

    private static void orders(ConfigNode node, SituationBuilder builder) {
        node.fields(new String[] {}, "mode", "zone", "ward", "help");
        if (node.has("mode")) {
            builder.mode(node.get("mode").enumValue(CombatMode.class));
        }
        if (node.has("zone")) {
            List<ConfigNode> zone = node.get("zone").items();
            builder.zone(
                    zone.get(0).number(-1000, 1000),
                    zone.get(1).number(-1000, 1000),
                    zone.get(2).number(1, Orders.MAX_ZONE_RADIUS));
        }
        if (node.has("ward")) {
            List<ConfigNode> ward = node.get("ward").items();
            builder.ward(
                    ward.get(0).number(-1000, 1000),
                    ward.get(1).number(-1000, 1000),
                    ward.get(2).bool());
        }
        if (node.has("help")) {
            builder.help(node.get("help").bool());
        }
    }

    private static TacticsMemory memory(ConfigNode node, Situation situation, long tick) {
        node.fields(new String[] {}, "state", "inState", "sinceSeen", "previous", "by", "decidedAgo", "mode");
        TacticsMemory base = TacticsMemory.initial(
                situation.orders().mode(), tick, situation.self().hpFraction());
        TacticalState state = node.has("state") ? node.get("state").enumValue(TacticalState.class) : base.state();
        long inState = node.has("inState") ? node.get("inState").integer(0, (int) tick) : 0;
        long sinceSeen =
                node.has("sinceSeen") ? node.get("sinceSeen").integer(0, (int) tick) : tick - base.lastSeenTick();
        Optional<TacticalOption> previous = node.has("previous")
                ? Optional.of(node.get("previous").enumValue(TacticalOption.class))
                : Optional.empty();
        DecisionSource by = node.has("by") ? node.get("by").enumValue(DecisionSource.class) : DecisionSource.SCORE;
        long decidedAgo = node.has("decidedAgo") ? node.get("decidedAgo").integer(0, (int) tick) : 0;
        CombatMode mode = node.has("mode") ? node.get("mode").enumValue(CombatMode.class) : base.mode();
        Optional<Point> anchor = state == TacticalState.CALM
                ? Optional.empty()
                : Optional.of(situation.self().pos());
        return new TacticsMemory(
                previous,
                by,
                tick - decidedAgo,
                state,
                tick - inState,
                mode,
                base.orderedMode(),
                anchor,
                tick - sinceSeen,
                base.hpWindowRef(),
                tick);
    }

    private static Expectation expectation(ConfigNode node) {
        node.fields(new String[] {"decision"}, "action", "retreat", "rule", "mode", "state", "callHelp", "returnTo");
        return new Expectation(
                Optional.of(node.get("decision").enumValue(TacticalOption.class)),
                node.has("action") ? Optional.of(node.get("action").enumValue(TacticalAction.class)) : Optional.empty(),
                node.has("retreat")
                        ? Optional.of(node.get("retreat").enumValue(RetreatMethod.class))
                        : Optional.empty(),
                node.has("rule") ? Optional.of(node.get("rule").text()) : Optional.empty(),
                node.has("mode") ? Optional.of(node.get("mode").enumValue(CombatMode.class)) : Optional.empty(),
                node.has("state") ? Optional.of(node.get("state").enumValue(TacticalState.class)) : Optional.empty(),
                node.has("callHelp") ? Optional.of(node.get("callHelp").bool()) : Optional.empty(),
                node.has("returnTo") ? Optional.of(node.get("returnTo").bool()) : Optional.empty());
    }
}
