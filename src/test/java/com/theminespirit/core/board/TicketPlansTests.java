package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ClearAreaParams;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class TicketPlansTests {

    private static final Map<BoardCase, String> CASE_SCRIPTS = Map.ofEntries(
            Map.entry(BoardCase.FILL_CRATER, "fill"),
            Map.entry(BoardCase.REPAIR_FENCE, "fill"),
            Map.entry(BoardCase.CAPTURE_ANIMAL, "capture"),
            Map.entry(BoardCase.BUILD_PEN, "build_schematic"),
            Map.entry(BoardCase.LURE_ANIMAL, "withdraw"),
            Map.entry(BoardCase.BUILD_MINE, "dig_descent"),
            Map.entry(BoardCase.SUPPLY_SITE, "acquire"),
            Map.entry(BoardCase.GATHER_MATERIALS, "acquire"),
            Map.entry(BoardCase.CLEAR_GROUNDS, "clear_area"),
            Map.entry(BoardCase.COLLECT_DROPS, "collect_drops"),
            Map.entry(BoardCase.BUILD_HOUSE, "build_schematic"),
            Map.entry(BoardCase.BUILD_WAREHOUSE, "build_schematic"),
            Map.entry(BoardCase.REPAIR_BUILDING, "build_schematic"),
            Map.entry(BoardCase.REPAIR_TOOL, "repair_tool"),
            Map.entry(BoardCase.STOCK_CHESTS, "craft"),
            Map.entry(BoardCase.CRAFTING_STATION, "craft"),
            Map.entry(BoardCase.CRAFT_ANVIL, "craft"),
            Map.entry(BoardCase.CRAFT_SMITHING_TABLE, "craft"),
            Map.entry(BoardCase.CRAFT_SMOKER, "craft"),
            Map.entry(BoardCase.CRAFT_FURNACE, "craft"),
            Map.entry(BoardCase.CRAFT_TORCHES, "craft"),
            Map.entry(BoardCase.SMELT_CHARCOAL, "smelt"),
            Map.entry(BoardCase.FISH, "fish"),
            Map.entry(BoardCase.CRAFT_WOODEN_PICKAXE, "craft"),
            Map.entry(BoardCase.CRAFT_WOODEN_AXE, "craft"),
            Map.entry(BoardCase.CRAFT_WOODEN_SHOVEL, "craft"),
            Map.entry(BoardCase.CRAFT_STONE_PICKAXE, "craft"),
            Map.entry(BoardCase.CRAFT_STONE_AXE, "craft"),
            Map.entry(BoardCase.CRAFT_STONE_SHOVEL, "craft"),
            Map.entry(BoardCase.CRAFT_STONE_SWORD, "craft"),
            Map.entry(BoardCase.CRAFT_SHEARS, "craft"),
            Map.entry(BoardCase.CRAFT_BED, "craft"),
            Map.entry(BoardCase.WEAVE_WOOL, "craft"),
            Map.entry(BoardCase.BUILD_FENCE, "build_schematic"),
            Map.entry(BoardCase.LIGHTING, "place_block"),
            Map.entry(BoardCase.ENCHANT_ROOM, "build_schematic"),
            Map.entry(BoardCase.ENCHANT_SHELVES, "craft"),
            Map.entry(BoardCase.DISMANTLE_SHELTER, "clear_area"),
            Map.entry(BoardCase.BREED_ANIMALS, "breed"),
            Map.entry(BoardCase.HERD_CULL, "cull"),
            Map.entry(BoardCase.RELOCATE_BUILDING, "build_schematic"),
            Map.entry(BoardCase.RESTOCK_FOOD, "withdraw"),
            Map.entry(BoardCase.TRANSFER_ITEM, "deliver"),
            Map.entry(BoardCase.SHEAR_SHEEP, "shear"),
            Map.entry(BoardCase.HUNT_WILD_SHEEP, "hunt"),
            Map.entry(BoardCase.HARVEST_HIVE, "harvest_hive"),
            Map.entry(BoardCase.PLACE_BEEHIVE, "craft"));

    private static final List<BoardCase> PLANNED_CASES =
            List.of(CASE_SCRIPTS.keySet().toArray(new BoardCase[0]));

    private static TicketTarget target() {
        return TicketTarget.at(new BlockCoord(10, 60, -5)).withSpecies(Species.SHEEP);
    }

    private static TicketTarget fullTarget() {
        return target().withScheme("pen")
                .withRelocate(new BlockCoord(10, 60, -5), new BlockCoord(30, 60, 10))
                .withSupply("bread", 1)
                .withTransfer(
                        UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                        UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
                        "bread",
                        1)
                .withCraft("minecraft:white_bed", 1)
                .withCount(2);
    }

    @Test
    void plan_EveryCase_ContainsCaseScript() {
        for (BoardCase boardCase : PLANNED_CASES) {
            TaskNode plan = TicketPlans.plan(boardCase, fullTarget());

            assertTrue(
                    collectScripts(plan).contains(CASE_SCRIPTS.get(boardCase)),
                    "План кейса " + boardCase + " обязан содержать целевой скрипт " + CASE_SCRIPTS.get(boardCase)
                            + ": без него задание не делает свою работу");
        }
    }

    @Test
    void plan_EveryCase_PassesCatalog() {
        for (BoardCase boardCase : PLANNED_CASES) {
            TaskNode plan = TicketPlans.plan(boardCase, fullTarget());

            TaskNode validated = PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());

            assertEquals(
                    collectScripts(plan),
                    collectScripts(validated),
                    "План случая " + boardCase + " обязан проходить каталог скриптов теми же шагами: иначе доска "
                            + "выдаст духу задание, которое валидатор отвергнет или перестроит при запуске");
        }
    }

    @Test
    void plan_EveryCase_HasPlan() {
        for (BoardCase boardCase : BoardCase.values()) {
            if (TicketPlans.EXTERNAL_CASES.contains(boardCase)) {
                continue;
            }
            TaskNode plan = TicketPlans.plan(boardCase, fullTarget());

            assertTrue(
                    plan != null && !collectScripts(plan).isEmpty(),
                    "Каждый не внешний кейс обязан иметь план: кейс " + boardCase
                            + " без плана не выдавался бы духу, а выдача без плана — задание в пустоту");
        }
        assertTrue(
                TicketPlans.EXTERNAL_CASES.contains(BoardCase.EAT_TO_FULL),
                "Внешний список обязан называть EAT_TO_FULL: дух ест сам скриптом eat, "
                        + "план доски ему не нужен, а пустой список спрятал бы кейс без плана");
    }

    @Test
    void plan_CollectDrops_DropsFilterAndTargetPoint() {
        BlockCoord point = new BlockCoord(7, 64, -3);
        TaskNode plan = TicketPlans.plan(
                BoardCase.COLLECT_DROPS, TicketTarget.at(point).withDrops(List.of("minecraft:cobblestone")));

        assertTrue(
                plan instanceof SequenceNode sequence && sequence.steps().size() == 2,
                "План сбора выкинутого — переход в точку и подбор: без перехода дух ищет вещи "
                        + "вокруг себя, а не там, где их выкинули");
        ScriptCall collect = (ScriptCall) ((SequenceNode) plan).steps().get(1);
        assertEquals(
                "collect_drops",
                collect.script(),
                "Второй шаг плана — collect_drops: без него задание не подбирает вещи");
        assertEquals(
                List.of("minecraft:cobblestone"),
                collect.params().get("filter"),
                "Фильтр плана называет именно выкинутый предмет: иначе дух соберёт чужой мусор");
        assertEquals(
                point.x(),
                ((ScriptCall) ((SequenceNode) plan).steps().get(0)).params().get("x"),
                "Переход идёт в координаты выкидывания по X");
        assertEquals(
                point.z(),
                ((ScriptCall) ((SequenceNode) plan).steps().get(0)).params().get("z"),
                "Переход идёт в координаты выкидывания по Z");
    }

    @Test
    void plan_Lighting_PlacesFourTorches() {
        TaskNode plan = TicketPlans.plan(BoardCase.LIGHTING, target());
        long torches =
                collectScripts(plan).stream().filter("place_block"::equals).count();

        assertEquals(
                4,
                torches,
                "Освещение — четыре факела по углам: один факел не освещает территорию, "
                        + "а больше — перерасход ресурсов доски");
    }

    @Test
    void plan_BuildFence_UsesFenceScheme() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_FENCE, target());

        assertTrue(
                collectScripts(plan).contains("build_schematic"),
                "Забор строится схемой-кольцом fence: новый скрипт не заводим, кольцо " + "описано файлом схемы");
    }

    @Test
    void plan_EnchantShelves_CraftsBookshelves() {
        TaskNode plan = TicketPlans.plan(BoardCase.ENCHANT_SHELVES, target());

        assertTrue(
                collectScripts(plan).contains("craft"),
                "Полки достраиваются крафтом книжных полок: книги гейтят задание, " + "а план их собирает в полки");
    }

    @Test
    void plan_CraftSmoker_CraftsSmoker() {
        TaskNode plan = TicketPlans.plan(BoardCase.CRAFT_SMOKER, target());
        ScriptCall craft = findCall(plan, "craft");

        assertEquals(
                "minecraft:smoker",
                craft.params().get("item"),
                "Кейс коптильни крафтит именно коптильню: без этого улов негде коптить");
    }

    @Test
    void plan_Fish_GoesToPointAndFishes() {
        BlockCoord point = new BlockCoord(7, 64, -3);
        TaskNode plan = TicketPlans.plan(BoardCase.FISH, TicketTarget.at(point));

        assertTrue(
                plan instanceof SequenceNode sequence && sequence.steps().size() == 2,
                "Рыбалка как занятие — переход на рабочую точку и ловля: без перехода дух рыбачит "
                        + "у случайной воды");
        ScriptCall fish = (ScriptCall) ((SequenceNode) plan).steps().get(1);
        assertEquals("fish", fish.script(), "Второй шаг плана рыбалки — скрипт fish: без него занятие ничего не ловит");
    }

    @Test
    void plan_DismantleShelter_ClearsShelter() {
        TaskNode plan = TicketPlans.plan(BoardCase.DISMANTLE_SHELTER, target());

        assertTrue(
                collectScripts(plan).contains("clear_area"),
                "Разбор укрытия — существующий clear_area: второй разборочный скрипт " + "не заводим");
    }

    @Test
    void plan_ClearGrounds_EachCallFitsVolumeLimitAndCoversWholeSite() {
        BlockCoord center = new BlockCoord(10, 60, -5);
        TaskNode plan = TicketPlans.plan(BoardCase.CLEAR_GROUNDS, TicketTarget.at(center));
        List<ScriptCall> calls = collectCalls(plan, "clear_area");
        Set<String> cells = new HashSet<>();
        for (ScriptCall call : calls) {
            ClearAreaParams params = ClearAreaParams.from(call.params());
            for (int x = params.x1(); x <= params.x2(); x++) {
                for (int y = params.y1(); y <= params.y2(); y++) {
                    for (int z = params.z1(); z <= params.z2(); z++) {
                        cells.add(x + ":" + y + ":" + z);
                    }
                }
            }
        }

        assertTrue(
                calls.size() > 1,
                "Расчистка участка 33×33×4 = 4356 больше предела clear_area 4096: одним вызовом "
                        + "скрипт падает, поэтому план обязан разбить участок на части, а не "
                        + "оставлять духа в цикле провалов");
        assertEquals(
                33 * 33 * 4,
                cells.size(),
                "Куски расчистки обязаны покрыть весь участок радиуса 16 без дыр: иначе "
                        + "факты поселения снова увидят деревья и переоткроют кейс");
    }

    @Test
    void plan_HarvestHive_UsesBoardRadius() {
        ScriptCall harvest = findCall(TicketPlans.plan(BoardCase.HARVEST_HIVE, target()), "harvest_hive");

        assertEquals(
                HiveRules.hiveRadius(),
                harvest.params().get("radius"),
                "Сбор улья ищет полный улей в радиусе доски: иначе задание не нашло бы цель");
        assertEquals(
                1,
                harvest.params().get("count"),
                "Задание собирает один улей: несколько за раз оставили бы духа без отчёта по каждому");
    }

    @Test
    void plan_PlaceBeehive_CraftsAndPlacesAboveSupport() {
        BlockCoord support = new BlockCoord(12, 64, 3);
        TaskNode plan = TicketPlans.plan(BoardCase.PLACE_BEEHIVE, TicketTarget.at(support));
        List<String> scripts = collectScripts(plan);

        assertTrue(
                scripts.contains("craft") && scripts.contains("place_block"),
                "Постановка улья — крафт улья и установка: без крафта духу нечего ставить");
        ScriptCall craft = findCall(plan, "craft");
        assertEquals(
                "minecraft:beehive",
                craft.params().get("item"),
                "Крафтится именно улей: другой предмет не даст улей у цветов");
        ScriptCall place = findCall(plan, "place_block");
        assertEquals(
                support.y() + 1,
                place.params().get("y"),
                "Улей ставится на опору из заявки, а не в её клетку: иначе блок заменил бы землю");
    }

    @Test
    void plan_Is_Deterministic() {
        TaskNode first = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(29));
        TaskNode second = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(29));

        assertEquals(
                first,
                second,
                "План обязан быть детерминирован: доска выдаёт план духу, и пересборка "
                        + "не должна менять задание на лету");
    }

    @Test
    void plan_FillCrater_AcquiresDirtFromStock() {
        TaskNode plan = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(29));

        assertTrue(
                collectScripts(plan).contains("acquire"),
                "Воронка обязана брать землю через acquire: гейт уже проверил склад, "
                        + "и план берёт из запаса, а не копает заново");
        assertTrue(
                !collectScripts(plan).contains("gather"),
                "План воронки не копает вслепую: gather игнорирует склад и проваливается "
                        + "там, где земли рядом нет, хотя гейт её уже нашёл");
    }

    @Test
    void plan_SupplySite_ClampsHugeCount() {
        TaskNode plan = TicketPlans.plan(BoardCase.SUPPLY_SITE, target().withSupply("dirt", 5000));
        ScriptCall acquire = findCall(plan, "acquire");

        assertEquals(
                256,
                acquire.params().get("count"),
                "Снабжение режет партию пределом каталога: AcquireParams принимает не больше "
                        + "COUNT_MAX, без нарезки дух падает на выдаче ("
                        + acquire.params().get("count")
                        + "), а остаток доска дозакажет следующим заданием");
    }

    @Test
    void plan_FillCrater_ClampsHugeEstimate() {
        TaskNode plan = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(10000));
        ScriptCall acquire = findCall(plan, "acquire");

        assertEquals(
                256,
                acquire.params().get("count"),
                "Оценка большой воронки тоже режется пределом каталога: иначе acquire " + "падает на выдаче ("
                        + acquire.params().get("count") + ")");
    }

    @Test
    void plan_CaptureAnimal_UsesSpecies() {
        TaskNode plan = TicketPlans.plan(BoardCase.CAPTURE_ANIMAL, target().withSpecies(Species.CHICKEN));

        assertTrue(
                collectScripts(plan).contains("capture"),
                "Отлов обязан идти скриптом capture: сам отлов — существующий механизм "
                        + " husbandry (25), план только его вызывает");
        assertEquals(
                "minecraft:wheat_seeds",
                findCall(plan, "withdraw").params().get("item"),
                "Перед отловом курицы дух берёт её корм со склада: capture ведёт животное приманкой");
        assertEquals(
                CatalogLimits.CONTAINER_RADIUS_DEFAULT,
                findCall(plan, "withdraw").params().get("radius"),
                "Внутренний withdraw обязан нести radius: доска запускает план мимо валидатора, и без радиуса "
                        + "скрипт падает «Не задан обязательный параметр 'radius'» (INC-6)");
    }

    @Test
    void plan_BuildPen_UsesRingScheme() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_PEN, target());

        assertTrue(
                collectScripts(plan).contains("build_schematic"),
                "Згон строится схемой-пресетом кольца из блоков (поправка 8): отдельный "
                        + "файл схемы описывает кольцо, новый скрипт не заводим");
    }

    @Test
    void plan_LureAnimal_WithdrawsLure() {
        TaskNode plan = TicketPlans.plan(BoardCase.LURE_ANIMAL, target().withLureItem("wheat"));

        assertTrue(
                collectScripts(plan).contains("withdraw"),
                "Приманить — сначала взять приманку: гейт требует приманку в запасах, "
                        + "а план — сначала вынуть её со склада");
        assertTrue(
                collectScripts(plan).contains("capture"),
                "Приманить — затем привести животное в загон скриптом capture (упрощение 44.4: приманка вместо "
                        + "кольца); без него дух взял бы пшеницу и встал на месте");
    }

    @Test
    void plan_MineLevelOne_DigsDescent() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_MINE, target());

        assertTrue(
                collectScripts(plan).contains("dig_descent"),
                "Уровень 1 шахты — спуск dig_descent: проход 1×3 вниз, версия 1");
        ScriptCall descent = findCall(plan, "dig_descent");
        assertEquals(
                "south",
                descent.params().get("direction"),
                "Спуск идёт в названную сторону: без направления неизвестно, куда копать тоннель");
    }

    @Test
    void plan_MineLevelTwo_ExpandsTunnel() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_MINE, 2, target());

        assertTrue(
                collectScripts(plan).contains("dig_area"),
                "Уровень 2 шахты — расширение тоннеля dig_area: шире и выше прохода уровня 1");
    }

    @Test
    void plan_MineLevelThree_BuildsSteps() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_MINE, 3, target());

        assertTrue(
                collectScripts(plan).contains("dig_area")
                        && collectScripts(plan).contains("fill"),
                "Уровень 3 шахты — ступеньки: выкопать уступ и заполнить ступень");
    }

    @Test
    void plan_MineLevelFour_WaitsForRails() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_MINE, 4, target());

        assertTrue(
                collectScripts(plan).contains("dig_area"),
                "Уровень 4 шахты — рельсы и вагонетка: скрипта рельсов в каталоге нет, план — "
                        + "заглушка dig_area минимальной длины до появления скрипта");
    }

    @Test
    void plan_MineAboveMax_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketPlans.plan(BoardCase.BUILD_MINE, 5, target()),
                "Уровень выше максимума обязан падать: выдача несуществующего уровня оставила бы "
                        + "духа без настоящей работы");
    }

    @Test
    void plan_BuildPen_LevelsUseGrowingSchemes() {
        ScriptCall one = findCall(TicketPlans.plan(BoardCase.BUILD_PEN, 1, target()), "build_schematic");
        ScriptCall two = findCall(TicketPlans.plan(BoardCase.BUILD_PEN, 2, target()), "build_schematic");
        ScriptCall three = findCall(TicketPlans.plan(BoardCase.BUILD_PEN, 3, target()), "build_schematic");

        assertEquals(
                "pen_4x5",
                one.params().get("file"),
                "Уровень 1 загона — схема pen_4x5: ограда в 1 блок с калиткой, минимум 4×5");
        assertEquals(
                "pen_7x7", two.params().get("file"), "Уровень 2 загона — схема pen_7x7: загон из библиотеки пресетов");
        assertEquals(
                "pen_9x9",
                three.params().get("file"),
                "Уровень 3 загона — схема больше 7×7: рост данными без правки кода");
    }

    @Test
    void plan_EnchantRoom_LevelsUseGrowingSchemes() {
        ScriptCall one = findCall(TicketPlans.plan(BoardCase.ENCHANT_ROOM, 1, target()), "build_schematic");
        ScriptCall two = findCall(TicketPlans.plan(BoardCase.ENCHANT_ROOM, 2, target()), "build_schematic");

        assertEquals(
                "enchant_room_v1",
                one.params().get("file"),
                "Уровень 1 чаровальни — пресет enchant_room_v1 (решение владельца 18.10: доска строит настоящие "
                        + "пресеты, а не заготовку из четырёх столбов): стол и кольцо 15 полок для 30 уровня");
        assertEquals(
                "enchant_room_v2",
                two.params().get("file"),
                "Уровень 2 чаровальни — пресет enchant_room_v2: второй стол и запас полок");
    }

    @Test
    void plan_DefaultLevel_UsesFirst() {
        TaskNode byLevel = TicketPlans.plan(BoardCase.BUILD_MINE, 1, target());
        TaskNode byCase = TicketPlans.plan(BoardCase.BUILD_MINE, target());

        assertEquals(
                byLevel,
                byCase,
                "plan(case, target) — это plan(case, 1, target): старый вызов выдаёт первый уровень, "
                        + "иначе адаптер и тесты разошлись бы в плане одного задания");
    }

    @Test
    void plan_NonLeveledLevel_UsesSamePlan() {
        assertEquals(
                TicketPlans.plan(BoardCase.FILL_CRATER, 1, target()),
                TicketPlans.plan(BoardCase.FILL_CRATER, 1, target()),
                "У кейса без уровней план не зависит от уровня: он всегда один");
    }

    @Test
    void plan_ZeroLevel_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketPlans.plan(BoardCase.BUILD_MINE, 0, target()),
                "Уровень 0 обязан падать: первая постройка ставится уровнем 1");
    }

    @Property
    void plan_LeveledLevel_ValidByCatalog(@ForAll("levelledCases") BoardCase boardCase, @ForAll("levels") int level) {
        TaskNode plan = TicketPlans.plan(boardCase, level, fullTarget());

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    @Property
    void plan_LeveledLevel_OnlyCatalogScripts(
            @ForAll("levelledCases") BoardCase boardCase, @ForAll("levels") int level) {
        TaskNode plan = TicketPlans.plan(boardCase, level, fullTarget());
        List<String> catalogScripts =
                CoreScripts.defaultCatalog().all().stream().map(ScriptSpec::id).toList();

        for (String script : collectScripts(plan)) {
            assertTrue(
                    catalogScripts.contains(script),
                    "План уровня " + level + " кейса " + boardCase + " ссылается на скрипт вне " + "каталога: "
                            + script);
        }
    }

    @Provide
    Arbitrary<BoardCase> levelledCases() {
        return Arbitraries.of(
                BoardCase.BUILD_MINE,
                BoardCase.BUILD_PEN,
                BoardCase.ENCHANT_ROOM,
                BoardCase.BUILD_HOUSE,
                BoardCase.BUILD_WAREHOUSE);
    }

    @Provide
    Arbitrary<Integer> levels() {
        return Arbitraries.integers().between(1, 3);
    }

    @Test
    void craftItem_ToolCases_ReturnToolSetItem() {
        for (BoardCase boardCase : ToolSet.chain()) {
            assertEquals(
                    Optional.of(ToolSet.itemId(boardCase)),
                    TicketPlans.craftItem(boardCase),
                    "Кейс " + boardCase + " обязан назвать предмет своего инструмента: по нему "
                            + "TransferWatch ищет ингредиенты и передачу");
        }
    }

    @Test
    void plan_RepairTool_CarriesItem() {
        ScriptCall repair = findCall(TicketPlans.plan(BoardCase.REPAIR_TOOL, fullTarget()), "repair_tool");

        assertEquals(
                "minecraft:white_bed",
                repair.params().get("item"),
                "План ремонта инструмента обязан нести в repair_tool тот же предмет, что задание доски: "
                        + "без имени скрипт не знает, какой изношенный предмет чинить");
    }

    @Test
    void craftItem_SmithyCases_ReturnStationItem() {
        assertEquals(
                Optional.of("minecraft:anvil"),
                TicketPlans.craftItem(BoardCase.CRAFT_ANVIL),
                "Кейс крафта наковальни обязан называть предмет наковальни: по нему гейт считает "
                        + "железные блоки и слитки из рецепта");
        assertEquals(
                Optional.of("minecraft:smithing_table"),
                TicketPlans.craftItem(BoardCase.CRAFT_SMITHING_TABLE),
                "Кейс крафта стола кузнеца обязан называть предмет стола: по нему гейт считает "
                        + "материалы из рецепта");
    }

    @Test
    void craftItem_BuildHouse_Empty() {
        assertEquals(
                Optional.empty(),
                TicketPlans.craftItem(BoardCase.BUILD_HOUSE),
                "Строительный кейс не крафт: передача ингредиентов ему не назначается");
    }

    @Test
    void plan_CraftCases_CarryStationRadius() {
        List<BoardCase> craftCases = new ArrayList<>(ToolSet.chain());
        craftCases.add(BoardCase.STOCK_CHESTS);
        craftCases.add(BoardCase.CRAFTING_STATION);
        craftCases.add(BoardCase.CRAFT_ANVIL);
        craftCases.add(BoardCase.CRAFT_SMITHING_TABLE);
        craftCases.add(BoardCase.ENCHANT_SHELVES);
        for (BoardCase craftCase : craftCases) {
            TaskNode plan = TicketPlans.plan(craftCase, target());
            ScriptCall craft = findCall(plan, "craft");
            Object radius = craft.params().get("radius");

            assertTrue(
                    radius instanceof Integer distance
                            && distance >= CatalogLimits.STATION_RADIUS_MIN
                            && distance <= CatalogLimits.STATION_RADIUS_MAX,
                    "craft обязан нести радиус поиска станции в границах каталога: CraftParams.from "
                            + "читает radius обязательным, без него дух проваливает задание " + craftCase
                            + " на выдаче (радиус " + radius + ")");
        }
    }

    @Test
    void plan_SupplySite_DepositCarriesContainerRadius() {
        TaskNode plan = TicketPlans.plan(BoardCase.SUPPLY_SITE, target());
        ScriptCall deposit = findCall(plan, "deposit");
        Object radius = deposit.params().get("radius");

        assertTrue(
                radius instanceof Integer distance
                        && distance >= CatalogLimits.CONTAINER_RADIUS_MIN
                        && distance <= CatalogLimits.CONTAINER_RADIUS_MAX,
                "deposit обязан нести радиус поиска контейнера в границах каталога: TransferParams.from "
                        + "читает radius обязательным, без него план падает на выдаче и добыча не кладёт "
                        + "предметы в склад (радиус " + radius + ")");
    }

    @Test
    void plan_TransferItemFromSpirit_DeliversToRecipient() {
        UUID giver = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID taker = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        TaskNode plan = TicketPlans.plan(BoardCase.TRANSFER_ITEM, target().withTransfer(giver, taker, "stick", 2));
        ScriptCall deliver = findCall(plan, "deliver");

        assertEquals("stick", deliver.params().get("item"), "Передача везёт тот предмет, которого не хватает крафтеру");
        assertEquals(2, deliver.params().get("count"), "Передача везёт ровно нехватку");
        assertEquals(
                "spirit:" + taker,
                deliver.params().get("to"),
                "Адресат deliver — дух-крафтер по UUID: имя духа неоднозначно");
    }

    @Test
    void plan_TransferItemFromWarehouse_WithdrawsToRecipient() {
        UUID taker = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        TaskNode plan = TicketPlans.plan(BoardCase.TRANSFER_ITEM, target().withTransfer(null, taker, "cobblestone", 3));
        ScriptCall withdraw = findCall(plan, "withdraw");

        assertEquals(
                "cobblestone",
                withdraw.params().get("item"),
                "Со склада берут тот же предмет, которого не хватает крафтеру");
        assertEquals(3, withdraw.params().get("count"), "Со склада берут ровно нехватку");
        assertEquals("nearest", withdraw.params().get("container"), "Склад ищется ближайшим контейнером к получателю");
    }

    @Test
    void plan_BreedAnimals_BreedsSpeciesPair() {
        TaskNode plan = TicketPlans.plan(BoardCase.BREED_ANIMALS, target().withSpecies(Species.COW));
        ScriptCall breed = findCall(plan, "breed");

        assertEquals(
                "cow",
                breed.params().get("species"),
                "Разведение сводит пару своего вида: чужой вид в загоне плодит помеси мимо плана");
        assertEquals(
                CatalogLimits.HERD_PAIRS_DEFAULT,
                breed.params().get("pairs"),
                "Разведение сводит одну пару за запуск: приплод ограничен пределами каталога, "
                        + "а не аппетитом доски");
    }

    @Test
    void plan_HerdCull_CullsOwnSpecies() {
        TaskNode plan = TicketPlans.plan(BoardCase.HERD_CULL, target().withSpecies(Species.SHEEP));

        assertTrue(
                collectScripts(plan).contains("cull"),
                "Контроль численности идёт существующим скриптом cull: второй забойный " + "скрипт не заводим");
        assertEquals(
                "sheep",
                findCall(plan, "cull").params().get("species"),
                "Забивают лишний свой вид: чужой вид под нож пускать нельзя");
    }

    @Test
    void plan_RestockFood_WithdrawsOwnerCount() {
        TaskNode plan = TicketPlans.plan(BoardCase.RESTOCK_FOOD, target().withSupply("bread", 1));
        ScriptCall withdraw = findCall(plan, "withdraw");

        assertEquals(
                48,
                withdraw.params().get("count"),
                "Пополнение личной еды забирает со склада 48 единиц (ответы владельца 16.10): "
                        + "меньше — дух снова голоден через день, больше — тащит лишний запас");
        assertEquals(
                "bread",
                withdraw.params().get("item"),
                "Забирают названный продукт: склад отдаёт конкретное, а не «еду вообще»");
    }

    @Test
    void plan_Relocate_ClearsSourceAndBuildsDestination() {
        TaskNode plan = TicketPlans.plan(BoardCase.RELOCATE_BUILDING, fullTarget());
        List<String> scripts = collectScripts(plan);

        assertTrue(
                scripts.contains("clear_area") && scripts.contains("build_schematic"),
                "Перенос сносит постройку в from и строит ту же схему в to: перенос без сноса "
                        + "удвоил бы постройку, а без стройки — снёс бы её");
        ScriptCall clear = findCall(plan, "clear_area");
        assertEquals(
                true,
                clear.params().get("demolish"),
                "Снос при переносе идёт с demolish=true: свой забор и калитка стоят в списке "
                        + "защищённых блоков, и обычный clear_area не убрал бы ни одного блока");
    }

    @Test
    void plan_RelocateSamePoint_IsRejected() {
        TicketTarget same =
                target().withScheme("pen").withRelocate(new BlockCoord(10, 60, -5), new BlockCoord(10, 60, -5));

        assertThrows(
                IllegalArgumentException.class,
                () -> TicketPlans.plan(BoardCase.RELOCATE_BUILDING, same),
                "Перенос в ту же точку обязан падать: сносить и строить на одном месте нечего, "
                        + "а молча принятый перенос гонял бы духа вхолостую");
    }

    private static ScriptCall findCall(TaskNode root, String script) {
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> {
                    if (call.script().equals(script)) {
                        return call;
                    }
                }
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case RepeatNode repeat -> queue.add(repeat.step());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                case ParallelNode parallel -> queue.addAll(parallel.steps());
                case ConditionNode condition -> {
                    queue.add(condition.thenStep());
                    if (condition.elseStep() != null) {
                        queue.add(condition.elseStep());
                    }
                }
            }
        }
        throw new AssertionError("В плане нет вызова скрипта " + script + ": без него задание не работает");
    }

    @Property
    void plan_AnyPlannedCase_ValidByCatalog(@ForAll("plannedCases") BoardCase boardCase) {
        TaskNode plan = TicketPlans.plan(boardCase, fullTarget().withCraterBlocks(29));

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    @Property
    void plan_AnyPlannedCase_OnlyCatalogScripts(@ForAll("plannedCases") BoardCase boardCase) {
        TaskNode plan = TicketPlans.plan(boardCase, fullTarget().withCraterBlocks(29));
        List<String> catalogScripts =
                CoreScripts.defaultCatalog().all().stream().map(ScriptSpec::id).toList();

        for (String script : collectScripts(plan)) {
            assertTrue(
                    catalogScripts.contains(script),
                    "План не обязан ссылаться на скрипты вне каталога: " + script
                            + " — иначе дух получит задание с неизвестным шагом");
        }
    }

    private static List<ScriptCall> collectCalls(TaskNode root, String script) {
        List<ScriptCall> calls = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> {
                    if (call.script().equals(script)) {
                        calls.add(call);
                    }
                }
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case RepeatNode repeat -> queue.add(repeat.step());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                case ParallelNode parallel -> queue.addAll(parallel.steps());
                case ConditionNode condition -> {
                    queue.add(condition.thenStep());
                    if (condition.elseStep() != null) {
                        queue.add(condition.elseStep());
                    }
                }
            }
        }
        return calls;
    }

    private static List<String> collectScripts(TaskNode root) {
        List<String> scripts = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> scripts.add(call.script());
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case RepeatNode repeat -> queue.add(repeat.step());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                case ParallelNode parallel -> queue.addAll(parallel.steps());
                case ConditionNode condition -> {
                    queue.add(condition.thenStep());
                    if (condition.elseStep() != null) {
                        queue.add(condition.elseStep());
                    }
                }
            }
        }
        return scripts;
    }

    @Provide
    Arbitrary<BoardCase> plannedCases() {
        return Arbitraries.of(PLANNED_CASES);
    }
}
