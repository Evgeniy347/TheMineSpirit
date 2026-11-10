package com.theminespirit.core.script;

import java.util.List;

final class SocialScriptSpecs {
    private SocialScriptSpecs() {}

    private static final String RADIUS = "radius";
    private static final String TARGET = "target";
    private static final String BLOCK = "block";
    private static final String COUNT = "count";
    private static final String ITEM = "item";

    static List<ScriptSpec> socialSpecs() {
        return List.of(
                spec(
                        "deliver",
                        "доставить предмет адресату",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("to", true)),
                        List.of("адресат существует"),
                        List.of("предмет доставлен")),
                spec(
                        "meet",
                        "встретиться с целью",
                        List.of(ParamSpec.text(TARGET, true)),
                        List.of("цель существует"),
                        List.of("дух рядом с целью")),
                spec(
                        "say",
                        "произнести сообщение",
                        List.of(ParamSpec.text("text", true)),
                        List.of(),
                        List.of("сообщение отправлено")),
                spec(
                        "wait",
                        "ждать заданное число тиков",
                        List.of(ParamSpec.integer(
                                "ticks", true, CatalogLimits.WAIT_TICKS_MIN, CatalogLimits.WAIT_TICKS_MAX)),
                        List.of(),
                        List.of("время ожидания прошло")),
                spec(
                        "sleep",
                        "лечь спать в кровать",
                        List.of(),
                        List.of("ночь, дух внутри поселения и не в обороне", "в поселении есть свободная кровать"),
                        List.of("дух спит в кровати и просыпается утром, при угрозе или уроне")));
    }

    static List<ScriptSpec> campSpecs() {
        return List.of(
                spec(
                        "close_door",
                        "подойти к двери и закрыть её",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("дверь существует", "к двери есть подход"),
                        List.of("дверь закрыта")),
                spec(
                        "set_camp",
                        "поставить лагерь похода",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        false,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", false, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        false,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.CAMP_RADIUS_MIN,
                                        CatalogLimits.CAMP_RADIUS_MAX,
                                        CatalogLimits.CAMP_RADIUS_DEFAULT)),
                        List.of(),
                        List.of("лагерь поставлен и переживёт перезапуск")),
                spec("clear_camp", "снять лагерь похода", List.of(), List.of(), List.of("лагеря у духа нет")),
                spec(
                        "repair_tool",
                        "починить инструмент: материалом на наковальне или двумя одинаковыми "
                                + "повреждёнными в точиле (дешевле по опыту и материалу)",
                        List.of(ParamSpec.text(ITEM, true)),
                        List.of(
                                "в инвентаре есть повреждённый предмет; есть материал ремонта или второй такой же",
                                "для ремонта материалом рядом есть наковальня"),
                        List.of("предмет починен: наковальня добавляет 25% максимума за материал, "
                                + "точило — сумму прочности + 5% и теряет зачарования")));
    }

    static List<ScriptSpec> interactionSpecs() {
        return List.of(
                spec(
                        "use_block",
                        "нажать или переключить блок (рычаг, кнопка, дверь)",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("в клетке есть нажимаемый блок (рычаг, кнопка, дверь)", "к блоку есть подход"),
                        List.of("блок нажат или переключён", "чужой механизм в постройке игрока не трогается")),
                spec(
                        "open",
                        "открыть дверь, люк, калитку или сундук",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("в клетке есть дверь, люк, калитка или сундук", "к блоку есть подход"),
                        List.of("дверь, люк или калитка открыты", "сундук открыт и закрыт")),
                spec(
                        "set_combat_mode",
                        "сменить режим тактики духа",
                        List.of(ParamSpec.enumeration("mode", true, SetCombatModeParams.MODE_IDS)),
                        List.of(),
                        List.of("режим боя духа сменён", "смена записана в журнал решений тактики")),
                spec(
                        "drop_to_player",
                        "бросить предмет к игроку или духу и оставить его на земле рядом",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("to", true)),
                        List.of("адресат существует", "предмета хватает в инвентаре"),
                        List.of("предмет выпал рядом с адресатом")));
    }

    static List<ScriptSpec> exchangeSpecs() {
        return List.of(
                spec(
                        "give",
                        "передать предмет игроку или духу (то же, что deliver)",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("to", true)),
                        List.of("адресат существует", "предмета хватает в инвентаре"),
                        List.of("предмет передан адресату")),
                spec(
                        "take",
                        "взять предмет у игрока или духа в свой инвентарь",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("from", true)),
                        List.of("источник существует", "у источника хватает предмета", "в инвентаре духа есть место"),
                        List.of("предмет перешёл к духу")),
                spec(
                        "report",
                        "доложить о состоянии в чат: задача, инвентарь, здоровье, где находится",
                        List.of(ParamSpec.text("to", false, "")),
                        List.of(),
                        List.of("доклад отправлен игроку или всем в радиусе слышимости")),
                spec(
                        "swap_tool",
                        "сменить инструмент в руке на лучший для блока",
                        List.of(ParamSpec.text(BLOCK, true)),
                        List.of("блок зарегистрирован", "в руке или инвентаре есть подходящий инструмент"),
                        List.of("в руке лучший целый инструмент для блока")),
                spec(
                        "drink_potion",
                        "выпить зелье из инвентаря по типу",
                        List.of(ParamSpec.text("type", true)),
                        List.of("в инвентаре есть зелье этого типа"),
                        List.of("эффекты зелья применены", "зелье убрано из инвентаря")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
