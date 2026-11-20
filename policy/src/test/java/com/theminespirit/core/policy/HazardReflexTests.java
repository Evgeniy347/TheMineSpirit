package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.api.PolicyExemptions;
import com.theminespirit.core.policy.api.PolicyId;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HazardReflexTests {
    private static final String S = Pictures.SOLID_LAYER;
    private static final String O = Pictures.OPEN_LAYER;
    private static final BodyFlags HURT = new BodyFlags(false, false, false, true);
    private static final BodyFlags IN_LAVA = new BodyFlags(true, true, false, true);
    private static final BodyFlags BURNING = new BodyFlags(false, true, false, true);
    private static final BodyFlags IN_WATER = new BodyFlags(false, false, true, false);
    private static final BodyFlags BURNING_IN_WATER = new BodyFlags(false, true, true, false);

    record Expected(PolicyId policy, Class<? extends ReflexAction> type, Optional<BlockCoord> cell) {}

    private static Expected clear() {
        return new Expected(null, null, Optional.empty());
    }

    private static Expected flee(int dx, int dy, int dz) {
        return new Expected(PolicyId.LAVA_FIRE, ReflexAction.Flee.class, Optional.of(Pictures.at(dx, dy, dz)));
    }

    private static Expected stop(PolicyId policy) {
        return new Expected(policy, ReflexAction.StopMoving.class, Optional.empty());
    }

    private static Expected exitWater(int dx, int dy, int dz) {
        return new Expected(PolicyId.WATER, ReflexAction.ExitWater.class, Optional.of(Pictures.at(dx, dy, dz)));
    }

    private static Arguments row(
            String name, String floor, String feet, String head, BodyFlags flags, Expected expected) {
        return Arguments.of(name, floor, feet, head, flags, expected);
    }

    static Stream<Arguments> pictures() {
        return Stream.of(
                row("всё безопасно", S, O, O, BodyFlags.CALM, clear()),
                row("лава сбоку вплотную на востоке", S, ".../..L/...", O, BodyFlags.CALM, flee(-1, 0, 0)),
                row("лава на западе", S, ".../L../...", O, BodyFlags.CALM, flee(1, 0, 0)),
                row(
                        "лава на севере: на запад нельзя, лава в кольце соседа",
                        S,
                        ".L./.../...",
                        O,
                        BodyFlags.CALM,
                        flee(0, 0, 1)),
                row("лава на юге", S, ".../.../.L.", O, BodyFlags.CALM, flee(0, 0, -1)),
                row("лава на уровне пола сбоку", "###/##L/###", O, O, BodyFlags.CALM, flee(-1, 0, 0)),
                row("лава под ногами", "###/#L#/###", O, O, BodyFlags.CALM, flee(-1, 0, 0)),
                row("лава в клетке головы", S, O, ".../.L./...", BodyFlags.CALM, flee(-1, 0, 0)),
                row("лава по диагонали на уровне ног не сосед", S, ".../.../..L", O, BodyFlags.CALM, clear()),
                row("огонь вплотную на востоке", S, ".../..F/...", O, BodyFlags.CALM, flee(-1, 0, 0)),
                row("огонь на уровне пола сбоку", "###/##F/###", O, O, BodyFlags.CALM, flee(-1, 0, 0)),
                row("дух стоит в огне и горит", S, ".../.F./...", O, BURNING, flee(-1, 0, 0)),
                row("дух в лаве по флагу", S, ".../.L./...", O, IN_LAVA, flee(-1, 0, 0)),
                row("лавовая яма: выход прыжком на край", S, "###/#L#/###", O, IN_LAVA, flee(-1, 1, 0)),
                row("лавовая яма под потолком: выхода нет", S, "###/#L#/###", S, IN_LAVA, stop(PolicyId.LAVA_FIRE)),
                row("горим, воды рядом нет и опасностей нет", S, O, O, BURNING, clear()),
                row("кактус в клетке ног", S, ".../.H./...", O, BodyFlags.CALM, flee(-1, 0, 0)),
                row("кактус сбоку, урона не было", S, ".../..H/...", O, BodyFlags.CALM, clear()),
                row("кактус сбоку и дух ранен", S, ".../..H/...", O, HURT, flee(-1, 0, 0)),
                row("магма под ногами и дух ранен", "###/#H#/###", O, O, HURT, flee(-1, 0, 0)),
                row("магма под ногами, урона не было", "###/#H#/###", O, O, BodyFlags.CALM, clear()),
                row("паутина в клетке головы не опасность ног", S, O, ".../.H./...", BodyFlags.CALM, clear()),
                row("падающий блок сбоку не опасность", S, ".../..S/...", O, BodyFlags.CALM, clear()),
                row("вода по пояс, берег на востоке", S, "WWW/WW./WWW", O, IN_WATER, exitWater(1, 0, 0)),
                row("вода по шею, берег на востоке", S, "WWW/WW./WWW", "WWW/WW./WWW", IN_WATER, exitWater(1, 0, 0)),
                row("лужа 1x1 по щиколотку", S, ".../.W./...", O, IN_WATER, exitWater(-1, 0, 0)),
                row("озеро без берега", S, "WWW/WWW/WWW", O, IN_WATER, clear()),
                row(
                        "вода под твёрдой крышей: тело замуровано, рефлекс воды не держит",
                        S,
                        "WWW/WWW/WWW",
                        S,
                        IN_WATER,
                        clear()),
                row("бассейн с бортом выше: выход прыжком", S, "###/#W#/###", O, IN_WATER, exitWater(-1, 1, 0)),
                row("лава у берега важнее воды", S, ".../LW./...", O, IN_WATER, flee(1, 0, 0)),
                row(
                        "горящий дух в воде выходит из воды, а не бежит к воде",
                        S,
                        ".../.W./...",
                        O,
                        BURNING_IN_WATER,
                        exitWater(-1, 0, 0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pictures")
    void evaluate_Picture_GivesExpectedVerdict(
            String name, String floor, String feet, String head, BodyFlags flags, Expected expected) {
        Pictures.Picture picture = Pictures.picture(floor, feet, head, flags);

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.empty(), picture.world());

        if (expected.policy() == null) {
            assertEquals(
                    new PolicyVerdict.Clear(),
                    verdict,
                    "Картинка «" + name + "»: опасности нет, дух не должен получать рефлекс, иначе сходит с работы. "
                            + "При смене evaluate проверь безопасные снимки и неопасные соседи вроде паутины");
            return;
        }
        PolicyVerdict.Reflex reflex = assertInstanceOf(
                PolicyVerdict.Reflex.class,
                verdict,
                "Картинка «" + name + "»: ждали рефлекс, получено " + verdict
                        + ", иначе дух молча идёт в опасность. При смене таблицы pictures проверь каждый опасный случай");
        assertEquals(
                expected.policy(),
                reflex.policy(),
                "Картинка «" + name + "»: рефлекс принадлежит другой политике, иначе исключения и лог ищут "
                        + "не ту причину. Причина: " + reflex.reason() + ". При смене порядка проверок сверь PolicyId");
        assertEquals(
                expected.type(),
                reflex.action().getClass(),
                "Картинка «" + name + "»: другое действие рефлекса, иначе исполнитель делает не то, что нужно, "
                        + "чтобы выжить. Причина: " + reflex.reason()
                        + ". При смене приоритетов проверь класс действия");
        if (expected.cell().isPresent()) {
            assertEquals(
                    expected.cell().get(),
                    cellOf(reflex.action()),
                    "Картинка «" + name + "»: духа ведут не в ту клетку, иначе бегство идёт в опасность. " + "Причина: "
                            + reflex.reason() + ". При смене выбора клетки проверь координаты");
        }
    }

    @Test
    void evaluate_WaterPocketUnderLowCeiling_GivesNoExitReflex() {
        Pictures.Picture picture =
                Pictures.pictureWithAbove("###/###/###", "###/#W#/###", "###/#../###", "###/##./###", IN_WATER);

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.empty(), picture.world());

        assertEquals(
                new PolicyVerdict.Clear(),
                verdict,
                "Живой случай -99,52,-417: ноги в воде, голова на воздухе, потолок на блоке 54, берег на 53. "
                        + "Прыжок в потолок невозможен — это не рефлекс выхода, а ловушка: дух обязан получить "
                        + "спасение (dig_out), а не кружить в воде (260 тысяч попыток), иначе дух не спасётся. При смене проверки потолка "
                        + "проверь высоту 54 и берег 53");
    }

    @Test
    void evaluate_WaterPocketWithJumpRoom_GivesExitReflex() {
        Pictures.Picture picture =
                Pictures.pictureWithAbove("###/###/###", "###/#W#/###", "###/#../###", "###/#../###", IN_WATER);

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.empty(), picture.world());

        assertEquals(
                new PolicyVerdict.Reflex(
                        PolicyId.WATER, new ReflexAction.ExitWater(Pictures.at(1, 1, 0)), "дух в воде"),
                verdict,
                "Тот же карман без потолка: прыжок на берег возможен, и рефлекс обязан вести туда, иначе дух "
                        + "остаётся в воде до спасения. При смене проверки места для прыжка сверь клетку над головой");
    }

    @Test
    void evaluate_WaterNoShoreNear_IsClearForRescue() {
        Pictures.Picture picture = Pictures.picture(S, "WWW/WWW/WWW", O, IN_WATER);

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.empty(), picture.world());

        assertEquals(
                new PolicyVerdict.Clear(),
                verdict,
                "Берега рядом нет: рефлекс не может вывести духа, а вечное удержание на поверхности не даёт "
                        + "выполнить спасение (AIR отдельно следит за воздухом под водой), иначе дух висит зря. При смене проверки берега "
                        + "проверь озеро без выхода");
    }

    private static BlockCoord cellOf(ReflexAction action) {
        return switch (action) {
            case ReflexAction.Flee flee -> flee.cell();
            case ReflexAction.ExitWater exit -> exit.cell();
            default -> throw new IllegalStateException("Действие " + action + " не несёт клетки");
        };
    }

    @Test
    void evaluate_BurningWithWaterNearby_FleesToTheWaterCell() {
        BlockCoord water = Pictures.at(3, 0, 0);
        Pictures.Picture picture = Pictures.picture(S, O, O, BURNING, Optional.of(water));

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.empty(), picture.world());

        assertEquals(
                new PolicyVerdict.Reflex(PolicyId.LAVA_FIRE, new ReflexAction.Flee(water), "дух горит: цель — вода"),
                verdict,
                "Горящий дух с водой в пределах досягаемости идёт к воде: огонь гаснет только в воде, а не на "
                        + "суше, иначе здоровье догорает. При смене выбора цели проверь приоритет воды при горении");
    }

    @Test
    void evaluate_BurningWithLavaBesideAndWaterKnown_GoesToTheWater() {
        BlockCoord water = Pictures.at(-3, 0, 0);
        Pictures.Picture picture = Pictures.picture(S, ".../..L/...", O, BURNING, Optional.of(water));

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.empty(), picture.world());

        PolicyVerdict.Reflex reflex = assertInstanceOf(
                PolicyVerdict.Reflex.class,
                verdict,
                "Горящий дух обязан получить рефлекс, иначе останется догорать и потеряет здоровье. "
                        + "При смене evaluate проверь флаг горения без воды рядом");
        assertEquals(
                new ReflexAction.Flee(water),
                reflex.action(),
                "Горящему духу важнее потушиться, чем отойти от лавы вплотную: лава рядом не жжёт, а пока горим, "
                        + "здоровье тает, иначе дух сгорит. При смене приоритетов проверь пожар против соседней лавы");
    }

    @Test
    void evaluate_WaterExemptionWithLavaBeside_StillFlees() {
        Pictures.Picture picture = Pictures.picture(S, ".../..L/...", O, IN_WATER);

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.of(PolicyId.WATER), picture.world());

        PolicyVerdict.Reflex reflex = assertInstanceOf(
                PolicyVerdict.Reflex.class,
                verdict,
                "Лава вплотную обязана дать рефлекс даже при исключении WATER, иначе дух стоит в опасности. "
                        + "При смене PolicyExemptions проверь, что WATER не глушит чужие политики");
        assertEquals(
                PolicyId.LAVA_FIRE,
                reflex.policy(),
                "Исключение WATER снимает только политику воды: лава остаётся опасной всегда, иначе переправа "
                        + "вброд отменяет защиту от лавы. При смене проверки исключений сверь LAVA_FIRE");
    }

    @Test
    void evaluate_InWaterWithWaterExemption_IsClear() {
        Pictures.Picture picture = Pictures.picture(S, ".../.W./...", O, IN_WATER);

        PolicyVerdict verdict =
                HazardReflex.evaluate(picture.surroundings(), PolicyExemptions.of(PolicyId.WATER), picture.world());

        assertEquals(
                new PolicyVerdict.Clear(),
                verdict,
                "Скрипт с исключением WATER (переправа вброд) не должен выдёргиваться из воды рефлексом, "
                        + "иначе переход не завершить. При смене evaluate проверь WATER с исключением в луже");
    }
}
