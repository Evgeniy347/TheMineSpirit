package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.incident.IncidentNote;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.scene.ScenePlan;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true, coordinator = true)
public final class FirstStageGameTests {
    static final String BATCH = "first_stage";
    private static final int STUCK_LIMIT = 600;
    private static final int STARTING_LOGS = 64;
    private static final int PIT_DEPTH = 3;
    private static final long NOON = 6000;
    private static final int SPIRITS = 3;
    private static final int REPORT_EVERY = 500;
    private static final int COPY_SHIFT = 46;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BoardCase> WORK = EnumSet.of(
            BoardCase.CLEAR_GROUNDS,
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_HOUSE,
            BoardCase.BUILD_MINE,
            BoardCase.LIGHTING,
            BoardCase.STOCK_CHESTS,
            BoardCase.REPAIR_BUILDING,
            BoardCase.DISMANTLE_SHELTER);

    private FirstStageGameTests() {}

    // [флак] этап 1 по плану поселения — площадки, двор, шахта, ограда этапа ~110 столбов, хижина с палисадником и
    // факелы тремя духами: тысячи тиков (замер 25.10 — 4500–5000 на трёх сидах), предел владельца ≤ 6000.
    @GameTest(template = "settlement_arena", batch = BATCH, timeoutTicks = 6000, skyAccess = true)
    public static void firstStage_GeneratedWorldThreeSpirits_PlotBuiltByPlan(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.holdDayTime(helper, NOON);
        long seed = GeneratedScene.seed();
        ScenePlan scene = GeneratedScene.place(helper, seed, BATCH);
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        board.board().restore(new BoardState(List.of(), Set.of()));
        BlockPos home = GeneratedScene.at(0, scene.height(0, 0) + 1, 0);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int index = 0; index < SPIRITS; index++) {
            spirits.add(spirit(helper, home.offset(index - 1, 0, 0), home, scene.wood()));
        }
        incidentRoundTrip(helper, spirits.get(0), seed);
        Watch watch = new Watch(helper, board, spirits, scene);
        helper.onEachTick(watch::tick);
        helper.succeedWhen(() -> {
            String head = "сид " + seed + " (повтор: scripts/test.sh gametest --seed " + seed + " " + BATCH + "): ";
            SettlementPlotStore store = SettlementPlotStore.get(level);
            Optional<SettlementPlot> plot = store.plot();
            helper.assertTrue(watch.stuck.isEmpty(), head + "дух застрял: " + watch.stuck + "; " + watch.state());
            helper.assertTrue(
                    plot.isPresent() && store.site().isPresent() && store.town().isPresent(),
                    head + "участок не обмерен или плана нет — доска не начала этап 1");
            StageSite site = store.site().orElseThrow();
            BlockCoord anchor = site.anchor();
            List<String> pits = pitsOutside(helper, scene, site);
            helper.assertTrue(
                    pits.isEmpty() && watch.fell.isEmpty(),
                    head + "за оградой ямы ниже участка " + pits + ", духи в ямах " + watch.fell);
            List<String> problems = StageOneAudit.problems(
                    level,
                    site,
                    store.town().orElseThrow().region(),
                    plot.orElseThrow().wood());
            helper.assertTrue(
                    problems.isEmpty(),
                    head + "этап 1 не по плану на якоре " + anchor + ": " + problems + "; " + watch.state());
            spirits.forEach(SpiritEntity::discard);
            GameTestFixtures.finish(helper);
        });
    }

    private static List<String> pitsOutside(GameTestHelper helper, ScenePlan scene, StageSite site) {
        BlockCoord anchor = site.anchor();
        List<String> pits = new ArrayList<>();
        for (int x = -scene.radius(); x <= scene.radius(); x++) {
            for (int z = -scene.radius(); z <= scene.radius(); z++) {
                int top = scene.height(x, z);
                BlockPos under = GeneratedScene.at(x, top - 1, z);
                BlockPos column = helper.absolutePos(under);
                boolean outside = !site.inside(column.getX(), column.getZ());
                if (outside
                        && helper.absolutePos(under).getY() < anchor.y()
                        && helper.getBlockState(GeneratedScene.at(x, top, z)).isAir()
                        && helper.getBlockState(under).isAir()) {
                    pits.add(x + "," + z);
                }
            }
        }
        return pits;
    }

    private static void incidentRoundTrip(GameTestHelper helper, SpiritEntity source, long seed) {
        ServerLevel level = helper.getLevel();
        IncidentNote note = IncidentSnapshots.note(source, "проверка снимка сцены");
        Path file = level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(IncidentNote.FOLDER)
                .resolve("scene-" + seed + ".nbt");
        IncidentSnapshots.write(level, note, source, file);
        BlockPos from =
                new BlockPos(note.origin().x(), note.origin().y(), note.origin().z());
        BlockPos corner = helper.relativePos(from).offset(COPY_SHIFT, 0, 0);
        StructureTemplate before = new StructureTemplate();
        int size = IncidentNote.SIZE;
        before.fillFromWorld(level, helper.absolutePos(corner), new Vec3i(size, size, size), false, null);
        IncidentSnapshots.Loaded loaded = IncidentScene.place(helper, file, corner);
        SpiritEntity copy = IncidentScene.spirit(helper, loaded, corner);
        List<String> differ = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(size - 1, size - 1, size - 1))) {
            BlockState original = level.getBlockState(from.offset(cell));
            if (!original.is(
                    level.getBlockState(helper.absolutePos(corner.offset(cell))).getBlock())) {
                differ.add(cell.toShortString() + " " + original.getBlock());
            }
        }
        int items = countItems(copy);
        copy.discard();
        BlockPos back = helper.absolutePos(corner);
        before.placeInWorld(level, back, back, new StructurePlaceSettings(), level.getRandom(), 2);
        helper.assertTrue(
                differ.isEmpty()
                        && items == countItems(source)
                        && loaded.spirit().equals(Optional.of(note.spirit())),
                "Снимок инцидента не повторяет место: расходится блоков " + differ.size() + " "
                        + differ.stream().limit(4).toList() + ", предметов у духа из снимка " + items + " вместо "
                        + countItems(source)
                        + ", дух " + loaded.spirit() + " — сцену из живого мира в тест не загрузить");
    }

    private static String woodHeld(SpiritEntity spirit) {
        int logs = 0;
        int planks = 0;
        int sticks = 0;
        int stone = 0;
        int coal = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            logs += id.endsWith("_log") ? stack.getCount() : 0;
            planks += id.endsWith("_planks") ? stack.getCount() : 0;
            sticks += "stick".equals(id) ? stack.getCount() : 0;
            stone += "cobblestone".equals(id) ? stack.getCount() : 0;
            coal += id.endsWith("coal") ? stack.getCount() : 0;
        }
        return "log" + logs + "/pl" + planks + "/st" + sticks + "/cb" + stone + "/c" + coal;
    }

    private static int countItems(SpiritEntity spirit) {
        int count = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            count += spirit.getInventory().getItem(slot).getCount();
        }
        return count;
    }

    private static SpiritEntity spirit(GameTestHelper helper, BlockPos at, BlockPos home, String wood) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), at);
        BlockPos absolute = helper.absolutePos(home);
        spirit.setHome(new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()));
        Item log = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(wood + "_log"));
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.STONE_AXE));
        spirit.getInventory().setItem(2, new ItemStack(Items.STONE_SHOVEL));
        spirit.getInventory().setItem(3, new ItemStack(Items.STONE_SWORD));
        spirit.getInventory().setItem(4, new ItemStack(log, STARTING_LOGS));
        spirit.getInventory().setItem(5, new ItemStack(Items.COAL, 16));
        return spirit;
    }

    private static final class Watch {
        private final GameTestHelper helper;
        private final BoardService board;
        private final List<SpiritEntity> spirits;
        private final Map<UUID, Integer> low = new HashMap<>();
        private final Map<UUID, Integer> idle = new HashMap<>();
        private final List<String> stuck = new ArrayList<>();
        private final List<String> fell = new ArrayList<>();
        private boolean pitDug;
        private final ScenePlan scene;
        private final Map<String, Integer> spent = new TreeMap<>();

        Watch(GameTestHelper helper, BoardService board, List<SpiritEntity> spirits, ScenePlan scene) {
            this.helper = helper;
            this.scene = scene;
            this.board = board;
            this.spirits = spirits;
        }

        void tick() {
            if (helper.getTick() % REPORT_EVERY == 0) {
                LOGGER.info("[scene] {}", state());
                SettlementPlotStore store = SettlementPlotStore.get(helper.getLevel());
                if (store.site().isPresent()
                        && store.town().isPresent()
                        && store.plot().isPresent()) {
                    LOGGER.info(
                            "[scene] аудит этапа 1 на тике {}: {}",
                            helper.getTick(),
                            StageOneAudit.problems(
                                    helper.getLevel(),
                                    store.site().orElseThrow(),
                                    store.town().orElseThrow().region(),
                                    store.plot().orElseThrow().wood()));
                }
            }
            Optional<StageSite> site =
                    SettlementPlotStore.get(helper.getLevel()).site();
            site.ifPresent(this::digPitAfterLeveling);
            boolean waiting = board.board().all().stream()
                    .filter(ticket -> WORK.contains(ticket.boardCase()))
                    .anyMatch(ticket -> board.board().statusOf(ticket.id()).orElse(null) == BoardStatus.PENDING);
            for (SpiritEntity spirit : spirits) {
                spent.merge(spirit.currentTaskScript().orElse("-"), 1, Integer::sum);
                boolean busy = spirit.taskStatus().orElse(null) == RunStatus.RUNNING
                        || !board.board().assignedTo(spirit.getUUID()).isEmpty();
                boolean below = site.map(s -> below(spirit, s)).orElse(false);
                site.ifPresent(s -> inPit(spirit, s));
                count(spirit, idle, !busy && waiting, "без задания при открытой работе доски");
                count(
                        spirit,
                        low,
                        below,
                        "ниже плана в " + spirit.blockPosition().toShortString());
            }
        }

        private void digPitAfterLeveling(StageSite site) {
            BlockCoord anchor = site.anchor();
            boolean leveling = board.board().all().stream()
                    .filter(ticket -> ticket.boardCase() == BoardCase.CLEAR_GROUNDS)
                    .anyMatch(
                            ticket -> board.board().statusOf(ticket.id()).orElse(BoardStatus.DONE) != BoardStatus.DONE);
            if (pitDug || leveling || !board.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
                return;
            }
            pitDug = true;
            ColumnMask.Cell spot = site.level().cells().stream()
                    .filter(cell -> !site.planCell(cell.x(), anchor.y(), cell.z()))
                    .findFirst()
                    .orElse(site.level().cells().get(0));
            BlockPos top = new BlockPos(spot.x(), anchor.y() - 1, spot.z());
            for (int depth = 0; depth < PIT_DEPTH; depth++) {
                helper.getLevel().setBlockAndUpdate(top.below(depth), Blocks.AIR.defaultBlockState());
            }
            LOGGER.info(
                    "[scene] яма {} на {} у двора после выравнивания: аудит ровности должен её засыпать",
                    top,
                    PIT_DEPTH);
        }

        private void inPit(SpiritEntity spirit, StageSite site) {
            BlockCoord anchor = site.anchor();
            BlockPos at = helper.relativePos(spirit.blockPosition());
            int x = at.getX() - GeneratedScene.CENTER;
            int z = at.getZ() - GeneratedScene.CENTER;
            int edge = Math.max(Math.abs(x), Math.abs(z));
            BlockPos feet = spirit.blockPosition();
            if (!site.inside(feet.getX(), feet.getZ())
                    && edge <= scene.radius()
                    && at.getY() - GeneratedScene.SURFACE < scene.height(x, z)
                    && spirit.blockPosition().getY() < anchor.y()
                    && fell.size() < SPIRITS) {
                fell.add(spirit.displayName() + " в " + spirit.blockPosition().toShortString() + " на тике "
                        + helper.getTick());
            }
        }

        private static boolean below(SpiritEntity spirit, StageSite site) {
            BlockPos at = spirit.blockPosition();
            return site.levelColumn(at.getX(), at.getZ())
                    && at.getY() < site.anchor().y() - 1;
        }

        private void count(SpiritEntity spirit, Map<UUID, Integer> ticks, boolean now, String what) {
            int run = now ? ticks.getOrDefault(spirit.getUUID(), 0) + 1 : 0;
            ticks.put(spirit.getUUID(), run);
            if (run == STUCK_LIMIT + 1) {
                stuck.add(spirit.displayName() + " " + what + " с тика " + (helper.getTick() - STUCK_LIMIT)
                        + " (задача " + spirit.currentTaskScript().orElse("-") + ")");
            }
        }

        private Map<String, Integer> chestStock() {
            Map<String, Integer> stock = new TreeMap<>();
            for (BlockCoord chest :
                    SettlementChestStore.get(helper.getLevel()).chests().all()) {
                if (helper.getLevel().getBlockEntity(new BlockPos(chest.x(), chest.y(), chest.z()))
                        instanceof Container container) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (!stack.isEmpty()) {
                            stock.merge(
                                    BuiltInRegistries.ITEM
                                            .getKey(stack.getItem())
                                            .getPath(),
                                    stack.getCount(),
                                    Integer::sum);
                        }
                    }
                }
            }
            return stock;
        }

        String state() {
            List<String> spiritsNow = spirits.stream()
                    .map(spirit ->
                            spirit.displayName() + "@" + spirit.blockPosition().toShortString() + "="
                                    + spirit.currentTaskScript().orElse("-") + " " + woodHeld(spirit))
                    .toList();
            List<String> open = board.board().all().stream()
                    .filter(ticket -> board.board()
                            .statusOf(ticket.id())
                            .map(status -> status != BoardStatus.DONE)
                            .orElse(false))
                    .map(ticket -> ticket.id() + "="
                            + board.board().statusOf(ticket.id()).orElse(null))
                    .toList();
            return "тик " + helper.getTick() + ", духи " + spiritsNow + ", сундуки " + chestStock() + ", открыто "
                    + open + ", тики по сценариям " + spent;
        }
    }
}
