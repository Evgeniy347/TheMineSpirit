package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.BuildStats;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Каждая постройка демо-каталога в четырёх поворотах: BuildAudit обязан не найти ни одного расхождения — ни по
 * клеткам схемы, ни по лишним блокам, ни по числу постановок, — а дух обязан строить с места: число построений пути
 * ограничено (решение владельца 18.10: «не бегает — ставит всё, до чего дотягивается»). Четыре поворота одной
 * постройки — одна пачка: четыре арены строятся параллельно.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemoAuditGameTests {
    static final int SUPPLY_RING = 2;
    static final int SUPPLY_SIDE_OFFSET = 3;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SETTLE_TICKS = 5;
    private static final int MIN_CLEAR_HEIGHT = 8;
    private static final int FLOOR_SIZE = 11;
    private static final int FLOOR_MARGIN = 4;
    private static final int DOUBLE_CHEST_ROWS = 6;
    // Заходов за материалами, как в демо (DemoSession.MAX_ROUNDS): больше — значит, стройка не продвигается.
    private static final int SPIRIT_SLOTS = 36;
    // Подходы аудита (решение владельца 20.10, ночь): стройка идёт по плану BuildPlanner, и подходов (стоянки + заходы)
    // не больше, чем в плане, плюс запас на движение — срывы подхода и запасные стоянки, когда со стоянки плана в
    // мире не дотянуться (тело духа, неровный край). Запас — замер по каталогу, см. MOVEMENT_MARGIN_*.
    private static final int MOVEMENT_MARGIN_MIN = 3;
    private static final int MOVEMENT_MARGIN_PERCENT = 10;

    private DemoAuditGameTests() {}

    // Высокая постройка (дом семьи 1 — 10 слоёв с крышей) оставила бы конёк соседнему тесту: чистим на её высоту.
    static int clearHeight(Schematic schematic) {
        return Math.max(MIN_CLEAR_HEIGHT, schematic.sizeY() + 2);
    }

    // Пол — с запасом в 4 клетки сверх большей стороны постройки: духу нужно место появиться снаружи.
    static int floorSize(Schematic schematic) {
        return Math.max(FLOOR_SIZE, Math.max(schematic.sizeX(), schematic.sizeZ()) + FLOOR_MARGIN);
    }

    static void clearArea(GameTestHelper helper, int size, int height) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < size; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    static void floor(GameTestHelper helper, int size, int height) {
        clearArea(helper, size, height);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    // Материалы всей постройки влезают в инвентарь — выдаются сразу; не влезают (дом семьи 3 — ~56 стопок на
    // 36 слотов) — лежат в сундуках, и дух ходит за ними заходами.
    private static boolean fitsAtOnce(Schematic schematic) {
        int stacks = 0;
        for (Map.Entry<String, Integer> material :
                PresetLibrary.countMaterials(schematic).entrySet()) {
            if (BlockItems.hasBlockItem(material.getKey())) {
                int max = BlockItems.itemFor(material.getKey()).getDefaultMaxStackSize();
                stacks += (material.getValue() + max - 1) / max;
            }
        }
        return stacks <= SPIRIT_SLOTS;
    }

    private static void give(GameTestHelper helper, SpiritEntity spirit, Schematic schematic) {
        int slot = 0;
        for (Map.Entry<String, Integer> material :
                PresetLibrary.countMaterials(schematic).entrySet()) {
            if (!BlockItems.hasBlockItem(material.getKey())) {
                continue;
            }
            Item item = BlockItems.itemFor(material.getKey());
            int left = material.getValue();
            while (left > 0) {
                int take = Math.min(left, item.getDefaultMaxStackSize());
                if (slot >= spirit.getInventory().getContainerSize()) {
                    helper.assertTrue(false, "материалы постройки не влезли в инвентарь духа: слоты кончились");
                }
                spirit.getInventory().setItem(slot++, new ItemStack(item, take));
                left -= take;
            }
        }
    }

    // Решение владельца 20.10: материалы, не влезающие в инвентарь, — в двойных сундуках у края пола; дух сам
    // ходит за ними порциями (BuildSupplyTrip), как в демо и в обычной стройке.
    record SupplyPoint(BlockPos base, Direction partner) {}

    // Сундуки — у главного входа, как в демо: за ступенью, в стороне от линии подхода.
    static SupplyPoint supplyPoint(String scheme, Schematic schematic, BuildRotation rotation, int ring) {
        Preset preset = DataTables.INSTANCE.presets().current().byId(scheme).orElseThrow();
        DemoPlacement.Door door = DemoPlacement.Door.of(preset);
        BuildRotation.Cell cell = rotation.rotate(door.x(), door.z(), schematic.sizeX(), schematic.sizeZ());
        BuildRotation.DirectionVector out =
                BuildRotation.rotateDirection(door.normalDx(), door.normalDz(), rotation.quarterTurns());
        Direction partner = Direction.fromDelta(-out.dz(), 0, out.dx());
        int frontX = ring + cell.x() + out.dx() * (door.porch() + ring);
        int frontZ = ring + cell.z() + out.dz() * (door.porch() + ring);
        return new SupplyPoint(
                new BlockPos(
                        frontX + partner.getStepX() * SUPPLY_SIDE_OFFSET,
                        1,
                        frontZ + partner.getStepZ() * SUPPLY_SIDE_OFFSET),
                partner);
    }

    private static List<BlockPos> supplyChests(GameTestHelper helper, Schematic schematic, SupplyPoint point) {
        BlockPos base = point.base();
        Direction partner = point.partner();
        BlockState leftHalf = Blocks.CHEST
                .defaultBlockState()
                .setValue(ChestBlock.FACING, partner.getCounterClockWise())
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState rightHalf = leftHalf.setValue(ChestBlock.TYPE, ChestType.RIGHT);
        List<BlockPos> halves = new ArrayList<>();
        Container chest = null;
        int slot = 0;
        for (Map.Entry<String, Integer> material :
                PresetLibrary.countMaterials(schematic).entrySet()) {
            if (!BlockItems.hasBlockItem(material.getKey())) {
                continue;
            }
            Item item = BlockItems.itemFor(material.getKey());
            int remaining = material.getValue();
            while (remaining > 0) {
                if (chest == null || slot >= chest.getContainerSize()) {
                    int pair = halves.size() / 2;
                    BlockPos main = base.above(pair);
                    BlockPos half = halves.size() % 2 == 0 ? main : main.relative(partner);
                    if (halves.size() % 2 == 0) {
                        helper.setBlock(main.relative(partner), rightHalf);
                        helper.setBlock(main, leftHalf);
                    }
                    halves.add(half);
                    chest = (Container) helper.getBlockEntity(half);
                    slot = 0;
                }
                int take = Math.min(remaining, item.getDefaultMaxStackSize());
                chest.setItem(slot++, new ItemStack(item, take));
                remaining -= take;
            }
        }
        return halves;
    }

    private static void emptyChests(GameTestHelper helper, List<BlockPos> halves) {
        for (BlockPos half : halves) {
            if (helper.getBlockEntity(half) instanceof Container chest) {
                chest.clearContent();
            }
        }
    }

    private static Map<String, Object> data(GameTestHelper helper, SpiritEntity spirit) {
        ScriptResult result = spirit.taskResult().orElse(null);
        helper.assertTrue(
                result instanceof ScriptResult.Data,
                "Успешная стройка обязана нести Data-итог: " + (result == null ? "пусто" : result));
        return ((ScriptResult.Data) result).fields();
    }

    private static void audit(GameTestHelper helper, String building, int level, BuildRotation rotation) {
        audit(helper, building, level, rotation, (h, schematic, origin, turned) -> {});
    }

    @FunctionalInterface
    private interface AfterBuild {
        void check(GameTestHelper helper, Schematic schematic, BlockCoord origin, BuildRotation rotation);
    }

    private static void audit(
            GameTestHelper helper, String building, int level, BuildRotation rotation, AfterBuild after) {
        audit(helper, building, level, rotation, after, SPIRIT_SLOTS);
    }

    // freeSlots < 36 — инвентарь духа занят чужим, свободно freeSlots: стройка обязана идти порциями из сундуков.
    private static void audit(
            GameTestHelper helper,
            String building,
            int level,
            BuildRotation rotation,
            AfterBuild after,
            int freeSlots) {
        String scheme = DemoBuildings.require(building).level(level).scheme();
        Schematic schematic = SchematicResources.load(scheme);
        int clearHeight = clearHeight(schematic);
        GameTestFixtures.reset(helper, clearHeight);
        // Заходы за материалами: дом отступает от края пола на кольцо с обеих сторон, чтобы дух обходил его кругом к
        // сундукам и к дверям и не сходил с пола за край.
        boolean atOnce = freeSlots >= SPIRIT_SLOTS && fitsAtOnce(schematic);
        int ring = atOnce ? 0 : SUPPLY_RING;
        int floorSize = floorSize(schematic) + 2 * ring;
        floor(helper, floorSize, clearHeight);
        BlockPos anchor = helper.absolutePos(new BlockPos(ring, 1, ring));
        BlockCoord origin = new BlockCoord(anchor.getX(), anchor.getY(), anchor.getZ());
        SupplyPoint supply = atOnce ? null : supplyPoint(scheme, schematic, rotation, ring);
        List<BlockPos> chests = supply == null ? List.of() : supplyChests(helper, schematic, supply);
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin, rotation, 3);
        // При заходах дух появляется у сундуков перед входом: путь от тыла вокруг большого дома длиннее поиска пути.
        BlockPos spawn = supply == null
                ? spawnOutside(schematic, rotation, floorSize)
                : supply.base().relative(supply.partner().getOpposite(), SUPPLY_SIDE_OFFSET - 1);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), spawn);
        spirit.setDemo(true);
        if (chests.isEmpty()) {
            give(helper, spirit, schematic);
        }
        for (int slot = 0; slot < SPIRIT_SLOTS - freeSlots; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.STICK, Items.STICK.getDefaultMaxStackSize()));
        }
        ScriptCall call = new ScriptCall(
                "build_schematic",
                Map.of(
                        "file",
                        scheme,
                        "x",
                        anchor.getX(),
                        "y",
                        anchor.getY(),
                        "z",
                        anchor.getZ(),
                        "rotation",
                        rotation.id()));
        SpiritRun run = SpiritRun.launch(helper, spirit, call, SETTLE_TICKS);
        AtomicBoolean measured = new AtomicBoolean();
        AtomicBoolean preplaced = new AtomicBoolean();

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Постройка '" + scheme + "' (" + rotation + ") обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", дух на "
                            + spirit.blockPosition()
                                    .subtract(helper.absolutePos(BlockPos.ZERO))
                                    .toShortString()
                            + ", построений пути " + spirit.pathBuilds() + ", тиков духа " + spirit.tickCount
                            + ", удалён " + spirit.getRemovalReason() + ", задача "
                            + GameTestFixtures.taskSnapshot(spirit).orElse("нет"));
            Map<String, Object> data = data(helper, spirit);
            int skipped = ((Number) data.get("skipped")).intValue();
            // Блоки без предмета (вода колодца) — после стройки, как в демо (DemoSession): поставленная до бортика
            // вода растекалась по полу и стекала за край арены лишним блоком (plaza_1 cw180, 20.10).
            if (!preplaced.getAndSet(true)) {
                placeWithoutItem(helper, schematic, origin, rotation);
            }
            BuildAudit.Result audit = BuildAudit.audit(helper.getLevel(), schematic, origin, rotation, before, skipped);
            helper.assertTrue(
                    audit.ok(),
                    "Постройка '" + scheme + "' (" + rotation
                            + ") обязана совпасть со схемой без лишних блоков, расхождения: " + audit.problems());
            assertFollowedPlan(helper, scheme, rotation, spirit, measured);
            after.check(helper, schematic, origin, rotation);
            emptyChests(helper, chests);
            clearArea(helper, floorSize, clearHeight);
            GameTestFixtures.finish(helper, clearHeight);
        });
    }

    // Стройка шла по плану: подходов (стоянки + заходы) не больше плана с запасом на движение.
    private static void assertFollowedPlan(
            GameTestHelper helper, String scheme, BuildRotation rotation, SpiritEntity spirit, AtomicBoolean measured) {
        BuildStats stats = spirit.buildStats().orElse(null);
        helper.assertTrue(stats != null, "Стройка '" + scheme + "' не оставила итога плана: аудит подходов не на чем");
        if (!measured.getAndSet(true)) {
            // Замер для запаса на движение: число в момент успеха, до повторов succeedWhen.
            LOGGER.info(
                    "Аудит '{}' ({}): подходов {} (план {}), стоянок {} (план {}), заходов {} (план {}), клеток плана "
                            + "на обычную стройку {}, построений пути {}",
                    scheme,
                    rotation,
                    stats.approaches(),
                    stats.planApproaches(),
                    stats.stands(),
                    stats.planStands(),
                    stats.trips(),
                    stats.planTrips(),
                    stats.deviations(),
                    spirit.pathBuilds());
        }
        long allowed = allowedApproaches(stats.planApproaches());
        helper.assertTrue(
                stats.planStands() > 0 && stats.approaches() <= allowed,
                "Постройка '" + scheme + "' (" + rotation + ") обязана идти по плану стройки: подходов не больше "
                        + allowed + " (план " + stats.planApproaches() + " + запас на движение), фактически "
                        + stats.approaches() + " (стоянок " + stats.stands() + ", заходов " + stats.trips()
                        + ", клеток плана на обычную стройку " + stats.deviations() + ")");
    }

    private static long allowedApproaches(long planApproaches) {
        return planApproaches + Math.max(MOVEMENT_MARGIN_MIN, (planApproaches * MOVEMENT_MARGIN_PERCENT + 99) / 100);
    }

    private static void assertDoubleChests(
            GameTestHelper helper, Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int chests = 0;
        for (Schematic.PlacedEntry entry : schematic.entries(origin, rotation)) {
            if (!"minecraft:chest".equals(entry.block()) && !"chest".equals(entry.block())) {
                continue;
            }
            chests++;
            BlockPos pos =
                    new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            BlockState state = helper.getLevel().getBlockState(pos);
            helper.assertTrue(
                    state.is(Blocks.CHEST) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE,
                    "Сундук склада в " + pos.toShortString() + " (" + rotation + ") обязан быть половиной двойного: "
                            + "пары в схеме заданы type=left/right, одинарный сундук рядом с соседом — брак; "
                            + "фактически " + state);
            MenuProvider provider = state.getMenuProvider(helper.getLevel(), pos);
            AbstractContainerMenu menu =
                    provider == null ? null : provider.createMenu(1, player.getInventory(), player);
            helper.assertTrue(
                    menu instanceof ChestMenu chest && chest.getRowCount() == DOUBLE_CHEST_ROWS,
                    "Сундук склада в " + pos.toShortString() + " обязан открываться двойным меню (6 рядов): "
                            + "фактически " + menu);
        }
        helper.assertTrue(chests > 0, "В складе не найдено ни одного сундука: проверять пары не на чем");
    }

    private static BlockPos spawnOutside(Schematic schematic, BuildRotation rotation, int floorSize) {
        int rotatedX = rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ());
        int rotatedZ = rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ());
        if (rotatedZ < floorSize) {
            return new BlockPos(floorSize / 2, 1, floorSize - 1);
        }
        if (rotatedX < floorSize) {
            return new BlockPos(floorSize - 1, 1, floorSize / 2);
        }
        throw new IllegalStateException("Постройка " + rotatedX + "×" + rotatedZ + " занимает весь пол арены "
                + floorSize + "×" + floorSize + ": духу негде появиться снаружи");
    }

    private static void placeWithoutItem(
            GameTestHelper helper, Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        Rotation mc = switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        };
        for (Schematic.PlacedEntry entry : schematic.entries(origin, rotation)) {
            if (BlockItems.hasBlockItem(entry.block())) {
                continue;
            }
            BlockPos pos =
                    new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            BlockState state = BlockStates.stateFor(entry.block(), entry.state());
            helper.getLevel()
                    .setBlock(
                            pos, rotation == BuildRotation.NONE ? state : state.rotate(helper.getLevel(), pos, mc), 3);
        }
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 1, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 1, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 1, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 1, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_stonehouse", timeoutTicks = 4000)
    public static void demo_StoneHouseNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "stone_house", 0, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_stonehouse", timeoutTicks = 4000)
    public static void demo_StoneHouseClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "stone_house", 0, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_stonehouse", timeoutTicks = 4000)
    public static void demo_StoneHouseClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "stone_house", 0, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_stonehouse", timeoutTicks = 4000)
    public static void demo_StoneHouseCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "stone_house", 0, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagehut", timeoutTicks = 4000)
    public static void demo_StorageHutNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage_hut", 0, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagehut", timeoutTicks = 4000)
    public static void demo_StorageHutClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage_hut", 0, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagehut", timeoutTicks = 4000)
    public static void demo_StorageHutClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage_hut", 0, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagehut", timeoutTicks = 4000)
    public static void demo_StorageHutCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage_hut", 0, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagelevel1", timeoutTicks = 4000)
    public static void demo_StorageLevel1None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage", 1, BuildRotation.NONE, DemoAuditGameTests::assertDoubleChests);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagelevel1", timeoutTicks = 4000)
    public static void demo_StorageLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage", 1, BuildRotation.CLOCKWISE_90, DemoAuditGameTests::assertDoubleChests);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagelevel1", timeoutTicks = 4000)
    public static void demo_StorageLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage", 1, BuildRotation.CLOCKWISE_180, DemoAuditGameTests::assertDoubleChests);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagelevel1", timeoutTicks = 4000)
    public static void demo_StorageLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "storage", 1, BuildRotation.COUNTERCLOCKWISE_90, DemoAuditGameTests::assertDoubleChests);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_well", timeoutTicks = 4000)
    public static void demo_WellNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 0, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_well", timeoutTicks = 4000)
    public static void demo_WellClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 0, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_well", timeoutTicks = 4000)
    public static void demo_WellClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 0, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_well", timeoutTicks = 4000)
    public static void demo_WellCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 0, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_torchpost", timeoutTicks = 4000)
    public static void demo_TorchPostNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 1, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_torchpost", timeoutTicks = 4000)
    public static void demo_TorchPostClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 1, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_torchpost", timeoutTicks = 4000)
    public static void demo_TorchPostClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 1, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_torchpost", timeoutTicks = 4000)
    public static void demo_TorchPostCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 1, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_palisadesection", timeoutTicks = 4000)
    public static void demo_PalisadeSectionNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "palisade_section", 0, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_palisadesection", timeoutTicks = 4000)
    public static void demo_PalisadeSectionClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "palisade_section", 0, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_palisadesection", timeoutTicks = 4000)
    public static void demo_PalisadeSectionClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "palisade_section", 0, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_palisadesection", timeoutTicks = 4000)
    public static void demo_PalisadeSectionCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "palisade_section", 0, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel1", timeoutTicks = 4000)
    public static void demo_PenLevel1None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 1, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel1", timeoutTicks = 4000)
    public static void demo_PenLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 1, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel1", timeoutTicks = 4000)
    public static void demo_PenLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 1, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel1", timeoutTicks = 4000)
    public static void demo_PenLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 1, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel1", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel1None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 1, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel1", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 1, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel1", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 1, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel1", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 1, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel2", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 2, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel2", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 2, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel2", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 2, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel2", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 2, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_mineentrance", timeoutTicks = 4000)
    public static void demo_MineEntranceNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "mine_entrance", 1, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_mineentrance", timeoutTicks = 4000)
    public static void demo_MineEntranceClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "mine_entrance", 1, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_mineentrance", timeoutTicks = 4000)
    public static void demo_MineEntranceClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "mine_entrance", 1, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_mineentrance", timeoutTicks = 4000)
    public static void demo_MineEntranceCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "mine_entrance", 1, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel2", timeoutTicks = 4000)
    public static void demo_PenLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 2, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel2", timeoutTicks = 4000)
    public static void demo_PenLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 2, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel2", timeoutTicks = 4000)
    public static void demo_PenLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 2, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel2", timeoutTicks = 4000)
    public static void demo_PenLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 2, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel3", timeoutTicks = 4000)
    public static void demo_PenLevel3None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 3, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel3", timeoutTicks = 4000)
    public static void demo_PenLevel3Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 3, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel3", timeoutTicks = 4000)
    public static void demo_PenLevel3Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 3, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_penlevel3", timeoutTicks = 4000)
    public static void demo_PenLevel3Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "pen", 3, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] дом семьи уровня 2 — сотни блоков и несколько этажей: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel2", timeoutTicks = 8000)
    public static void demo_FamilyHouseLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 2, BuildRotation.NONE);
    }

    // [флак] дом семьи уровня 2 — сотни блоков и несколько этажей: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel2", timeoutTicks = 8000)
    public static void demo_FamilyHouseLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 2, BuildRotation.CLOCKWISE_90);
    }

    // [флак] дом семьи уровня 2 — сотни блоков и несколько этажей: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel2", timeoutTicks = 8000)
    public static void demo_FamilyHouseLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 2, BuildRotation.CLOCKWISE_180);
    }

    // [флак] дом семьи уровня 2 — сотни блоков и несколько этажей: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel2", timeoutTicks = 8000)
    public static void demo_FamilyHouseLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 2, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.NONE);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.CLOCKWISE_90);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.CLOCKWISE_180);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // Решение владельца 20.10: в инвентаре два свободных слота — дух берёт порцию, ставит всё, что ею можно, и идёт
    // за следующей; склад-хижина из 11 материалов строится заходами до конца.
    // [флак] семь заходов к сундуку и обратно: запас тиков на подходы, тест меряет результат и число подходов.
    @GameTest(template = "big_arena", batch = "demo_audit_trips", timeoutTicks = 8000)
    public static void demo_StorageHutTwoFreeSlots_BuildsInPortions(GameTestHelper helper) {
        audit(helper, "storage_hut", 0, BuildRotation.NONE, (h, s, o, r) -> {}, 2);
    }

    // Решение владельца 20.10: инвентарь забит посторонним (мусор, дроп мобов), свободных слотов нет — демо-дом всё
    // равно достраивается: лишнее освобождает слоты под порцию. Без правила заход не берёт порцию и стройка падает.
    // [флак] заходы к сундуку и обратно: запас тиков на подходы, тест меряет результат и число подходов.
    @GameTest(template = "big_arena", batch = "demo_audit_clutter", timeoutTicks = 8000)
    public static void demo_StorageHutInventoryFullOfJunk_StillCompletes(GameTestHelper helper) {
        audit(helper, "storage_hut", 0, BuildRotation.NONE, (h, s, o, r) -> {}, 0);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 60000)
    public static void demo_FamilyHouseLevel4None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.NONE);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 60000)
    public static void demo_FamilyHouseLevel4Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.CLOCKWISE_90);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 60000)
    public static void demo_FamilyHouseLevel4Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.CLOCKWISE_180);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 60000)
    public static void demo_FamilyHouseLevel4Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.NONE);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.CLOCKWISE_90);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.CLOCKWISE_180);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат. Краснела (21.10) четырьмя
    // клетками
    // гравия под стоянками плана: запасные стоянки отмечали их недоступными, и после плана обычная стройка их не
    // видела.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.NONE);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.CLOCKWISE_90);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.CLOCKWISE_180);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.NONE);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.CLOCKWISE_90);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.CLOCKWISE_180);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.NONE);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.CLOCKWISE_90);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.CLOCKWISE_180);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.NONE);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.CLOCKWISE_90);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.CLOCKWISE_180);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.NONE);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.CLOCKWISE_90);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.CLOCKWISE_180);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.NONE);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.CLOCKWISE_90);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.CLOCKWISE_180);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] площадь 3 — мощение 25×25 с кольцом, фонтан, скамьи и клумбы: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel3", timeoutTicks = 10000)
    public static void demo_PlazaLevel3None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 3, BuildRotation.NONE);
    }

    // [флак] площадь 3 — мощение 25×25 с кольцом, фонтан, скамьи и клумбы: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel3", timeoutTicks = 10000)
    public static void demo_PlazaLevel3Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 3, BuildRotation.CLOCKWISE_90);
    }

    // [флак] площадь 3 — мощение 25×25 с кольцом, фонтан, скамьи и клумбы: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel3", timeoutTicks = 10000)
    public static void demo_PlazaLevel3Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 3, BuildRotation.CLOCKWISE_180);
    }

    // [флак] площадь 3 — мощение 25×25 с кольцом, фонтан, скамьи и клумбы: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel3", timeoutTicks = 10000)
    public static void demo_PlazaLevel3Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 3, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] дом игрока 3 — два этажа 20×17, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel3", timeoutTicks = 40000)
    public static void demo_PlayerHouseLevel3None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 3, BuildRotation.NONE);
    }

    // [флак] дом игрока 3 — два этажа 20×17, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel3", timeoutTicks = 40000)
    public static void demo_PlayerHouseLevel3Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 3, BuildRotation.CLOCKWISE_90);
    }

    // [флак] дом игрока 3 — два этажа 20×17, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel3", timeoutTicks = 40000)
    public static void demo_PlayerHouseLevel3Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 3, BuildRotation.CLOCKWISE_180);
    }

    // [флак] дом игрока 3 — два этажа 20×17, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel3", timeoutTicks = 40000)
    public static void demo_PlayerHouseLevel3Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 3, BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] особняк 22×20 с залом на два этажа, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel4", timeoutTicks = 60000)
    public static void demo_PlayerHouseLevel4None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 4, BuildRotation.NONE);
    }

    // [флак] особняк 22×20 с залом на два этажа, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel4", timeoutTicks = 60000)
    public static void demo_PlayerHouseLevel4Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 4, BuildRotation.CLOCKWISE_90);
    }

    // [флак] особняк 22×20 с залом на два этажа, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel4", timeoutTicks = 60000)
    public static void demo_PlayerHouseLevel4Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 4, BuildRotation.CLOCKWISE_180);
    }

    // [флак] особняк 22×20 с залом на два этажа, материалы заходами: запас тиков на подходы и заходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel4", timeoutTicks = 60000)
    public static void demo_PlayerHouseLevel4Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 4, BuildRotation.COUNTERCLOCKWISE_90);
    }
}
