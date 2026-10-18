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
import BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Каждая постройка демо-каталога и family_house в четырёх поворотах: BuildAudit обязан не найти
 * ни одного расхождения — ни по клеткам схемы, ни по лишним блокам, ни по числу постановок.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemoAuditGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int FLOOR_SIZE = 11;

    private DemoAuditGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < FLOOR_SIZE; x++) {
            for (int z = 0; z < FLOOR_SIZE; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    private static void give(GameTestHelper helper, SpiritEntity spirit, Schematic schematic) {
        int slot = 0;
        for (Map.Entry<String, Integer> material : PresetLibrary.countMaterials(schematic).entrySet()) {
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
        helper.assertTrue(result instanceof ScriptResult.Data,
                "Успешная стройка обязана нести Data-итог: " + (result == null ? "пусто" : result));
        return ((ScriptResult.Data) result).fields();
    }

    private static void auditBuilt(GameTestHelper helper, String scheme, BuildRotation rotation) {
        GameTestFixtures.reset(helper);
        floor(helper);
        Schematic schematic = SchematicResources.load(scheme);
        BlockPos anchorRelative = new BlockPos(0, 1, 0);
        BlockPos anchor = helper.absolutePos(anchorRelative);
        BlockCoord origin = new BlockCoord(anchor.getX(), anchor.getY(), anchor.getZ());
        preplaceWithoutItem(helper, schematic, origin, rotation);
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin, rotation, 3);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, 1, 10));
        give(helper, spirit, schematic);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_schematic",
                Map.of("file", scheme, "x", anchor.getX(), "y", anchor.getY(), "z", anchor.getZ(),
                        "rotation", rotation.quarterTurns())), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Постройка '" + scheme + "' (" + rotation + ") обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = data(helper, spirit);
            int skipped = ((Number) data.get("skipped")).intValue();
            BuildAudit.Result audit = BuildAudit.audit(helper.getLevel(), schematic, origin, rotation, before, skipped);
            helper.assertTrue(audit.ok(), "Постройка '" + scheme + "' (" + rotation
                    + ") обязана совпасть со схемой без лишних блоков, расхождения: " + audit.problems());
            GameTestFixtures.finish(helper);
        });
    }

    private static void preplaceWithoutItem(GameTestHelper helper, Schematic schematic, BlockCoord origin,
            BuildRotation rotation) {
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
            BlockPos pos = new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            BlockState state = BlockStates.stateFor(entry.block(), entry.state());
            helper.getLevel().setBlock(pos, rotation == BuildRotation.NONE ? state : state.rotate(helper.getLevel(), pos, mc), 3);
        }
    }

    // [флак] 147 блоков дома: подход к клеткам стены с запасом, тест меряет результат, а не скорость.
    @GameTest(template = "arena", batch = "demo_audit_family_none", timeoutTicks = 6000)
    public static void demo_FamilyHouseNoRotation_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, "family_house", BuildRotation.NONE);
    }

    // [флак] 147 блоков дома: подход к клеткам стены с запасом.
    @GameTest(template = "arena", batch = "demo_audit_family_90", timeoutTicks = 6000)
    public static void demo_FamilyHouseClockwise90_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, "family_house", BuildRotation.CLOCKWISE_90);
    }

    // [флак] 147 блоков дома: подход к клеткам стены с запасом.
    @GameTest(template = "arena", batch = "demo_audit_family_180", timeoutTicks = 6000)
    public static void demo_FamilyHouseClockwise180_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, "family_house", BuildRotation.CLOCKWISE_180);
    }

    // [флак] 147 блоков дома: подход к клеткам стены с запасом.
    @GameTest(template = "arena", batch = "demo_audit_family_270", timeoutTicks = 6000)
    public static void demo_FamilyHouseCounterclockwise90_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, "family_house", BuildRotation.COUNTERCLOCKWISE_90);
    }

    // [флак] 17 блоков первого дома: подход к клеткам с запасом.
    @GameTest(template = "arena", batch = "demo_audit_house", timeoutTicks = 3000)
    public static void demo_House_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("house"), BuildRotation.NONE);
    }

    // [флак] 66 блоков склада: подход к клеткам с запасом, тест меряет результат, а не скорость.
    @GameTest(template = "arena", batch = "demo_audit_storage_hut", timeoutTicks = 6000)
    public static void demo_StorageHut_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("storage_hut"), BuildRotation.NONE);
    }

    // [флак] 80 блоков каменного дома: подход к клеткам с запасом.
    @GameTest(template = "arena", batch = "demo_audit_stone_house", timeoutTicks = 6000)
    public static void demo_StoneHouse_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("stone_house"), BuildRotation.NONE);
    }

    // [флак] 13 блоков колодца, вода ставится без предмета.
    @GameTest(template = "arena", batch = "demo_audit_well", timeoutTicks = 3000)
    public static void demo_Well_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("well"), BuildRotation.NONE);
    }

    // [флак] 4 блока факельного столба: факел ставится на забор после updateFromNeighbourShapes.
    @GameTest(template = "arena", batch = "demo_audit_torch_post", timeoutTicks = 3000)
    public static void demo_TorchPost_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("torch_post"), BuildRotation.NONE);
    }

    // [флак] 8 блоков палисада с калиткой: подход к клеткам с запасом.
    @GameTest(template = "arena", batch = "demo_audit_palisade", timeoutTicks = 3000)
    public static void demo_PalisadeSection_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("palisade_section"), BuildRotation.NONE);
    }

    // [флак] 14 блоков загона уровня 1: подход к клеткам с запасом.
    @GameTest(template = "arena", batch = "demo_audit_pen", timeoutTicks = 3000)
    public static void demo_Pen_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("pen"), BuildRotation.NONE);
    }

    // [флак] 244 блока чаровальни уровня 1: подход к клеткам с запасом, тест меряет результат.
    @GameTest(template = "arena", batch = "demo_audit_enchant", timeoutTicks = 12000)
    public static void demo_EnchantRoom_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("enchant_room"), BuildRotation.NONE);
    }

    // [флак] 17 блоков входа в шахту: подход к клеткам с запасом.
    @GameTest(template = "arena", batch = "demo_audit_mine", timeoutTicks = 3000)
    public static void demo_MineEntrance_MatchesScheme(GameTestHelper helper) {
        auditBuilt(helper, scheme("mine_entrance"), BuildRotation.NONE);
    }

    private static String scheme(String id) {
        return DemoBuildings.require(id).levels().get(0).scheme();
    }
}
