package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.FirstStepsPlan;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.TorchRules;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.OwnWood;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * Генератор первых шагов: по фактам поселения ставит очередное задание из цепочки.
 * Шаг с материалами из рецепта или схемы сначала требует запас: не хватает — на доску
 * встают мелкие задачи добычи, а сам шаг ждёт их выполнения и открывается после.
 */
public final class FirstStepsRunner {
    private static final Set<BoardCase> WOODEN_BUILDINGS = EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);
    private static final int LAYOUT_SLACK_DIVISOR = 50;
    private static final Set<BoardCase> LAYOUT_STEPS =
            EnumSet.of(BoardCase.LIGHTING, BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);
    // Заранее — только с расчистки: раньше добыча забора отнимала доски у первых инструментов (простой 1947 тиков).
    private static final Set<BoardCase> PREFETCH_DURING =
            EnumSet.of(BoardCase.CLEAR_GROUNDS, BoardCase.LIGHTING, BoardCase.BUILD_FENCE);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCAN_RADIUS = 16;
    private static final String GATHER_PREFIX = "gather:";
    private static final String GATHER_PART_SUFFIX = ":2";
    private static final List<BoardCase> STONE_STEPS = List.of(
            BoardCase.CRAFT_STONE_PICKAXE,
            BoardCase.CRAFT_STONE_AXE,
            BoardCase.CRAFT_STONE_SHOVEL,
            BoardCase.CRAFT_STONE_SWORD,
            BoardCase.CRAFT_FURNACE);
    private static final String FIRST_PREFIX = "first:";
    private static final String MINECRAFT = "minecraft:";
    private static final String CLEAR_PART_PREFIX = "clear:";
    // Каменный шаг, открытый вместе с текущим, — помощник шага (как четверти расчистки): «first:» всегда один.
    private static final String STONE_PART_PREFIX = "stone:";
    private static final String FENCE_PART_PREFIX = "fence:";
    private static final String COBBLESTONE = "cobblestone";
    private static final String FURNACE_ITEM = "furnace";
    private static final int FEET_SCAN_DEPTH = 48;
    private static final int EXPAND_DEPTH = 4;
    private static final int LEVELING_SHOWN = 6;

    private FirstStepsRunner() {}

    // Как мир сверяется с планом поселения: AUDIT — постройки плана сверяются с миром (боевой режим);
    // JOURNAL — сделанная по журналу постройка считается стоящей. JOURNAL — только сценам GameTest, проверяющим шаги
    // после построек плана (наковальня), где участок 23×23 и спуск до y=−58 на арене 11×11 не поставить.
    public enum PlanWorld {
        AUDIT,
        JOURNAL
    }

    public static void tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        tick(level, service, spirits, stock, now, PlanWorld.AUDIT);
    }

    public static void tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            long now,
            PlanWorld world) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        // Поселение задаёт дом: без него центра участка нет и доска выдала бы first:*
        // духу без дома (сцена REST получала чужое задание до своего POST).
        if (spirits.stream().noneMatch(spirit -> spirit.home().isPresent())) {
            return;
        }
        // Якорь участка — один на поселение, зафиксирован в мире: от него выравнивание и все постройки раскладки.
        BlockCoord anchor = plotOf(level, service, spirits, now).anchor();
        BlockCoord home = new BlockCoord(anchor.x(), homeY(spirits), anchor.z());
        String wood = woodOf(level, home, stock);
        Scan scan = buildFacts(level, new StoneSite(home, wood), anchor, stock, service, world);
        SettlementFacts facts = scan.facts();
        boolean mine = facts.mineEntrance();
        List<BoardCase> ready = FirstStepsPlan.ready(facts);
        Optional<BoardCase> next = ready.stream().findFirst();
        String log = WoodSpecies.swap("oak_log", wood);
        dropOtherWood(level, service, wood);
        ensurePickaxe(level, service, spirits, stock, new StoneSite(home, wood), next, now);
        if (ready.size() == 1 && STONE_STEPS.contains(next.orElseThrow())) {
            parallelStone(level, service, facts, new StoneSite(home, wood), stock, next.orElseThrow(), now);
        }
        next.filter(PREFETCH_DURING::contains)
                .filter(current -> hasActive(service, current))
                .ifPresent(current -> prefetch(level, service, facts, home, stock, current, now));
        int torches = 0;
        for (BoardCase caseValue : ready) {
            if (!hasActive(service, caseValue)) {
                StepContext context = new StepContext(level, service, spirits, stock, facts, anchor, wood, now);
                torches = Math.max(torches, openIfStocked(context, caseValue));
            }
        }
        TorchWatch.tick(
                service, spirits, home, stock, new TorchWatch.Site(scan.furnaceBlock(), mine, torches, log), now);
    }

    // Шаг открывается, когда его материалы в запасе; нехватка — добыча. Возвращает факелы шага сверх запаса.
    // Деревянное стройки (забор, ступени, дверь, доски) строители крафтят сами из брёвен поселения: шаг не ждёт его
    // в запасе, когда брёвен хватает (сцена 56: ограда ждала забор ×82, куски шли по одному через передачи).
    private static void dropOwnWood(Map<String, Integer> need, StockView stock, String wood) {
        int cost = 0;
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : need.entrySet()) {
            if (wood.equals(OwnWood.species(entry.getKey()))) {
                cost += OwnWood.plankCost(entry.getKey(), entry.getValue());
                items.add(entry.getKey());
            }
        }
        if (!items.isEmpty() && OwnWood.planksIn(stock.available(), wood) >= cost) {
            items.forEach(need::remove);
        }
    }

    private record StepContext(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            SettlementFacts facts,
            BlockCoord anchor,
            String wood,
            long now) {}

    private static int openIfStocked(StepContext context, BoardCase caseValue) {
        ServerLevel level = context.level();
        BoardService service = context.service();
        List<SpiritEntity> spirits = context.spirits();
        StockView stock = context.stock();
        SettlementFacts facts = context.facts();
        BlockCoord anchor = context.anchor();
        String wood = context.wood();
        long now = context.now();
        BlockCoord home = new BlockCoord(anchor.x(), homeY(spirits), anchor.z());
        TicketTarget target = targetFor(caseValue, anchor, wood);
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            target = target.withLeveling(leveling(level, anchor, stock));
        }
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = new HashMap<>();
        // Доски, брёвна и деревянные блоки — породы поселения: в джунглях «oak_planks» не добыть (INC-36).
        materials(source, caseValue, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            // Землю на подсыпку расчистка берёт сама — со срезки и добычей за оградой (INC-68): ждать её в запасе
            // до открытия шага — вечное ожидание, земля под травой участка доской не добывается (сцена 56, сид
            // 519074267563303498: dirt=96 ждала 6000 тиков).
            need.remove(PlotLeveling.DEFAULT_BLOCK);
        }
        if (caseValue == BoardCase.BUILD_MINE) {
            // Пол и стенки спуска над землёй и в пустотах кладутся из запаса: строитель шахты не нёс ни булыжника, ни
            // земли — их сдала в сундук расчистка — и падал «нет булыжника на стенку» (сцена 56).
            int floors = mineFloorsToPlace(level, anchor);
            String block = stock.available().getOrDefault(COBBLESTONE, 0) >= floors
                            || stock.available().getOrDefault(PlotLeveling.DEFAULT_BLOCK, 0) < floors
                    ? COBBLESTONE
                    : PlotLeveling.DEFAULT_BLOCK;
            if (floors > 0) {
                need.merge(block, floors, Integer::sum);
            }
        }
        if (WOODEN_BUILDINGS.contains(caseValue)) {
            // Повтор проваленной стройки просит только непоставленное: после провала ограды на 91/94 доска снова
            // заказывала забор ×72 на всю схему — 1800 тиков лишней добычи (приёмка «джунгли» 24.10).
            minusPlaced(level, TicketPlans.plan(caseValue, 1, target), wood, need);
            dropOwnWood(need, stock, wood);
        }
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), stock);
        // Факелы шага (ограда — на столбах углов и калитки) — сверх обычного запаса факелов: иначе шаг ждал бы их
        // вечно.
        int torches = need.getOrDefault(TorchRules.TORCH, 0);
        if (!missing.isEmpty()) {
            LOGGER.info("Кейс первых шагов {} ждёт: не хватает {}", caseValue, missing);
            for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                if (gatherable(entry.getKey(), facts)) {
                    int shortage = entry.getValue();
                    if (COBBLESTONE.equals(entry.getKey())) {
                        shortage = Math.max(shortage, stoneAhead(source, facts, anchor, wood, stock));
                    }
                    postGather(service, source, home, entry.getKey(), shortage, helpers(entry.getKey(), spirits), now);
                }
            }
            return torches;
        }
        openStep(level, service, new OpenStep(caseValue, target, need, wood), source, now);
        return torches;
    }

    private record OpenStep(BoardCase caseValue, TicketTarget target, Map<String, Integer> need, String wood) {}

    private static void openStep(
            ServerLevel level, BoardService service, OpenStep step, MaterialSource source, long now) {
        BoardCase caseValue = step.caseValue();
        TicketTarget target = step.target();
        Map<String, Integer> need = step.need();
        String wood = step.wood();
        // Расчистка — четвертями, ограда — кусками периметра на разных духов: одним духом расчистка шла ~4000 тиков,
        // ограда ~2900, двое простаивали (приёмка «джунгли» 24.10). Первая часть — first:, остальные — помощники.
        int parts = partsOf(caseValue);
        if (LAYOUT_STEPS.contains(caseValue)
                || caseValue == BoardCase.CLEAR_GROUNDS
                || caseValue == BoardCase.BUILD_MINE) {
            if (reopenDone(level, service, step, parts, now)) {
                return;
            }
        }
        BoardOutcome outcome = BoardOutcome.ACCEPTED;
        for (int part = 1; part <= parts; part++) {
            TicketTarget partTarget = parts == 1 ? target : target.withCount(part);
            Map<String, Integer> reserve = part == 1 ? need : Map.of();
            if (caseValue == BoardCase.BUILD_FENCE) {
                // Ограда — кусками периметра (решение главного агента 24.10): у куска своя бронь — материалы его
                // клеток без поставленного, строители не делят один материал (проба fence:2 давала «141 из 94»).
                // Деревянное, которое строители крафтят сами (снято с нужд шага), куску не бронируется.
                reserve = new HashMap<>(partNeed(level, source, partTarget, wood));
                reserve.keySet().retainAll(need.keySet());
            }
            BoardOutcome submitted = service.submit(
                    new BoardTicket(
                            partId(caseValue, part),
                            caseValue,
                            Optional.empty(),
                            BoardRules.priorityOf(caseValue),
                            // Части — в очередь по номеру: при равном времени доска брала по имени, «clear:N» раньше
                            // «first:», и первая часть шла последней одним духом, двое стояли (сцена 56).
                            now + part - 1,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            // Материалы шага — бронь доски: фоновые занятия их не трогают (BoardReserve).
                            Map.copyOf(reserve)),
                    partTarget);
            if (part == 1) {
                outcome = submitted;
            }
        }
        if (outcome == BoardOutcome.ACCEPTED) {
            // Только реально поставленный шаг: BLOCKED/FAILED-тикет доска не переоткрывает, и строка «открыт»
            // каждые 5 с вводила в заблуждение (INC-26).
            LOGGER.info("Кейс первых шагов {} открыт: материалы на месте", caseValue);
        }
    }

    // Постройка раскладки сделана по журналу, но в мире её нет (DONE старой сборки, сломана): задание и его куски
    // снимаются с доски вместе с отметкой — иначе submit отвечает DUPLICATE и шаг вечно «сделан» (INC-50).
    // Постройка плана построена (DONE), но мир с ней разошёлся: схема — задание REPAIR_BUILDING «восстановление»
    // (INC-60), прочее (свет, шахта, выравнивание) — шаг снова в очереди. true — восстановление поставлено.
    private static boolean reopenDone(ServerLevel level, BoardService service, OpenStep step, int parts, long now) {
        BoardCase caseValue = step.caseValue();
        if (service.board().statusOf(partId(caseValue, 1)).orElse(null) != BoardStatus.DONE) {
            return false;
        }
        Placed placed = audit(level, caseValue, step.target().target(), step.wood());
        int missing = placed.total() - placed.done();
        Optional<TicketTarget> scheme = RepairPlan.target(TicketPlans.plan(caseValue, 1, step.target()));
        LOGGER.info(
                "[layout] {} сделан по журналу, но в мире нет {} из {} блоков плана: {}",
                caseValue,
                missing,
                placed.total(),
                scheme.isPresent() ? "восстановление" : "шаг снова в очереди");
        service.journal()
                .recordEntry(BoardLog.note(
                        "layout:" + caseValue.name().toLowerCase(Locale.ROOT),
                        caseValue,
                        now,
                        List.of("missing=" + missing, "total=" + placed.total()),
                        null,
                        ConfigStamp.current()));
        if (scheme.isPresent()) {
            BoardOutcome repair = submitRepair(service, step, scheme.orElseThrow(), now);
            if (repair == BoardOutcome.ACCEPTED || repair == BoardOutcome.DUPLICATE) {
                return true;
            }
        }
        for (int part = 1; part <= parts; part++) {
            String id = partId(caseValue, part);
            if (service.board().statusOf(id).orElse(null) == BoardStatus.DONE) {
                service.reopen(id);
            }
        }
        return false;
    }

    private static BoardOutcome submitRepair(BoardService service, OpenStep step, TicketTarget target, long now) {
        String id = RepairPlan.PREFIX + step.caseValue().name().toLowerCase(Locale.ROOT);
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.REPAIR_BUILDING),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.copyOf(step.need())),
                target);
        if (outcome == BoardOutcome.ACCEPTED) {
            LOGGER.info(
                    "[layout] восстановление {}: задание {} по схеме {}", step.caseValue(), id, target.schemeFile());
        }
        return outcome;
    }

    // Добыча под другую породу снимается: порода поселения меняется по голосам (запас, деревья рядом, биом), а
    // gather:oak_fence, поставленный до смены, висел PENDING рядом с выбранной jungle (INC-42, живой 24.10).
    public static void dropOtherWood(ServerLevel level, BoardService service, String wood) {
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (!ticket.id().startsWith(GATHER_PREFIX) || !active(service, ticket.id())) {
                continue;
            }
            String item = bare(ticket.id().substring(GATHER_PREFIX.length()));
            Optional<String> species = WoodSpecies.speciesOf(item);
            if (species.isPresent() && !species.orElseThrow().equals(wood) && service.cancel(level, ticket.id())) {
                LOGGER.info("[wood] снята добыча {}: порода поселения {}", ticket.id(), wood);
            }
        }
    }

    // Этап 1 ещё строится: открыт шаг первых шагов, кусок ограды или часть расчистки. Отметку журнала постройке
    // ставит первый сделанный кусок, и этап 2 (уровень шахты, склад) начинался посреди этапа 1 (сцена 56).
    public static boolean stageOneBuilding(BoardService service) {
        return service.board().all().stream()
                .anyMatch(ticket -> (ticket.id().startsWith(FIRST_PREFIX)
                                || ticket.id().startsWith(FENCE_PART_PREFIX)
                                || ticket.id().startsWith(CLEAR_PART_PREFIX))
                        && active(service, ticket.id()));
    }

    private static int partsOf(BoardCase caseValue) {
        return switch (caseValue) {
            case CLEAR_GROUNDS -> TicketPlans.CLEAR_PARTS;
            case BUILD_FENCE -> TicketPlans.FENCE_PARTS;
            default -> 1;
        };
    }

    private static String partId(BoardCase caseValue, int part) {
        if (part == 1) {
            return FIRST_PREFIX + caseValue.name().toLowerCase(Locale.ROOT);
        }
        return (caseValue == BoardCase.BUILD_FENCE ? FENCE_PART_PREFIX : CLEAR_PART_PREFIX) + part;
    }

    private static Map<String, Integer> partNeed(
            ServerLevel level, MaterialSource source, TicketTarget target, String wood) {
        Map<String, Integer> need = new HashMap<>();
        materials(source, BoardCase.BUILD_FENCE, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        minusPlaced(level, TicketPlans.plan(BoardCase.BUILD_FENCE, 1, target), wood, need);
        return need;
    }

    // Каменные шаги после текущего — сразу, если булыжника в запасе хватает на все вместе: по одному шагу за раз
    // инструменты и печь крафтил один дух ~2000 тиков, двое простаивали (приёмка «джунгли» 24.10, тики 4216–6604).
    private static void parallelStone(
            ServerLevel level,
            BoardService service,
            SettlementFacts facts,
            StoneSite site,
            StockView stock,
            BoardCase current,
            long now) {
        BlockCoord home = site.home();
        String wood = site.wood();
        MaterialSource source = materialSource(level);
        Map<String, Integer> booked = new HashMap<>();
        boolean after = false;
        for (BoardCase step : STONE_STEPS) {
            TicketTarget target = targetFor(step, home, wood);
            Map<String, Integer> need = new HashMap<>();
            materials(source, step, target)
                    .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
            if (step == current) {
                after = true;
            }
            if (facts.done().contains(step)) {
                continue;
            }
            need.forEach((item, count) -> booked.merge(item, count, Integer::sum));
            if (!after || step == current || hasActive(service, step)) {
                continue;
            }
            if (!ResourceGate.missing(new StockNeed(booked), stock).isEmpty()) {
                return;
            }
            BoardOutcome outcome = service.submit(
                    new BoardTicket(
                            STONE_PART_PREFIX + step.name().toLowerCase(Locale.ROOT),
                            step,
                            Optional.empty(),
                            BoardRules.priorityOf(step),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.copyOf(need)),
                    target);
            if (outcome == BoardOutcome.ACCEPTED) {
                LOGGER.info("Каменный шаг {} открыт вместе с {}: булыжника хватает на оба", step, current);
            }
        }
    }

    // Выравнивание участка (решение 16): срезка и подсыпка по высотам колонок; яма — булыжником, если его хватает в
    // запасе, иначе землёй.
    private static PlotLeveling leveling(ServerLevel level, BlockCoord anchor, StockView stock) {
        PlotLeveling plan = PlotLeveling.plan(anchor, SettlementLayout.LEVEL_HALF, plotFeet(level, anchor));
        IntBinaryOperator feet = (x, z) ->
                SettlementLayout.mineColumn(anchor, x, z) ? Integer.MIN_VALUE : feetAt(level, x, z, anchor.y());
        plan = plan.withVoids(PlotLeveling.voids(
                anchor,
                SettlementLayout.HALF,
                feet,
                (x, y, z) -> level.getBlockState(new BlockPos(x, y, z)).isAir()));
        if (plan.fillVolume() > 0 && stock.available().getOrDefault(COBBLESTONE, 0) >= plan.fillVolume()) {
            plan = plan.withBlock(COBBLESTONE);
        }
        if (!plan.cut().isEmpty() || !plan.fill().isEmpty()) {
            LOGGER.info(
                    "Выравнивание участка на y={}: срезка {} ящиков, подсыпка {} блоков {}, пустот под участком {}",
                    anchor.y(),
                    plan.cut().size(),
                    plan.fillVolume(),
                    plan.block(),
                    plan.voids().size());
            if (plan.cut().size() + plan.fill().size() <= LEVELING_SHOWN) {
                // Остаток выравнивания из пары ящиков — их координаты: повтор по кругу из лога виден сразу (сцена 56).
                LOGGER.info("[plot] остаток выравнивания: срезка {}, подсыпка {}", plan.cut(), plan.fill());
            }
        }
        return plan;
    }

    // Высоты колонок участка для выравнивания: спуск в шахту — на уровне участка, его не засыпают (INC-50).
    private static int descentEnd(BlockCoord anchor) {
        try {
            return MineDescentBottom.standOf(anchor).map(BlockCoord::z).orElse(Integer.MAX_VALUE);
        } catch (IllegalStateException | IllegalArgumentException unbuildable) {
            return Integer.MAX_VALUE;
        }
    }

    private static IntBinaryOperator plotFeet(ServerLevel level, BlockCoord anchor) {
        // Колонки стенок спуска: яма засыпается (иначе у спуска оставалась яма, и строители ограды падали в неё по
        // 1000+ тиков, сцена 56), бугор (стенка над землёй у входа) не срезается. Проход — на уровне участка.
        // Проход за низом спуска — обычная земля участка: колонка спуска до края участка длиннее самого спуска.
        int passageEnd = descentEnd(anchor);
        return (x, z) -> {
            if (SettlementLayout.minePassage(anchor, x, z) && z <= passageEnd) {
                return anchor.y();
            }
            int feet = feetAt(level, x, z, anchor.y());
            return SettlementLayout.mineColumn(anchor, x, z) ? Math.min(feet, anchor.y()) : feet;
        };
    }

    // Уровень ног колонки — над верхним блоком земли; постройки, станции, деревья и растения землёй не считаются.
    private static int feetAt(ServerLevel level, int x, int z, int fallback) {
        BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        for (int step = 0; step < FEET_SCAN_DEPTH; step++) {
            pos.move(0, -1, 0);
            if (!level.isLoaded(pos)) {
                return fallback;
            }
            if (isGround(level.getBlockState(pos))) {
                return pos.getY() + 1;
            }
        }
        return fallback;
    }

    private static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.CLAY)
                // Вода — не земля участка: пруд в низине засыпается до дна, а не «подсыпка над водой», где у ямы нет
                // ни стоянки, ни опоры, и выравнивание шло по кругу (сцена 56, сид −1042323956605046699).
                || (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER));
    }

    // Пока шаг идёт (расчистку ведёт один дух), материалы ближайшей деревянной стройки добываются заранее: иначе
    // двое духов простаивали всю расчистку (приёмка «джунгли» 23.10: простой 3844 тика при открытой расчистке).
    private static void prefetch(
            ServerLevel level,
            BoardService service,
            SettlementFacts facts,
            BlockCoord home,
            StockView stock,
            BoardCase current,
            long now) {
        Optional<BoardCase> building = FirstStepsPlan.nextAfter(facts, current, WOODEN_BUILDINGS);
        if (building.isEmpty() || hasActive(service, building.orElseThrow())) {
            return;
        }
        String wood = woodOf(level, home, stock);
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = new HashMap<>();
        materials(source, building.orElseThrow(), targetFor(building.orElseThrow(), home, wood))
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        // Впрок — только сырьё (брёвна): добыча крафтового ждала бы передач от занятых духов и сама простаивала.
        Map<String, Integer> raw = new HashMap<>();
        ResourceGate.missing(new StockNeed(need), stock).forEach((item, count) -> expand(source, item, count, raw, 0));
        Map<String, Integer> rawNeed = new HashMap<>();
        raw.forEach((item, count) -> rawNeed.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        ResourceGate.missing(new StockNeed(rawNeed), stock).forEach((item, count) -> {
            if (gatherable(item, facts)) {
                postGather(service, source, home, item, count, 1, now);
            }
        });
    }

    private static void expand(MaterialSource source, String item, int count, Map<String, Integer> raw, int depth) {
        List<Ingredient> ingredients = depth < EXPAND_DEPTH ? source.ingredientsFor(item) : List.of();
        int output = source.outputCount(item);
        if (ingredients.isEmpty() || output <= 0) {
            raw.merge(
                    item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item, count, Integer::sum);
            return;
        }
        int crafts = (count + output - 1) / output;
        for (Ingredient ingredient : ingredients) {
            expand(source, ingredient.item(), ingredient.count() * crafts, raw, depth + 1);
        }
    }

    private static String woodOf(ServerLevel level, BlockCoord home, StockView stock) {
        return SettlementWood.of(level, home, stock.available());
    }

    private static TicketTarget targetFor(BoardCase caseValue, BlockCoord home, String wood) {
        TicketTarget target = TicketTarget.at(home);
        if (WOODEN_BUILDINGS.contains(caseValue) && !WoodSpecies.OAK.equals(wood)) {
            target = target.withWood(wood);
        }
        // По одному инструменту за задание: N штук одному крафтеру не собрать из запаса, разнесённого по духам;
        // остальные до набора на духа ставит постоянный запас (ToolStockWatch).
        return ToolSet.isTool(caseValue) ? target.withToolCount(1) : target;
    }

    // Булыжник на все оставшиеся каменные шаги разом: добыча по 3 на каждый инструмент шла цепочкой «добыл → скрафтил
    // → добыл», двое духов простаивали (приёмка «джунгли» 24.10, тики 2099–4619). Добытое лежит в сундуке склада, и
    // фоновая плавка его на печь не тратит (считает только инвентари).
    private static int stoneAhead(
            MaterialSource source, SettlementFacts facts, BlockCoord anchor, String wood, StockView stock) {
        int need = 0;
        for (BoardCase step : STONE_STEPS) {
            if (!facts.done().contains(step)) {
                need += materials(source, step, targetFor(step, anchor, wood)).getOrDefault(COBBLESTONE, 0);
            }
        }
        return Math.max(0, need - stock.available().getOrDefault(COBBLESTONE, 0));
    }

    // Сколько духов могут добывать: булыжник и уголь — только с киркой (вторая доля без кирки падала «нужен
    // инструмент (кирка) для stone», 24.10), остальное — любой.
    private static int helpers(String item, List<SpiritEntity> spirits) {
        if (!COBBLESTONE.equals(item) && !TorchRules.COAL.equals(item)) {
            return spirits.size();
        }
        int miners = 0;
        for (SpiritEntity spirit : spirits) {
            var inventory = spirit.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).getItem() instanceof PickaxeItem) {
                    miners++;
                    break;
                }
            }
        }
        return miners;
    }

    private static void postGather(
            BoardService service,
            MaterialSource source,
            BlockCoord home,
            String item,
            int shortage,
            int helpers,
            long now) {
        String id = GATHER_PREFIX + item;
        if (active(service, id) || active(service, id + GATHER_PART_SUFFIX)) {
            return;
        }
        int batch = GatherBatch.round(shortage, source.outputCount(item));
        // Большая добыча — на двух духов: один дух копал всё, остальные стояли.
        List<Integer> portions = GatherBatch.split(batch, source.outputCount(item), helpers);
        for (int part = 0; part < portions.size(); part++) {
            submitGather(service, home, part == 0 ? id : id + GATHER_PART_SUFFIX, item, portions.get(part), now);
        }
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static void submitGather(
            BoardService service, BlockCoord home, String id, String item, int batch, long now) {
        LOGGER.info("Поставлено: добыть {} ×{} (кейс первых шагов ждёт)", item, batch);
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.GATHER_MATERIALS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(home).withSupply(item, batch));
    }

    static Map<String, Integer> materials(MaterialSource source, BoardCase caseValue, TicketTarget target) {
        try {
            TaskNode plan = TicketPlans.plan(caseValue, 1, target);
            return CaseMaterials.need(plan, source);
        } catch (RuntimeException failure) {
            LOGGER.error("Материалы шага первых шагов {} не посчитаны: {}", caseValue, failure.getMessage());
            return Map.of();
        }
    }

    static MaterialSource materialSource(ServerLevel level) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(level);
        return new MaterialSource() {
            @Override
            public int outputCount(String item) {
                return knowledge.outputCount(item);
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return knowledge.ingredientsFor(item);
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                // Шахта копается в породе: её пол — добытый ею же булыжник, на складе его до шахты нет и быть не
                // должно.
                boolean underground = DataTables.INSTANCE
                        .presets()
                        .current()
                        .byId(file)
                        .map(preset -> preset.type() == BuildingType.UNDERGROUND)
                        .orElse(false);
                return underground ? Map.of() : PresetLibrary.countMaterials(SchematicResources.load(file));
            }
        };
    }

    // Кирки нет ни у одного духа, а расчистка или шахта ждут её (гейт ToolGate, INC-67): задание на деревянную кирку,
    // без досок и палок — добыча дерева на них.
    private static void ensurePickaxe(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            StoneSite site,
            Optional<BoardCase> next,
            long now) {
        boolean gated = next.filter(step -> ToolGate.required(step).isPresent()).isPresent()
                || hasActive(service, BoardCase.CLEAR_GROUNDS)
                || hasActive(service, BoardCase.BUILD_MINE);
        boolean nobody = spirits.stream()
                .noneMatch(spirit -> ToolGate.allows(BoardCase.CLEAR_GROUNDS, BoardService.itemIds(spirit)));
        if (!gated || !nobody || active(service, BoardService.PICKAXE_TICKET)) {
            return;
        }
        MaterialSource source = materialSource(level);
        TicketTarget target = targetFor(BoardCase.CRAFT_WOODEN_PICKAXE, site.home(), site.wood());
        Map<String, Integer> need = new HashMap<>();
        materials(source, BoardCase.CRAFT_WOODEN_PICKAXE, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, site.wood()), count, Integer::sum));
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(need), stock);
        if (!missing.isEmpty()) {
            LOGGER.info("[tool] кирки нет ни у одного духа: на кирку не хватает {} — добыча", missing);
            missing.forEach((item, count) -> postGather(service, source, site.home(), item, count, 1, now));
            return;
        }
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        BoardService.PICKAXE_TICKET,
                        BoardCase.CRAFT_WOODEN_PICKAXE,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.CRAFT_WOODEN_PICKAXE),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.copyOf(need)),
                target);
        if (outcome == BoardOutcome.ACCEPTED) {
            LOGGER.info("[tool] кирки нет ни у одного духа: поставлено {}", BoardService.PICKAXE_TICKET);
        }
    }

    private static boolean hasActive(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(t -> t.boardCase() == boardCase)
                .anyMatch(t -> service.board()
                        .statusOf(t.id())
                        .map(s -> s == BoardStatus.PENDING
                                || s == BoardStatus.WAITING_RESOURCES
                                || s == BoardStatus.ASSIGNED)
                        .orElse(false));
    }

    // Факелы и уголь ставит TorchWatch; булыжник добывается в шахте — до её спуска добыча камня не ставится.
    private static boolean gatherable(String item, SettlementFacts facts) {
        if (TorchRules.handles(item)) {
            return false;
        }
        // Камень — только из шахты плана: старая яма с лестницей шахтой не считается (INC-59).
        return !COBBLESTONE.equals(item) || facts.mineEntrance();
    }

    // Блоки схем плана, уже стоящие в мире, вычитаются из нужды шага (схема без поворота, порода — поселения).
    public static void minusPlaced(ServerLevel level, TaskNode plan, String wood, Map<String, Integer> need) {
        switch (plan) {
            case SequenceNode sequence -> sequence.steps().forEach(step -> minusPlaced(level, step, wood, need));
            case ScriptCall call
            when "build_schematic".equals(call.script())
                    && !call.params().containsKey("rotation")
                    && call.params().get("file") instanceof String file
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z -> {
                for (Schematic.Entry entry : SchematicResources.load(file).blocks()) {
                    if (entry.optional() || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                        continue;
                    }
                    String block = bare(WoodSpecies.swap(entry.block(), wood));
                    BlockState state =
                            level.getBlockState(new BlockPos(x + entry.dx(), y + entry.dy(), z + entry.dz()));
                    if (placed(state, block)) {
                        String item = bare(WoodSpecies.swap(SchematicBlocks.itemOf(entry.block()), wood));
                        need.computeIfPresent(item, (key, count) -> count > 1 ? count - 1 : null);
                    }
                }
            }
            default -> {}
        }
    }

    private static String bare(String id) {
        return id.startsWith(MINECRAFT) ? id.substring(MINECRAFT.length()) : id;
    }

    private record Scan(SettlementFacts facts, boolean furnaceBlock) {}

    private record StoneSite(BlockCoord home, String wood) {}

    private static Scan buildFacts(
            ServerLevel level,
            StoneSite site,
            BlockCoord anchor,
            StockView stock,
            BoardService service,
            PlanWorld world) {
        BlockCoord home = site.home();
        boolean groundsDone = service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1);
        boolean hasHome = true;
        boolean groundsCleared = true;
        int chests = 0;
        boolean craftingStation = false;
        boolean smithingTable = false;
        boolean anvil = false;
        boolean furnace = false;
        BlockPos start = new BlockPos(home.x(), home.y(), home.z());
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                    continue;
                }
                BlockPos pos = start.offset(dx, 0, dz);
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos check = pos.offset(0, dy, 0);
                    if (!level.isLoaded(check)) {
                        continue;
                    }
                    var state = level.getBlockState(check);
                    if (!groundsDone && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
                        groundsCleared = false;
                    }
                    if (state.is(Blocks.CHEST)) {
                        chests++;
                    }
                    if (state.is(Blocks.CRAFTING_TABLE)) {
                        craftingStation = true;
                    }
                    if (state.is(Blocks.FURNACE)) {
                        furnace = true;
                    }
                    if (state.is(Blocks.SMITHING_TABLE)) {
                        smithingTable = true;
                    }
                    if (state.getBlock() instanceof AnvilBlock) {
                        anvil = true;
                    }
                }
            }
        }
        Set<String> tools = toolStock(stock);
        Set<BoardCase> done = journalSteps(service, false);
        Set<BoardCase> blocked = journalSteps(service, true);
        boolean furnaceAvailable = furnace || stock.available().getOrDefault(FURNACE_ITEM, 0) > 0;
        // Ровность — свой факт мира (INC-50): голый неровный участок без деревьев тоже выравнивается.
        // Сделанная по журналу расчистка без ровного участка (с полосой за оградой) снова в очереди: ограда — только
        // на ровном (INC-53).
        boolean journal = world == PlanWorld.JOURNAL;
        boolean plotLevel =
                journal || PlotLeveling.isLevel(anchor, SettlementLayout.LEVEL_HALF, plotFeet(level, anchor));
        // Постройки раскладки сверяются с миром только сделанные по журналу: несделанные и так в очереди.
        // Шахта — постройка плана: спуск mine_descent_v1 на своём месте сверяется по схеме, а не «лестница рядом»
        // (INC-59: старая яма засчитывалась шахтой).
        boolean mineEntrance = built(level, done, BoardCase.BUILD_MINE, anchor, site, journal);
        boolean lighting = built(level, done, BoardCase.LIGHTING, anchor, site, journal);
        boolean fence = built(level, done, BoardCase.BUILD_FENCE, anchor, site, journal);
        boolean houses = built(level, done, BoardCase.BUILD_HOUSE, anchor, site, journal);
        SettlementFacts facts = new SettlementFacts(
                hasHome,
                groundsCleared,
                plotLevel,
                chests,
                craftingStation,
                tools,
                mineEntrance,
                furnaceAvailable,
                fence,
                lighting,
                houses,
                smithingTable,
                anvil,
                done,
                blocked);
        return new Scan(facts, furnace);
    }

    // Постройка раскладки стоит в мире целиком: каждый блок её плана (факел, схема без поворота) на своей клетке.
    private static boolean built(
            ServerLevel level,
            Set<BoardCase> done,
            BoardCase step,
            BlockCoord anchor,
            StoneSite site,
            boolean journal) {
        if (!done.contains(step) || journal) {
            return done.contains(step);
        }
        Placed placed = audit(level, step, anchor, site.wood());
        // Допуск — 2% клеток (трава, лиана на месте блока схемы не возвращают стройку по кругу); 4 факела — без
        // допуска, ограда 93 из 105 — не готова.
        return (placed.total() - placed.done()) * LAYOUT_SLACK_DIVISOR <= placed.total();
    }

    // Сверка постройки плана с миром: шахта — по развёртке спуска, остальное — по блокам своего плана.
    private static Placed audit(ServerLevel level, BoardCase step, BlockCoord anchor, String wood) {
        if (step == BoardCase.BUILD_MINE) {
            return mineAudit(level, anchor);
        }
        return placedBlocks(level, TicketPlans.plan(step, 1, targetFor(step, anchor, wood)), wood);
    }

    private static int mineFloorsToPlace(ServerLevel level, BlockCoord anchor) {
        Placed floors = mineFloors(level, anchor);
        return floors.total() - floors.done();
    }

    private static Placed mineFloors(ServerLevel level, BlockCoord anchor) {
        Optional<MineLayout> layout = mineLayout(anchor);
        if (layout.isEmpty()) {
            return new Placed(0, 0);
        }
        int done = 0;
        for (BlockCoord cell : layout.get().floors()) {
            BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
            if (SchematicBlocks.isFloorClass(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                done++;
            }
        }
        return new Placed(done, layout.get().floors().size());
    }

    private static Optional<MineLayout> mineLayout(BlockCoord anchor) {
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(TicketPlans.MINE_DESCENT_SCHEME);
        return preset.map(found -> MineLayout.place(
                found.schematic(),
                found.entry(),
                TicketPlans.mineDescentOrigin(anchor),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE));
    }

    // Спуск по схеме: клетки прохода свободны (факел и лестница не мешают), пол — твёрдый блок класса пола.
    private static Placed mineAudit(ServerLevel level, BlockCoord anchor) {
        Optional<MineLayout> layout = mineLayout(anchor);
        if (layout.isEmpty()) {
            return new Placed(0, 0);
        }
        int done = 0;
        for (BlockCoord cell : layout.get().air()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                done++;
            }
        }
        return new Placed(done, layout.get().air().size()).plus(mineFloors(level, anchor));
    }

    private record Placed(int done, int total) {
        Placed plus(Placed other) {
            return new Placed(done + other.done, total + other.total);
        }
    }

    private static Placed placedBlocks(ServerLevel level, TaskNode plan, String wood) {
        return switch (plan) {
            case SequenceNode sequence ->
                sequence.steps().stream()
                        .map(step -> placedBlocks(level, step, wood))
                        .reduce(new Placed(0, 0), Placed::plus);
            case ScriptCall call
            when "place_block".equals(call.script())
                    && call.params().get("block") instanceof String block
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z ->
                new Placed(
                        placed(level.getBlockState(new BlockPos(x, y, z)), bare(WoodSpecies.swap(block, wood))) ? 1 : 0,
                        1);
            case ScriptCall call
            when "build_schematic".equals(call.script())
                    && !call.params().containsKey("rotation")
                    && call.params().get("file") instanceof String file
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z -> {
                int done = 0;
                int total = 0;
                for (Schematic.Entry entry : SchematicResources.load(file).blocks()) {
                    if (entry.optional() || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                        continue;
                    }
                    BlockState state =
                            level.getBlockState(new BlockPos(x + entry.dx(), y + entry.dy(), z + entry.dz()));
                    total++;
                    if (placed(state, bare(WoodSpecies.swap(entry.block(), wood)))) {
                        done++;
                    }
                }
                yield new Placed(done, total);
            }
            default -> new Placed(0, 0);
        };
    }

    // Факел у стены — тот же факел: place_block ставит настенный, если пол занят.
    private static boolean placed(BlockState state, String block) {
        String actual = bare(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        return actual.equals(block)
                || WoodSpecies.sameRole(actual, block)
                || ("torch".equals(block) && "wall_torch".equals(actual));
    }

    private static Set<BoardCase> journalSteps(BoardService service, boolean blocked) {
        Set<BoardCase> steps = EnumSet.noneOf(BoardCase.class);
        for (BoardCase step : FirstStepsPlan.chain()) {
            String id = FIRST_PREFIX + step.name().toLowerCase(Locale.ROOT);
            boolean match = blocked
                    ? service.board().statusOf(id).orElse(null) == BoardStatus.BLOCKED
                    : service.board().ledger().has(step, 1) && partsDone(service, step);
            if (match) {
                steps.add(step);
            }
        }
        return steps;
    }

    // Шаг кусками (ограда) сделан, когда сделаны все куски: отметку в журнале ставит первый сделанный кусок.
    // Мир до кусков (first:build_fence DONE, кусков на доске нет) — ограда целиком, она сделана.
    private static boolean partsDone(BoardService service, BoardCase step) {
        if (step == BoardCase.CLEAR_GROUNDS) {
            // Расчистка сделана, когда ни одна её часть не идёт: шахта, открытая по первой части, строилась в ящике
            // ещё идущей расчистки, и та срывала её стенки (сцена 56). Ровность после частей сверяет мир.
            for (int part = 2; part <= TicketPlans.CLEAR_PARTS; part++) {
                if (active(service, partId(step, part))) {
                    return false;
                }
            }
            return true;
        }
        if (step != BoardCase.BUILD_FENCE) {
            return true;
        }
        boolean anyPart = false;
        boolean allDone = true;
        for (int part = 1; part <= TicketPlans.FENCE_PARTS; part++) {
            Optional<BoardStatus> status = service.board().statusOf(partId(step, part));
            anyPart |= part > 1 && status.isPresent();
            allDone &= status.orElse(null) == BoardStatus.DONE;
        }
        return allDone || (!anyPart && service.board().statusOf(partId(step, 1)).orElse(null) == BoardStatus.DONE);
    }

    private static Set<String> toolStock(StockView stock) {
        Set<String> tools = new HashSet<>();
        for (Map.Entry<String, Integer> entry : stock.available().entrySet()) {
            if (entry.getValue() > 0) {
                tools.add(entry.getKey());
            }
        }
        return tools;
    }

    // Участок фиксируется один раз до расчистки (INC-55): центр — медиана домов духов, не дом первого в списке
    // (INC-56); высота — медиана ног 23×23. Пишется в лог и журнал доски. Якорь вдали от всех домов — старое
    // поселение: участок обмеряется заново.
    public static SettlementPlot plotOf(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        List<BlockCoord> homes = homes(spirits);
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<SettlementPlot> stored = store.plot();
        BlockCoord center = SettlementPlot.center(homes);
        if (stored.isPresent() && stored.orElseThrow().covers(center)) {
            return checkedWood(level, service, store, stored.orElseThrow(), now);
        }
        String wood = SettlementWood.survey(level, center, service.stock().available());
        SettlementPlot plot =
                SettlementPlot.survey(center, SettlementLayout.HALF, (x, z) -> feetAt(level, x, z, center.y()), wood);
        store.fix(plot);
        LOGGER.info(
                "[plot] высота {}: медиана по {} колонкам, мин {} макс {}, центр ({}, {}) по {} домам духов, порода {}",
                plot.anchor().y(),
                plot.columns(),
                plot.min(),
                plot.max(),
                plot.anchor().x(),
                plot.anchor().z(),
                homes.size(),
                wood);
        service.journal().recordEntry(plot.journalEntry(now, ConfigStamp.current()));
        return plot;
    }

    // Порода плана, нарушающая правило «запас < 16 — решает биом» (закреплена старой сборкой, INC-62), пересчитывается
    // с записью [plot]; правилу не противоречит — закреплённая порода не меняется.
    private static SettlementPlot checkedWood(
            ServerLevel level, BoardService service, SettlementPlotStore store, SettlementPlot plot, long now) {
        Optional<String> ruled =
                SettlementWood.biomeRules(service.stock().available(), SettlementWood.biomeAt(level, plot.anchor()));
        if (ruled.isEmpty() || ruled.get().equals(plot.wood())) {
            return plot;
        }
        SettlementPlot fixed = plot.withWood(ruled.get());
        store.fix(fixed);
        LOGGER.info(
                "[plot] порода плана {} → {}: запас брёвен меньше 16 — решает биом (INC-62)",
                plot.wood(),
                fixed.wood());
        service.journal().recordEntry(fixed.journalEntry(now, ConfigStamp.current()));
        return fixed;
    }

    private static List<BlockCoord> homes(List<SpiritEntity> spirits) {
        return spirits.stream().flatMap(spirit -> spirit.home().stream()).toList();
    }

    // Уровень, на котором стоят станции и сундуки поселения: медиана домов духов (для обзора вокруг дома).
    private static int homeY(List<SpiritEntity> spirits) {
        return SettlementPlot.center(homes(spirits)).y();
    }
}
