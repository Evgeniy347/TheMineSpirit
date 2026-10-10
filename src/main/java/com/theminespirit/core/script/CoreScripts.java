package com.theminespirit.core.script;

import java.util.List;

public final class CoreScripts {
    private CoreScripts() {
    }

    public static ScriptCatalog defaultCatalog() {
        return ScriptCatalog.of(List.of(
                spec("scan", "просканировать мир вокруг духа",
                        List.of(p("radius", ParamSpec.TYPE_INT, false), p("filter", ParamSpec.TYPE_STRING_LIST, false)),
                        List.of(), List.of("обновлена карта блоков, сущностей и контейнеров")),
                spec("goto", "пройти к заданной точке",
                        List.of(ip("x"), ip("y"), ip("z")),
                        List.of("точка достижима"), List.of("дух находится в точке")),
                spec("follow", "следовать за целью на расстоянии",
                        List.of(p("target", ParamSpec.TYPE_STRING, true), p("radius", ParamSpec.TYPE_INT, false)),
                        List.of("цель существует"), List.of("дух держит дистанцию до цели")),
                spec("return_home", "вернуться на точку возрождения",
                        List.of(), List.of("точка возрождения известна"), List.of("дух дома")),
                spec("patrol", "патрулировать область по маршруту",
                        List.of(), List.of("маршрут задан"), List.of("маршрут пройден")),
                spec("flee", "убежать от угрозы",
                        List.of(), List.of("поблизости есть угроза"), List.of("дух вне опасности")),
                spec("chop_tree", "срубить деревья",
                        List.of(p("species", ParamSpec.TYPE_STRING, false), p("count", ParamSpec.TYPE_INT, true)),
                        List.of("в радиусе есть подходящие брёвна"),
                        List.of("брёвна срублены", "предметы подобраны в инвентарь")),
                spec("mine_vein", "добыть жилу руды",
                        List.of(p("ore", ParamSpec.TYPE_STRING, false), p("count", ParamSpec.TYPE_INT, true)),
                        List.of("в радиусе есть подходящая жила"), List.of("руда добыта")),
                spec("dig_area", "выкопать область",
                        List.of(ip("x"), ip("y"), ip("z"), ip("size")),
                        List.of(), List.of("блоки области удалены")),
                spec("gather", "собрать блоки заданного типа",
                        List.of(p("block", ParamSpec.TYPE_STRING, true), p("count", ParamSpec.TYPE_INT, true)),
                        List.of("в радиусе есть подходящие блоки"), List.of("блоки собраны")),
                spec("collect_drops", "подобрать выпавшие предметы",
                        List.of(), List.of("рядом есть выпавшие предметы"), List.of("предметы в инвентаре")),
                spec("harvest_crop", "собрать урожай",
                        List.of(p("crop", ParamSpec.TYPE_STRING, true), p("area", ParamSpec.TYPE_INT, false)),
                        List.of("посевы созрели"), List.of("урожай собран")),
                spec("plant_crop", "посадить культуру",
                        List.of(p("crop", ParamSpec.TYPE_STRING, true), p("area", ParamSpec.TYPE_INT, false)),
                        List.of("есть семена", "есть подготовленная земля"), List.of("культура посажена")),
                spec("craft", "скрафтить предмет",
                        List.of(p("item", ParamSpec.TYPE_STRING, true), p("count", ParamSpec.TYPE_INT, true)),
                        List.of("есть материалы и рецепт"), List.of("предмет скрафчен")),
                spec("smelt", "переплавить предмет",
                        List.of(p("item", ParamSpec.TYPE_STRING, true), p("count", ParamSpec.TYPE_INT, true)),
                        List.of("есть печь и топливо"), List.of("предмет переплавлен")),
                spec("equip", "надеть предмет",
                        List.of(p("item", ParamSpec.TYPE_STRING, true)),
                        List.of("предмет есть в инвентаре"), List.of("предмет надет")),
                spec("deposit", "положить предметы в контейнер",
                        List.of(p("container", ParamSpec.TYPE_STRING, true), p("item", ParamSpec.TYPE_STRING, true),
                                p("count", ParamSpec.TYPE_INT, true)),
                        List.of("контейнер доступен"), List.of("предметы в контейнере")),
                spec("withdraw", "взять предметы из контейнера",
                        List.of(p("container", ParamSpec.TYPE_STRING, true), p("item", ParamSpec.TYPE_STRING, true),
                                p("count", ParamSpec.TYPE_INT, true)),
                        List.of("контейнер доступен"), List.of("предметы в инвентаре")),
                spec("sort_chest", "рассортировать содержимое сундука",
                        List.of(), List.of("контейнер доступен"), List.of("содержимое упорядочено")),
                spec("acquire", "добыть предмет любым способом",
                        List.of(p("item", ParamSpec.TYPE_STRING, true), p("count", ParamSpec.TYPE_INT, true)),
                        List.of(), List.of("предмет есть в инвентаре")),
                spec("attack", "атаковать цель",
                        List.of(p("target", ParamSpec.TYPE_STRING, true)),
                        List.of("цель видима"), List.of("цель атакована")),
                spec("defend", "защитить цель",
                        List.of(p("target", ParamSpec.TYPE_STRING, true)),
                        List.of("цель существует"), List.of("цель защищена")),
                spec("retreat", "отступить",
                        List.of(), List.of("есть путь отхода"), List.of("дух отступил")),
                spec("place_block", "поставить блок",
                        List.of(p("block", ParamSpec.TYPE_STRING, true), ip("x"), ip("y"), ip("z")),
                        List.of("место свободно"), List.of("блок поставлен")),
                spec("fill", "заполнить область блоком",
                        List.of(p("block", ParamSpec.TYPE_STRING, true), ip("x1"), ip("y1"), ip("z1"), ip("x2"),
                                ip("y2"), ip("z2")),
                        List.of(), List.of("область заполнена")),
                spec("clear_area", "очистить область",
                        List.of(ip("x1"), ip("y1"), ip("z1"), ip("x2"), ip("y2"), ip("z2")),
                        List.of(), List.of("область очищена")),
                spec("build_schematic", "построить схему из файла",
                        List.of(p("file", ParamSpec.TYPE_STRING, true), ip("x"), ip("y"), ip("z")),
                        List.of("файл схемы существует"), List.of("схема построена")),
                spec("deliver", "доставить предмет адресату",
                        List.of(p("item", ParamSpec.TYPE_STRING, true), p("count", ParamSpec.TYPE_INT, true),
                                p("to", ParamSpec.TYPE_STRING, true)),
                        List.of("адресат существует"), List.of("предмет доставлен")),
                spec("meet", "встретиться с целью",
                        List.of(p("target", ParamSpec.TYPE_STRING, true)),
                        List.of("цель существует"), List.of("дух рядом с целью")),
                spec("say", "произнести сообщение",
                        List.of(p("text", ParamSpec.TYPE_STRING, true)),
                        List.of(), List.of("сообщение отправлено")),
                spec("wait", "ждать заданное число тиков",
                        List.of(p("ticks", ParamSpec.TYPE_INT, true)),
                        List.of(), List.of("время ожидания прошло"))));
    }

    private static ParamSpec p(String name, String type, boolean required) {
        return new ParamSpec(name, type, required);
    }

    private static ParamSpec ip(String name) {
        return new ParamSpec(name, ParamSpec.TYPE_INT, true);
    }

    private static ScriptSpec spec(String id, String summary, List<ParamSpec> params, List<String> preconditions,
            List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
