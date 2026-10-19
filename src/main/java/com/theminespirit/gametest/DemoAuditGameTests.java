package com.theminespirit.gametest;

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
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/**
 * Каждая постройка демо-каталога в четырёх поворотах: BuildAudit обязан не найти ни одного расхождения — ни по
 * клеткам схемы, ни по лишним блокам, ни по числу постановок, — а дух обязан строить с места: число построений пути
 * ограничено (решение владельца 18.10: «не бегает — ставит всё, до чего дотягивается»). Четыре поворота одной
 * постройки — одна пачка: четыре арены строятся параллельно.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemoAuditGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int MIN_CLEAR_HEIGHT = 8;
    private static final int FLOOR_SIZE = 11;
    private static final int FLOOR_MARGIN = 4;
    // Решение владельца 18.10 (ветка reach-planner): дом семьи — не больше 15 построений пути на весь дом.
    private static final int FAMILY_HOUSE_MAX_PATH_BUILDS = 15;
    // Дом семьи уровня 2 (19.10) больше хижины: подходов больше; пределы — замер по поворотам с запасом.
    private static final int FAMILY_HOUSE_LEVEL2_MAX_PATH_BUILDS = 40;
    private static final int DEFAULT_MAX_PATH_BUILDS = 15;
    // Чаровальня 2 (владелец 19.10) — зал 11×9 на два кольца полок у стен: стоянки только в кольцах и проходе у
    // входа, замер 9–16 подходов по поворотам; запас 2 подхода сверх худшего поворота.
    private static final int ENCHANT_HALL_MAX_PATH_BUILDS = 18;
    private static final int DOUBLE_CHEST_ROWS = 6;
    // Дом семьи 3 (19.10) — два этажа, материалы заходами: замер 72–84 подхода по поворотам (20.10), запас 10 %.
    private static final int FAMILY_HOUSE_LEVEL3_MAX_PATH_BUILDS = 93;
    // Заходов за материалами, как в демо (DemoSession.MAX_ROUNDS): больше — значит, стройка не продвигается.
    private static final int SPIRIT_SLOTS = 36;
    // Схемы витрины, заход 2 (20.10), замер по поворотам: усадьба 185–188 (заходы, ~3200 блоков), дом игрока 31–39 и
    // 51–82 (уровень 2), площадь 30–40 и 23–25 (уровень 2), колодец 4–9, фонари 1–2.
    private static final int FAMILY_HOUSE_LEVEL4_MAX_PATH_BUILDS = 207;
    private static final int PLAYER_HOUSE_MAX_PATH_BUILDS = 44;
    private static final int PLAZA_MAX_PATH_BUILDS = 45;
    private static final int WELL2_MAX_PATH_BUILDS = 11;
    private static final int LAMP_MAX_PATH_BUILDS = 3;
    private static final int PLAZA2_MAX_PATH_BUILDS = 28;
    private static final int PLAYER_HOUSE2_MAX_PATH_BUILDS = 90;
    private static final int SUPPLY_RING = 2;
    // Склад-хижина с двумя свободными слотами: 7 заходов к сундуку, замер 12 подходов (20.10), запас 3.
    private static final int STORAGE_HUT_TRIPS_MAX_PATH_BUILDS = 15;
    private static final int SUPPLY_SIDE_OFFSET = 3;

    private DemoAuditGameTests() {}

    // Высокая постройка (дом семьи 1 — 10 слоёв с крышей) оставила бы конёк соседнему тесту: чистим на её высоту.
    private static int clearHeight(Schematic schematic) {
        return Math.max(MIN_CLEAR_HEIGHT, schematic.sizeY() + 2);
    }

    // Пол — с запасом в 4 клетки сверх большей стороны постройки: духу нужно место появиться снаружи.
    private static int floorSize(Schematic schematic) {
        return Math.max(FLOOR_SIZE, Math.max(schematic.sizeX(), schematic.sizeZ()) + FLOOR_MARGIN);
    }

    private static void clearArea(GameTestHelper helper, int size, int height) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < size; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static void floor(GameTestHelper helper, int size, int height) {
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
    private record SupplyPoint(BlockPos base, Direction partner) {}

    // Сундуки — у главного входа, как в демо: за ступенью, в стороне от линии подхода.
    private static SupplyPoint supplyPoint(String scheme, Schematic schematic, BuildRotation rotation, int ring) {
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
        audit(helper, building, level, rotation, DEFAULT_MAX_PATH_BUILDS);
    }

    @FunctionalInterface
    private interface AfterBuild {
        void check(GameTestHelper helper, Schematic schematic, BlockCoord origin, BuildRotation rotation);
    }

    private static void audit(
            GameTestHelper helper, String building, int level, BuildRotation rotation, int maxPathBuilds) {
        audit(helper, building, level, rotation, maxPathBuilds, (h, schematic, origin, turned) -> {});
    }

    private static void audit(
            GameTestHelper helper,
            String building,
            int level,
            BuildRotation rotation,
            int maxPathBuilds,
            AfterBuild after) {
        audit(helper, building, level, rotation, maxPathBuilds, after, SPIRIT_SLOTS);
    }

    // freeSlots < 36 — инвентарь духа занят чужим, свободно freeSlots: стройка обязана идти порциями из сундуков.
    private static void audit(
            GameTestHelper helper,
            String building,
            int level,
            BuildRotation rotation,
            int maxPathBuilds,
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
        preplaceWithoutItem(helper, schematic, origin, rotation);
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

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Постройка '" + scheme + "' (" + rotation + ") обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = data(helper, spirit);
            int skipped = ((Number) data.get("skipped")).intValue();
            BuildAudit.Result audit = BuildAudit.audit(helper.getLevel(), schematic, origin, rotation, before, skipped);
            helper.assertTrue(
                    audit.ok(),
                    "Постройка '" + scheme + "' (" + rotation
                            + ") обязана совпасть со схемой без лишних блоков, расхождения: " + audit.problems());
            helper.assertTrue(
                    spirit.pathBuilds() <= maxPathBuilds,
                    "Постройка '" + scheme + "' (" + rotation
                            + ") обязана уложиться в " + maxPathBuilds
                            + " построений пути: дух ставит всё досягаемое с "
                            + "места, а не подходит к каждому блоку; фактически " + spirit.pathBuilds());
            after.check(helper, schematic, origin, rotation);
            emptyChests(helper, chests);
            clearArea(helper, floorSize, clearHeight);
            GameTestFixtures.finish(helper, clearHeight);
        });
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

    private static void preplaceWithoutItem(
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
        audit(helper, "family_house", 1, BuildRotation.NONE, FAMILY_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 1, BuildRotation.CLOCKWISE_90, FAMILY_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 1, BuildRotation.CLOCKWISE_180, FAMILY_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 1, BuildRotation.COUNTERCLOCKWISE_90, FAMILY_HOUSE_MAX_PATH_BUILDS);
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
        audit(
                helper,
                "storage",
                1,
                BuildRotation.NONE,
                DEFAULT_MAX_PATH_BUILDS,
                DemoAuditGameTests::assertDoubleChests);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagelevel1", timeoutTicks = 4000)
    public static void demo_StorageLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(
                helper,
                "storage",
                1,
                BuildRotation.CLOCKWISE_90,
                DEFAULT_MAX_PATH_BUILDS,
                DemoAuditGameTests::assertDoubleChests);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagelevel1", timeoutTicks = 4000)
    public static void demo_StorageLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(
                helper,
                "storage",
                1,
                BuildRotation.CLOCKWISE_180,
                DEFAULT_MAX_PATH_BUILDS,
                DemoAuditGameTests::assertDoubleChests);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_storagelevel1", timeoutTicks = 4000)
    public static void demo_StorageLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(
                helper,
                "storage",
                1,
                BuildRotation.COUNTERCLOCKWISE_90,
                DEFAULT_MAX_PATH_BUILDS,
                DemoAuditGameTests::assertDoubleChests);
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
        audit(helper, "enchant_room", 2, BuildRotation.NONE, ENCHANT_HALL_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel2", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 2, BuildRotation.CLOCKWISE_90, ENCHANT_HALL_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel2", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 2, BuildRotation.CLOCKWISE_180, ENCHANT_HALL_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_enchantroomlevel2", timeoutTicks = 4000)
    public static void demo_EnchantRoomLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "enchant_room", 2, BuildRotation.COUNTERCLOCKWISE_90, ENCHANT_HALL_MAX_PATH_BUILDS);
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
        audit(helper, "family_house", 2, BuildRotation.NONE, FAMILY_HOUSE_LEVEL2_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 2 — сотни блоков и несколько этажей: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel2", timeoutTicks = 8000)
    public static void demo_FamilyHouseLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 2, BuildRotation.CLOCKWISE_90, FAMILY_HOUSE_LEVEL2_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 2 — сотни блоков и несколько этажей: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel2", timeoutTicks = 8000)
    public static void demo_FamilyHouseLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 2, BuildRotation.CLOCKWISE_180, FAMILY_HOUSE_LEVEL2_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 2 — сотни блоков и несколько этажей: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel2", timeoutTicks = 8000)
    public static void demo_FamilyHouseLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 2, BuildRotation.COUNTERCLOCKWISE_90, FAMILY_HOUSE_LEVEL2_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.NONE, FAMILY_HOUSE_LEVEL3_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.CLOCKWISE_90, FAMILY_HOUSE_LEVEL3_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.CLOCKWISE_180, FAMILY_HOUSE_LEVEL3_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 3 — два этажа и заходы за материалами: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel3", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel3Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 3, BuildRotation.COUNTERCLOCKWISE_90, FAMILY_HOUSE_LEVEL3_MAX_PATH_BUILDS);
    }

    // Решение владельца 20.10: в инвентаре два свободных слота — дух берёт порцию, ставит всё, что ею можно, и идёт
    // за следующей; склад-хижина из 11 материалов строится заходами до конца.
    // [флак] семь заходов к сундуку и обратно: запас тиков на подходы, тест меряет результат и число подходов.
    @GameTest(template = "big_arena", batch = "demo_audit_trips", timeoutTicks = 8000)
    public static void demo_StorageHutTwoFreeSlots_BuildsInPortions(GameTestHelper helper) {
        audit(helper, "storage_hut", 0, BuildRotation.NONE, STORAGE_HUT_TRIPS_MAX_PATH_BUILDS, (h, s, o, r) -> {}, 2);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel4None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.NONE, FAMILY_HOUSE_LEVEL4_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel4Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.CLOCKWISE_90, FAMILY_HOUSE_LEVEL4_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel4Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.CLOCKWISE_180, FAMILY_HOUSE_LEVEL4_MAX_PATH_BUILDS);
    }

    // [флак] дом семьи уровня 4 — усадьба в два этажа, материалы заходами: запас тиков на подходы, тест меряет
    // результат.
    @GameTest(template = "big_arena", batch = "demo_audit_familyhouselevel4", timeoutTicks = 40000)
    public static void demo_FamilyHouseLevel4Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 4, BuildRotation.COUNTERCLOCKWISE_90, FAMILY_HOUSE_LEVEL4_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.NONE, PLAYER_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.CLOCKWISE_90, PLAYER_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.CLOCKWISE_180, PLAYER_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 1 — сотни блоков под коньком: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouse", timeoutTicks = 8000)
    public static void demo_PlayerHouseLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 1, BuildRotation.COUNTERCLOCKWISE_90, PLAYER_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.NONE, PLAZA_MAX_PATH_BUILDS);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.CLOCKWISE_90, PLAZA_MAX_PATH_BUILDS);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.CLOCKWISE_180, PLAZA_MAX_PATH_BUILDS);
    }

    // [флак] площадь 15×16 с колодцем: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plaza", timeoutTicks = 4000)
    public static void demo_PlazaLevel1Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 1, BuildRotation.COUNTERCLOCKWISE_90, PLAZA_MAX_PATH_BUILDS);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.NONE, WELL2_MAX_PATH_BUILDS);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.CLOCKWISE_90, WELL2_MAX_PATH_BUILDS);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.CLOCKWISE_180, WELL2_MAX_PATH_BUILDS);
    }

    // [флак] колодец 2 с крышей на столбах: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_welllevel2", timeoutTicks = 4000)
    public static void demo_WellLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "well", 2, BuildRotation.COUNTERCLOCKWISE_90, WELL2_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.NONE, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.CLOCKWISE_90, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.CLOCKWISE_180, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на столбе: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel2", timeoutTicks = 1200)
    public static void demo_LampLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 2, BuildRotation.COUNTERCLOCKWISE_90, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.NONE, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.CLOCKWISE_90, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.CLOCKWISE_180, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] фонарь на цепи: запас тиков на подход, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_lamplevel3", timeoutTicks = 1200)
    public static void demo_LampLevel3Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "lamp", 3, BuildRotation.COUNTERCLOCKWISE_90, LAMP_MAX_PATH_BUILDS);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.NONE, PLAZA2_MAX_PATH_BUILDS);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.CLOCKWISE_90, PLAZA2_MAX_PATH_BUILDS);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.CLOCKWISE_180, PLAZA2_MAX_PATH_BUILDS);
    }

    // [флак] площадь 2 — мощение 19×20 с узором, скамьи и клумбы: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_plazalevel2", timeoutTicks = 6000)
    public static void demo_PlazaLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "plaza", 2, BuildRotation.COUNTERCLOCKWISE_90, PLAZA2_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2None_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.NONE, PLAYER_HOUSE2_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2Clockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.CLOCKWISE_90, PLAYER_HOUSE2_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2Clockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.CLOCKWISE_180, PLAYER_HOUSE2_MAX_PATH_BUILDS);
    }

    // [флак] дом игрока 2 — зал и две комнаты под крышей 16×15: запас тиков на подходы, тест меряет результат.
    @GameTest(template = "big_arena", batch = "demo_audit_playerhouselevel2", timeoutTicks = 12000)
    public static void demo_PlayerHouseLevel2Counterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "player_house", 2, BuildRotation.COUNTERCLOCKWISE_90, PLAYER_HOUSE2_MAX_PATH_BUILDS);
    }
}
