package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SituationValidationTests {
    private static final Point ORIGIN = new Point(0, 0, 0);

    private static SelfFacts self(double hp, double maxHp, double armor, double dps, double speed, double food,
            int blocks, int allies) {
        return new SelfFacts(ORIGIN, hp, maxHp, armor, dps, speed, food, blocks, 0, false, allies, 0);
    }

    private static EnemyFact enemy(String type, String id, double hpFraction, double armor, int count) {
        return new EnemyFact(type, id, ORIGIN, hpFraction, armor, true, true, count);
    }

    private static EnvFacts env(double lava, double drop, int light, double home) {
        return new EnvFacts(lava, drop, light, TimeOfDay.DAY, home, true, 5, 5);
    }

    static Stream<Arguments> rejectedFacts() {
        return Stream.of(
                row("HP больше максимума", () -> self(21, 20, 0, 5, 0.3, 1, 0, 0), "SelfFacts.hp"),
                row("HP отрицательный", () -> self(-1, 20, 0, 5, 0.3, 1, 0, 0), "SelfFacts.hp"),
                row("HP не число", () -> self(Double.NaN, 20, 0, 5, 0.3, 1, 0, 0), "SelfFacts.hp"),
                row("максимум HP нулевой", () -> self(0, 0, 0, 5, 0.3, 1, 0, 0), "SelfFacts.maxHp"),
                row("броня больше 20", () -> self(20, 20, 20.5, 5, 0.3, 1, 0, 0), "SelfFacts.armor"),
                row("броня отрицательная", () -> self(20, 20, -0.1, 5, 0.3, 1, 0, 0), "SelfFacts.armor"),
                row("урон бесконечный", () -> self(20, 20, 0, Double.POSITIVE_INFINITY, 0.3, 1, 0, 0),
                        "SelfFacts.dps"),
                row("скорость отрицательная", () -> self(20, 20, 0, 5, -0.3, 1, 0, 0), "SelfFacts.speed"),
                row("еда больше доли 1", () -> self(20, 20, 0, 5, 0.3, 1.2, 0, 0), "SelfFacts.food"),
                row("блоков отрицательно", () -> self(20, 20, 0, 5, 0.3, 1, -1, 0), "SelfFacts.blocks"),
                row("союзников слишком много", () -> self(20, 20, 0, 5, 0.3, 1, 0, 65), "SelfFacts.allies"),
                row("доля HP врага больше 1", () -> enemy("minecraft:zombie", "a", 1.01, 0, 1),
                        "EnemyFact.hpFraction"),
                row("броня врага больше 20", () -> enemy("minecraft:zombie", "a", 1, 21, 1), "EnemyFact.armor"),
                row("число врагов в записи нулевое", () -> enemy("minecraft:zombie", "a", 1, 0, 0),
                        "EnemyFact.count"),
                row("тип врага пустой", () -> enemy(" ", "a", 1, 0, 1), "EnemyFact.type"),
                row("расстояние до лавы отрицательное", () -> env(-1, 0, 15, 5), "EnvFacts.lavaNear"),
                row("высота обрыва не число", () -> env(5, Double.NaN, 15, 5), "EnvFacts.dropHeight"),
                row("свет больше 15", () -> env(5, 0, 16, 5), "EnvFacts.light"),
                row("дом дальше предела", () -> env(5, 0, 15, 5000), "EnvFacts.homeDistance"),
                row("зона с радиусом без центра", () -> new Orders(CombatMode.GUARD_AREA, null, 10, null, false, false),
                        "Orders.zoneCenter"),
                row("зона с центром без радиуса", () -> new Orders(CombatMode.GUARD_AREA, ORIGIN, 0, null, false, false),
                        "Orders.zoneCenter"),
                row("угроза подопечному без подопечного",
                        () -> new Orders(CombatMode.PROTECT, null, 0, null, true, false), "Orders.wardThreatened"),
                row("точка за пределами мира", () -> new Point(4.0E7, 0, 0), "Point.x"),
                row("ticksSinceHit отрицательный",
                        () -> new Situation(self(20, 20, 0, 5, 0.3, 1, 0, 0), List.of(), env(5, 0, 15, 5),
                                Orders.mode(CombatMode.DEFENSIVE), -1),
                        "Situation.ticksSinceHit"));
    }

    private static Arguments row(String name, Executable construction, String field) {
        return Arguments.of(name, construction, field);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedFacts")
    void facts_InvalidValue_ThrowWithFieldName(String name, Executable construction, String field) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, construction,
                "Факт «" + name + "» недопустим: адаптер, передавший такое, ошибся, а тактика не должна считать по мусору");

        assertTrue(error.getMessage().contains(field),
                "Сообщение называет поле " + field + ", чтобы адаптер сразу видел, что именно он передал неверно, "
                        + "а получили: " + error.getMessage());
    }

    @Test
    void facts_BoundaryValues_AreAccepted() {
        SelfFacts weakest = self(0, 1, 0, 0, 0, 0, 0, 0);
        SelfFacts strongest = self(1024, 1024, 20, 1000, 2, 1, 2304, 64);

        assertEquals(0.0, weakest.hpFraction(), 0.0,
                "Нулевое HP на границе допустимо: дух умирает, и тактика обязана считать этот кадр без исключения");
        assertEquals(1.0, strongest.hpFraction(), 0.0, "Полное HP на верхней границе допустимо");
    }

    @Test
    void ordersMode_OnlyMode_HasNoZoneAndNoWard() {
        Orders orders = Orders.mode(CombatMode.DEFENSIVE);

        assertEquals(List.of(false, false), List.of(orders.zoneSet(), orders.wardPresent()),
                "Приказ только с режимом не задаёт ни зону, ни подопечного: иначе guard_area без зоны не вернулся бы в defensive");
    }
}
