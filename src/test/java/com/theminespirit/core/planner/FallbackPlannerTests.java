package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FallbackPlannerTests {
    private static final FallbackPlanner PLANNER = new FallbackPlanner();
    private static final WorldObservation OBSERVATION = Observations.empty("spirit_1");

    private static TaskNode plan(Goal goal) {
        return PLANNER.plan(goal, OBSERVATION).plan();
    }

    @Test
    void plan_ChopGoal_ReturnsScanAndChopSequence() {
        TaskNode plan = plan(new Goal("срубить деревья возле дома", Map.of()));

        assertEquals(chopPlan(8, "any", 1), plan,
                "Цель со словом 'сруб' обязана дать scan с фильтром брёвен и chop_tree: иначе дух рубит "
                        + "без предварительного скана и не находит деревья");
    }

    @Test
    void plan_TreeWordGoal_ReturnsScanAndChopSequence() {
        TaskNode plan = plan(new Goal("валить деревья", Map.of()));

        assertEquals(chopPlan(8, "any", 1), plan,
                "Слово 'дерев' обязано распознаваться как рубка: иначе синоним формулировки уводит в fallback "
                        + "и дух не рубит то, что игрок просил");
    }

    @Test
    void plan_MineGoal_ReturnsScanAndMineSequence() {
        TaskNode plan = plan(new Goal("добыть руду из шахты", Map.of()));

        assertEquals(minePlan(8, "any", 1), plan,
                "Цель со словом 'руд' обязана дать scan с фильтром руды и mine_vein: иначе дух копает "
                        + "вслепую и не находит жилу");
    }

    @Test
    void plan_MineShaftGoal_ReturnsScanAndMineSequence() {
        TaskNode plan = plan(new Goal("спустись в шахту", Map.of()));

        assertEquals(minePlan(8, "any", 1), plan,
                "Слово 'шахт' обязано распознаваться как добыча: иначе дух дойдёт до шахты и встанет, "
                        + "не начав добычу ради которой пришёл");
    }

    @Test
    void plan_FollowGoal_ReturnsFollowCall() {
        TaskNode plan = plan(new Goal("следуй за мной", Map.of("speaker", "Steve")));

        assertEquals(new ScriptCall("follow", Map.of("target", "Steve", "radius", 3)), plan,
                "Цель со словом 'следу' обязана дать follow на игрока-источника: иначе дух не сопровождает "
                        + "игрока и цель сопровождения не исполняется");
    }

    @Test
    void plan_FollowGoalWithoutSpeaker_ThrowsValidationException() {
        assertThrows(PlanValidationException.class,
                () -> plan(new Goal("следуй за мной", Map.of())),
                "Сопровождение без игрока-источника обязано отклоняться: заглушка 'player' в правилах fallback "
                        + "указывала на несуществующего игрока и роняла задачу на первом тике");
    }

    @Test
    void requiresPlayerSource_FollowGoal_ReturnsTrue() {
        assertTrue(FallbackPlanner.requiresPlayerSource("следуй за мной"),
                "Сопровождение без игрока-источника исполнить нечем: планировщик обязан сообщать, что цели нужен "
                        + "игрок, чтобы команда ответила понятным отказом, а не общим текстом валидатора");
    }

    @Test
    void requiresPlayerSource_AttackGoal_ReturnsFalse() {
        assertFalse(FallbackPlanner.requiresPlayerSource("убей зомби"),
                "Атака не требует игрока-источника: иначе консоль или командный блок не смог бы приказать духу "
                        + "бить враждебную цель");
    }

    @Test
    void requiresLogEvidence_ChopGoal_ReturnsTrue() {
        assertTrue(FallbackPlanner.requiresLogEvidence("срубить деревья возле дома"),
                "Рубка без брёвен рядом обречена: команда обязана сначала проверить наблюдение духа, иначе дух "
                        + "получит задачу, которую провалит на первом же шаге");
    }

    @Test
    void requiresLogEvidence_TreeWordGoal_ReturnsTrue() {
        assertTrue(FallbackPlanner.requiresLogEvidence("валить деревья"),
                "Синоним рубки обязан требовать ту же проверку окружения: иначе формулировка без слова 'сруб' "
                        + "обходит проверку и ставит безнадёжную задачу");
    }

    @Test
    void requiresLogEvidence_FollowGoal_ReturnsFalse() {
        assertFalse(FallbackPlanner.requiresLogEvidence("следуй за мной"),
                "Сопровождение от деревьев не зависит: лишняя проверка окружения отклоняла бы рабочую цель "
                        + "в чистом поле");
    }

    @Test
    void requiresLogEvidence_UnknownGoal_ReturnsFalse() {
        assertFalse(FallbackPlanner.requiresLogEvidence("полететь на луну"),
                "Незнакомая цель и так отклоняется планировщиком: проверка окружения для неё бессмысленна "
                        + "и лишь запутала бы причину отказа");
    }

    @Test
    void requiresOreEvidence_MineGoal_ReturnsTrue() {
        assertTrue(FallbackPlanner.requiresOreEvidence("добыть руду из шахты"),
                "Добыча без жилы рядом обречена: команда обязана сначала проверить наблюдение духа, иначе дух "
                        + "получит задачу, которую провалит на первом же шаге");
    }

    @Test
    void requiresOreEvidence_ChopGoal_ReturnsFalse() {
        assertFalse(FallbackPlanner.requiresOreEvidence("срубить дерево"),
                "Рубке нужна древесина, а не руда: чуждая проверка отклоняла бы рубку в лесу без единой жилы");
    }

    @Test
    void requiresOreEvidence_UnknownGoal_ReturnsFalse() {
        assertFalse(FallbackPlanner.requiresOreEvidence("полететь на луну"),
                "Незнакомая цель и так отклоняется планировщиком: проверка окружения для неё бессмысленна "
                        + "и лишь запутала бы причину отказа");
    }

    @Test
    void plan_AttackGoalWithSpeaker_DoesNotTargetSpeaker() {
        TaskNode plan = plan(new Goal("убей зомби", Map.of("speaker", "Steve")));

        assertEquals(new ScriptCall("attack", Map.of("target", "hostile")), plan,
                "Игрок-источник не должен становиться целью атаки: иначе '/spirit goal ... убей' заставляет "
                        + "духа бить собственного хозяина вместо враждебной цели");
    }

    @Test
    void plan_FollowBehindPhrase_ReturnsFollowCall() {
        TaskNode plan = plan(new Goal("держись за мной", Map.of("speaker", "Steve")));

        assertEquals(new ScriptCall("follow", Map.of("target", "Steve", "radius", 3)), plan,
                "Фраза 'за мной' обязана распознаваться как сопровождение: иначе естественная формулировка "
                        + "остаётся непонятой и дух теряет игрока");
    }

    @Test
    void plan_HomeGoal_ReturnsReturnHomeCall() {
        TaskNode plan = plan(new Goal("вернись домой", Map.of()));

        assertEquals(new ScriptCall("return_home", Map.of()), plan,
                "Цель со словом 'домой' обязана дать return_home: иначе дух остаётся в шахте на ночь "
                        + "и теряет точку возрождения");
    }

    @Test
    void plan_ComeBackGoal_ReturnsReturnHomeCall() {
        TaskNode plan = plan(new Goal("вернись ко мне", Map.of()));

        assertEquals(new ScriptCall("return_home", Map.of()), plan,
                "Слово 'верни' обязано распознаваться как возвращение домой: иначе приказ отступить "
                        + "не исполняется и дух остаётся в опасной зоне");
    }

    @Test
    void plan_AttackGoal_ReturnsAttackCall() {
        TaskNode plan = plan(new Goal("атакуй монстра", Map.of()));

        assertEquals(new ScriptCall("attack", Map.of("target", "hostile")), plan,
                "Цель со словом 'атак' обязана дать attack на враждебную цель: иначе дух мирно стоит "
                        + "перед зомби и погибает без защиты игрока");
    }

    @Test
    void plan_KillGoal_ReturnsAttackCall() {
        TaskNode plan = plan(new Goal("убей крипера", Map.of()));

        assertEquals(new ScriptCall("attack", Map.of("target", "hostile")), plan,
                "Слово 'убей' обязано распознаваться как атака: иначе боевая команда трактуется как мирная "
                        + "и дух не отгоняет угрозу");
    }

    @Test
    void plan_ChopGoalWithParams_OverridesDefaults() {
        TaskNode plan = plan(new Goal("срубить дерево",
                Map.of("radius", 16, "species", "oak", "count", 3)));

        assertEquals(chopPlan(16, "oak", 3), plan,
                "Параметры цели обязаны переопределять значения по умолчанию: иначе дух рубит любой вид "
                        + "в полном радиусе, а игрок просил только дубы рядом");
    }

    @Test
    void plan_FollowGoalWithParams_OverridesDefaults() {
        TaskNode plan = plan(new Goal("следуй за мной",
                Map.of("speaker", "villager", "radius", 5)));

        assertEquals(new ScriptCall("follow", Map.of("target", "villager", "radius", 5)), plan,
                "Параметры цели обязаны переопределять цель и дистанцию сопровождения: иначе дух сопровождает "
                        + "игрока, а не торговца, которому нужна защита");
    }

    @Test
    void plan_UppercaseGoalWithSpaces_IsNormalizedBeforeMatch() {
        TaskNode plan = plan(new Goal("   СРУБИТЬ ДУБ  ", Map.of()));

        assertEquals(chopPlan(8, "any", 1), plan,
                "Текст цели обязан нормализоваться (trim и нижний регистр) до поиска ключевых слов: иначе "
                        + "одна и та же цель с лишними пробелами или заглавными буквами исполняется по-разному");
    }

    @Test
    void plan_UnknownGoal_ThrowsUnplannableGoalNamingGoal() {
        UnplannableGoalException error = assertThrows(UnplannableGoalException.class,
                () -> plan(new Goal("полететь на луну", Map.of())),
                "Незнакомая цель обязана давать UnplannableGoalException: молчаливый план-заглушка уронит "
                        + "ожидания игрока и спрячет непонятые формулировки");

        assertTrue(error.getMessage().contains("полететь на луну"),
                "Сообщение обязано называть нераспознанную цель: по нему игрок видит, что планировщик не понял "
                        + "его просьбу. Фактическое: " + error.getMessage());
    }

    @Test
    void plan_TurnLeftGoal_ThrowsUnplannableGoal() {
        assertThrows(UnplannableGoalException.class,
                () -> plan(new Goal("поверни налево", Map.of())),
                "Слово 'поверни' не содержит отдельного слова 'верни': подстрочный поиск ошибочно гнал "
                        + "разворот на месте в return_home, и дух шёл домой вместо поворота");
    }

    @Test
    void plan_HardTaskGoal_ThrowsUnplannableGoal() {
        assertThrows(UnplannableGoalException.class,
                () -> plan(new Goal("трудная задача", Map.of())),
                "Слово 'трудная' начинается на 'труд', а не на отдельное слово 'руд': подстрочный поиск "
                        + "ошибочно отправлял дух добывать руду вместо отказа");
    }

    @Test
    void plan_ChopWordGoal_ReturnsScanAndChopSequence() {
        TaskNode plan = plan(new Goal("руби дерево", Map.of()));

        assertEquals(chopPlan(8, "any", 1), plan,
                "Слово 'дерево' обязано распознаваться как рубка по началу слова: иначе живая формулировка "
                        + "игрока не превращается в план и дух бездельничает");
    }

    @Test
    void plan_FollowWordGoal_ReturnsFollowCall() {
        TaskNode plan = plan(new Goal("иди за мной", Map.of("speaker", "Steve")));

        assertEquals(new ScriptCall("follow", Map.of("target", "Steve", "radius", 3)), plan,
                "Фраза 'за мной' по словам обязана распознаваться как сопровождение: иначе игрок идёт один "
                        + "и дух теряет его");
    }

    @Test
    void plan_AttackWithTreeMentioned_AttackWins() {
        TaskNode plan = plan(new Goal("убей зомби у дерева", Map.of()));

        assertEquals(new ScriptCall("attack", Map.of("target", "hostile")), plan,
                "При нескольких совпадениях побеждает правило, чьё слово раньше в тексте: иначе упоминание "
                        + "дерева уводит боевую команду в рубку и дух не защищает игрока");
    }

    @Test
    void plan_ChopGoalWithInvalidCountType_ThrowsValidationException() {
        assertThrows(PlanValidationException.class,
                () -> plan(new Goal("срубить деревья", Map.of("count", "пять"))),
                "Цель с неверным типом count должна быть отклонена валидатором: иначе в план попадает невалидное значение");
    }

    private static TaskNode chopPlan(int radius, String species, int count) {
        return new SequenceNode(List.of(
                new ScriptCall("scan", Map.of("radius", radius, "filter", List.of("*_log"))),
                new ScriptCall("chop_tree", Map.of("species", species, "count", count))));
    }

    private static TaskNode minePlan(int radius, String ore, int count) {
        return new SequenceNode(List.of(
                new ScriptCall("scan", Map.of("radius", radius, "filter", List.of("*_ore"))),
                new ScriptCall("mine_vein", Map.of("ore", ore, "count", count,
                        "radius", CatalogLimits.HARVEST_RADIUS_DEFAULT))));
    }
}
