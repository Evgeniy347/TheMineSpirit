package com.theminespirit.core.script;

import static com.theminespirit.core.script.ScriptCatalogFixturesData.*;
import static com.theminespirit.core.script.ScriptCatalogTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ParamType;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptCatalogPart5Tests {
    @Test
    void defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined() {
        ScriptSpec unload = CoreScripts.defaultCatalog().find("unload").orElseThrow();
        ParamSpec force = param(unload, "force");
        assertEquals(
                false,
                force.required(),
                "force у unload необязателен: без него разгрузка срабатывает только при заполненном инвентаре, "
                        + "а не по каждой мелочи Иначе тест defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined перестанет ловить эту поломку. При смене defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined проверь это утверждение.");
        assertEquals(
                false,
                force.defaultValue(),
                "force по умолчанию false: разгрузка «без фанатизма» не трогает инвентарь, пока занято "
                        + "меньше 24 слотов Иначе тест defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined перестанет ловить эту поломку. При смене defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined проверь это утверждение.");
        assertEquals(
                24,
                CatalogLimits.UNLOAD_TRIGGER_SLOTS,
                "Порог разгрузки — 24 слота из 36: дух тащит добро на базу, только когда в инвентаре "
                        + "действительно не осталось места Иначе тест defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined перестанет ловить эту поломку. При смене defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined проверь это утверждение.");
    }

    @Test
    void defaultCatalog_CollectDrops_OverflowDefaultsToByValue() {
        ScriptSpec collectDrops =
                CoreScripts.defaultCatalog().find("collect_drops").orElseThrow();
        ParamSpec overflow = param(collectDrops, "overflow");
        assertEquals(
                false,
                overflow.required(),
                "overflow у collect_drops необязателен: по умолчанию действует поведение «берём ценное, "
                        + "дешёвое оставляем» Иначе тест defaultCatalog_CollectDrops_OverflowDefaultsToByValue перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_OverflowDefaultsToByValue проверь это утверждение.");
        assertEquals(
                List.of("fail", "by_value", "return_to_base"),
                overflow.values(),
                "У overflow три режима: fail — отказ, by_value — подмена по ценности, return_to_base — вынос на базу Иначе тест defaultCatalog_CollectDrops_OverflowDefaultsToByValue перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_OverflowDefaultsToByValue проверь это утверждение.");
        assertEquals(
                "by_value",
                overflow.defaultValue(),
                "По умолчанию collect_drops работает по ценности: дешёвое остаётся, ценное берётся, "
                        + "а не полный отказ Иначе тест defaultCatalog_CollectDrops_OverflowDefaultsToByValue перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_OverflowDefaultsToByValue проверь это утверждение.");
    }

    @Test
    void defaultCatalog_Equip_TakesOnlyItemPattern() {
        ScriptSpec equip = CoreScripts.defaultCatalog().find("equip").orElseThrow();
        assertEquals(
                List.of("item"),
                equip.params().stream().map(ParamSpec::name).toList(),
                "У equip один обязательный параметр item: по его шаблону ('*_pickaxe') дух выбирает, "
                        + "что надевать, и берёт самый ценный подходящий Иначе тест defaultCatalog_Equip_TakesOnlyItemPattern перестанет ловить эту поломку. При смене defaultCatalog_Equip_TakesOnlyItemPattern проверь это утверждение.");
    }

    @Test
    void defaultCatalog_NavScripts_ParamsAndDefaults() {
        ScriptSpec gotoBlock = CoreScripts.defaultCatalog().find("goto_block").orElseThrow();
        ScriptSpec comeToPlayer =
                CoreScripts.defaultCatalog().find("come_to_player").orElseThrow();
        ScriptSpec patrol = CoreScripts.defaultCatalog().find("patrol").orElseThrow();
        ScriptSpec wander = CoreScripts.defaultCatalog().find("wander").orElseThrow();
        ScriptSpec keepDistance =
                CoreScripts.defaultCatalog().find("keep_distance").orElseThrow();
        assertEquals(
                16,
                param(gotoBlock, "radius").defaultValue(),
                "radius в goto_block по умолчанию 16: каталог задаёт умолчание, чтобы маппинг его не дублировал Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                1,
                param(gotoBlock, "count").defaultValue(),
                "count в goto_block по умолчанию 1: без числа дух идёт к ближайшему блоку вида Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                "nearest",
                param(comeToPlayer, "player").defaultValue(),
                "player в come_to_player по умолчанию 'nearest': без имени дух идёт к ближайшему игроку Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                ParamType.ENUM,
                param(patrol, "mode").type(),
                "mode в patrol — enum: опечатка в режиме обязана отклоняться валидатором до запуска Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                List.of("loop", "ping_pong"),
                param(patrol, "mode").values(),
                "У patrol два режима обхода: loop — по кругу, ping_pong — туда-обратно Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                "loop",
                param(patrol, "mode").defaultValue(),
                "mode в patrol по умолчанию 'loop': умолчание живёт в каталоге, а не в маппинге Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                1,
                param(patrol, "loops").defaultValue(),
                "loops в patrol по умолчанию 1: без числа кругов патруль проходит маршрут один раз Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                ParamType.STRING_LIST,
                param(patrol, "points").type(),
                "points в patrol — список строк 'x,y,z': точки маршрута задаются параметрами Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                16,
                param(wander, "radius").defaultValue(),
                "radius в wander по умолчанию 16: значение задаёт каталог, чтобы маппинг его не дублировал Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                600,
                param(wander, "duration_ticks").defaultValue(),
                "duration_ticks в wander по умолчанию 600: без длительности блуждание не знает, когда кончить Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
        assertEquals(
                5,
                param(keepDistance, "distance").defaultValue(),
                "distance в keep_distance по умолчанию 5: без дистанции дух не знает, где встать Иначе тест defaultCatalog_NavScripts_ParamsAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_NavScripts_ParamsAndDefaults проверь это утверждение.");
    }

    @Test
    void defaultCatalog_NavLimits_MatchAgreedBounds() {
        assertEquals(
                1,
                CatalogLimits.NAV_RADIUS_MIN,
                "Нижняя граница радиуса навигации — 1: нулевой радиус искал бы только под ногами духа Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                32,
                CatalogLimits.NAV_RADIUS_MAX,
                "Верхняя граница радиуса навигации — 32: больший поиск кладёт серверный тик Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                16,
                CatalogLimits.NAV_RADIUS_DEFAULT,
                "Радиус навигации по умолчанию — 16: значение задаёт каталог, чтобы маппинги его не дублировали Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                64,
                CatalogLimits.NAV_PATROL_POINTS_MAX,
                "Точек маршрута patrol — не больше 64: длинный маршрут режется на части Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1,
                CatalogLimits.NAV_PATROL_LOOPS_MIN,
                "Кругов patrol — не меньше 1: ноль кругов завершается сразу и врёт стратегу Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                16,
                CatalogLimits.NAV_PATROL_LOOPS_MAX,
                "Кругов patrol — не больше 16: лимит держит патруль в разумном времени Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1,
                CatalogLimits.NAV_PATROL_LOOPS_DEFAULT,
                "Кругов patrol по умолчанию — 1: без числа патруль проходит маршрут один раз Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                "loop",
                CatalogLimits.NAV_PATROL_MODE_DEFAULT,
                "Режим patrol по умолчанию — 'loop': умолчание живёт в каталоге и подставляется валидатором Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                20,
                CatalogLimits.NAV_WANDER_DURATION_MIN,
                "Блуждание короче 20 тиков бессмысленно: за секунду дух не успевает сдвинуться Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                6000,
                CatalogLimits.NAV_WANDER_DURATION_MAX,
                "Блуждание дольше 6000 тиков (5 минут) не планируется одним шагом Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                600,
                CatalogLimits.NAV_WANDER_DURATION_DEFAULT,
                "Длительность блуждания по умолчанию — 600 тиков: без неё скрипт не знает, когда кончить Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1,
                CatalogLimits.NAV_KEEP_DISTANCE_MIN,
                "Дистанция keep_distance — не меньше 1: нулевая дистанция — стояние внутри цели Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                16,
                CatalogLimits.NAV_KEEP_DISTANCE_MAX,
                "Дистанция keep_distance — не больше 16: дальше дух уходит из тикающей зоны Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                5,
                CatalogLimits.NAV_KEEP_DISTANCE_DEFAULT,
                "Дистанция keep_distance по умолчанию — 5: без неё дух не знает, где встать Иначе тест defaultCatalog_NavLimits_MatchAgreedBounds перестанет ловить эту поломку. При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
        assertEquals(
                1.25,
                CatalogLimits.NAV_ARRIVAL_TOLERANCE,
                "Допуск прибытия навигации — 1.25 блока, как у goto: иначе прибытие считается то здесь, то там При смене defaultCatalog_NavLimits_MatchAgreedBounds проверь это утверждение.");
    }
}
