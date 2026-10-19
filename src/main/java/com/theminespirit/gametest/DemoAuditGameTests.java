package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static final int FLOOR_SIZE = 11;
    // Решение владельца 18.10 (ветка reach-planner): дом семьи — не больше 15 построений пути на весь дом.
    private static final int FAMILY_HOUSE_MAX_PATH_BUILDS = 15;
    private static final int DEFAULT_MAX_PATH_BUILDS = 15;
    // Чаровальня 2 (владелец 19.10) — зал 11×9 на два кольца полок у стен: стоянки только в кольцах и проходе у
    // входа, замер 9–16 подходов по поворотам; запас 2 подхода сверх худшего поворота.
    private static final int ENCHANT_HALL_MAX_PATH_BUILDS = 18;
    private static final int DOUBLE_CHEST_ROWS = 6;

    private DemoAuditGameTests() {}

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < FLOOR_SIZE; x++) {
            for (int z = 0; z < FLOOR_SIZE; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
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
        String scheme = DemoBuildings.require(building).level(level).scheme();
        GameTestFixtures.reset(helper);
        floor(helper);
        Schematic schematic = SchematicResources.load(scheme);
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockCoord origin = new BlockCoord(anchor.getX(), anchor.getY(), anchor.getZ());
        preplaceWithoutItem(helper, schematic, origin, rotation);
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin, rotation, 3);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), spawnOutside(schematic, rotation));
        spirit.setDemo(true);
        give(helper, spirit, schematic);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
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
                                rotation.id())),
                SETTLE_TICKS);

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
            GameTestFixtures.finish(helper);
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

    private static BlockPos spawnOutside(Schematic schematic, BuildRotation rotation) {
        int rotatedX = rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ());
        int rotatedZ = rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ());
        if (rotatedZ < FLOOR_SIZE) {
            return new BlockPos(FLOOR_SIZE / 2, 1, FLOOR_SIZE - 1);
        }
        if (rotatedX < FLOOR_SIZE) {
            return new BlockPos(FLOOR_SIZE - 1, 1, FLOOR_SIZE / 2);
        }
        throw new IllegalStateException("Постройка " + rotatedX + "×" + rotatedZ + " занимает весь пол арены "
                + FLOOR_SIZE + "×" + FLOOR_SIZE + ": духу негде появиться снаружи");
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
    @GameTest(template = "mine_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseNone_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 0, BuildRotation.NONE, FAMILY_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 0, BuildRotation.CLOCKWISE_90, FAMILY_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 0, BuildRotation.CLOCKWISE_180, FAMILY_HOUSE_MAX_PATH_BUILDS);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_familyhouse", timeoutTicks = 4000)
    public static void demo_FamilyHouseCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "family_house", 0, BuildRotation.COUNTERCLOCKWISE_90, FAMILY_HOUSE_MAX_PATH_BUILDS);
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
        audit(helper, "torch_post", 0, BuildRotation.NONE);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_torchpost", timeoutTicks = 4000)
    public static void demo_TorchPostClockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "torch_post", 0, BuildRotation.CLOCKWISE_90);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_torchpost", timeoutTicks = 4000)
    public static void demo_TorchPostClockwise180_MatchesScheme(GameTestHelper helper) {
        audit(helper, "torch_post", 0, BuildRotation.CLOCKWISE_180);
    }

    // [флак] стройка с места с запасом на подходы; тест меряет результат и число подходов, а не скорость.
    @GameTest(template = "mine_arena", batch = "demo_audit_torchpost", timeoutTicks = 4000)
    public static void demo_TorchPostCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        audit(helper, "torch_post", 0, BuildRotation.COUNTERCLOCKWISE_90);
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
}
