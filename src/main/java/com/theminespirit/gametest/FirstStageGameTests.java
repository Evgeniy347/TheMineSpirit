package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.incident.IncidentNote;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.scene.ScenePlan;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Сквозная сцена этапа 1 (не сокращать): три духа на неплоском мире по сиду ({@link GeneratedScene}) с
 * доской и координатором по обычным механикам доводят участок до плана — ровно, ограда цела и непроходима, хижина,
 * спуск в шахту и факелы по раскладке ({@link StageOneAudit}); ни один дух не застревает ниже плана и не стоит без
 * задания дольше {@link #STUCK_LIMIT} тиков. Упала — повтор на той же сцене: {@code scripts/test.sh gametest --seed N
 * first_stage}, сид — в строке {@code [scene]} лога и в сообщении.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true, coordinator = true)
public final class FirstStageGameTests {
    static final String BATCH = "first_stage";
    private static final int STUCK_LIMIT = 600;
    private static final long NOON = 6000;
    private static final int SPIRITS = 3;
    private static final int REPORT_EVERY = 500;
    private static final int COPY_SHIFT = 46;
    private static final Logger LOGGER = LogUtils.getLogger();

    private FirstStageGameTests() {}

    // [флак] этап 1 целиком — выравнивание участка 27×27, шахта, ограда 23×23 и хижина тремя духами: тысячи тиков по
    // решению владельца (≤ 6000); простой и застревание меряет STUCK_LIMIT, предел — потолок всего этапа.
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
        Watch watch = new Watch(helper, board, spirits);
        helper.onEachTick(watch::tick);
        helper.succeedWhen(() -> {
            String head = "сид " + seed + " (повтор: scripts/test.sh gametest --seed " + seed + " " + BATCH + "): ";
            Optional<SettlementPlot> plot = SettlementPlotStore.get(level).plot();
            helper.assertTrue(watch.stuck.isEmpty(), head + "дух застрял: " + watch.stuck + "; " + watch.state());
            helper.assertTrue(plot.isPresent(), head + "участок не обмерен — доска не начала этап 1");
            BlockCoord anchor = plot.orElseThrow().anchor();
            List<String> problems =
                    StageOneAudit.problems(level, anchor, plot.orElseThrow().wood());
            helper.assertTrue(
                    problems.isEmpty(),
                    head + "этап 1 не по плану на якоре " + anchor + ": " + problems + "; " + watch.state());
            spirits.forEach(SpiritEntity::discard);
            GameTestFixtures.finish(helper);
        });
    }

    // Снимок инцидента грузится сценой: окрестность ±12 первого духа в файл, файл — копией в угол шаблона вне
    // сцены, блок в блок, и дух из снимка — с тем же инвентарём; потом угол возвращается как был.
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
            if (!original.equals(level.getBlockState(helper.absolutePos(corner.offset(cell))))) {
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
        spirit.getInventory().setItem(4, new ItemStack(log, 64));
        spirit.getInventory().setItem(5, new ItemStack(Items.COAL, 16));
        return spirit;
    }

    // Застревание по миру: дух ниже уровня участка вне спуска в шахту или без задания дольше STUCK_LIMIT подряд.
    private static final class Watch {
        private final GameTestHelper helper;
        private final BoardService board;
        private final List<SpiritEntity> spirits;
        private final Map<UUID, Integer> low = new HashMap<>();
        private final Map<UUID, Integer> idle = new HashMap<>();
        private final List<String> stuck = new ArrayList<>();
        // Тики духов по сценариям — куда ушло время этапа (предел 6000 тиков).
        private final Map<String, Integer> spent = new TreeMap<>();

        Watch(GameTestHelper helper, BoardService board, List<SpiritEntity> spirits) {
            this.helper = helper;
            this.board = board;
            this.spirits = spirits;
        }

        void tick() {
            if (helper.getTick() % REPORT_EVERY == 0) {
                LOGGER.info("[scene] {}", state());
            }
            Optional<SettlementPlot> plot =
                    SettlementPlotStore.get(helper.getLevel()).plot();
            for (SpiritEntity spirit : spirits) {
                spent.merge(spirit.currentTaskScript().orElse("-"), 1, Integer::sum);
                boolean busy = spirit.taskStatus().orElse(null) == RunStatus.RUNNING
                        || !board.board().assignedTo(spirit.getUUID()).isEmpty();
                boolean below = plot.map(p -> below(spirit, p.anchor())).orElse(false);
                count(spirit, idle, !busy, "без задания");
                count(
                        spirit,
                        low,
                        below,
                        "ниже плана в " + spirit.blockPosition().toShortString());
            }
        }

        private static boolean below(SpiritEntity spirit, BlockCoord anchor) {
            BlockPos at = spirit.blockPosition();
            boolean onPlot = Math.abs(at.getX() - anchor.x()) <= SettlementLayout.LEVEL_HALF
                    && Math.abs(at.getZ() - anchor.z()) <= SettlementLayout.LEVEL_HALF;
            return onPlot && at.getY() < anchor.y() - 1 && !SettlementLayout.mineColumn(anchor, at.getX(), at.getZ());
        }

        private void count(SpiritEntity spirit, Map<UUID, Integer> ticks, boolean now, String what) {
            int run = now ? ticks.getOrDefault(spirit.getUUID(), 0) + 1 : 0;
            ticks.put(spirit.getUUID(), run);
            if (run == STUCK_LIMIT + 1) {
                stuck.add(spirit.displayName() + " " + what + " с тика " + (helper.getTick() - STUCK_LIMIT)
                        + " (задача " + spirit.currentTaskScript().orElse("-") + ")");
            }
        }

        String state() {
            List<String> spiritsNow = spirits.stream()
                    .map(spirit ->
                            spirit.displayName() + "@" + spirit.blockPosition().toShortString() + "="
                                    + spirit.currentTaskScript().orElse("-"))
                    .toList();
            List<String> open = board.board().all().stream()
                    .filter(ticket -> board.board()
                            .statusOf(ticket.id())
                            .map(status -> status != BoardStatus.DONE)
                            .orElse(false))
                    .map(ticket -> ticket.id() + "="
                            + board.board().statusOf(ticket.id()).orElse(null))
                    .toList();
            return "тик " + helper.getTick() + ", духи " + spiritsNow + ", открыто " + open + ", тики по сценариям "
                    + spent;
        }
    }
}
