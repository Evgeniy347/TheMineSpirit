package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Раскладка демо-стройки, увиденная из мира: площадка вокруг дома расчищена и выровнена до стройки,
 * вход без явного поворота смотрит на игрока в любую сторону, а после стройки на площадке нет ни одного
 * блока мимо схемы и сундука. Проверяется на доме семьи: у него есть крыша, окна и мебель.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemoLayoutGameTests {
    private static final BlockPos ANCHOR = new BlockPos(15, 1, 15);

    private DemoLayoutGameTests() {}

    private static CommandSourceStack source(ServerPlayer player) {
        return player.server
                .createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(4);
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    private static List<SpiritEntity> spiritsNear(GameTestHelper helper, BlockPos absolute, int radius) {
        AABB box = new AABB(absolute).inflate(radius);
        return helper.getLevel().getEntitiesOfClass(SpiritEntity.class, box, spirit -> !spirit.isRemoved());
    }

    private static DemoPlacement placement(BlockPos anchor, int lookDx, int lookDz) {
        Preset family =
                DataTables.INSTANCE.presets().current().byId("family_house").orElseThrow();
        return DemoPlacement.compute(
                new DemoPlacement.Anchor(anchor.getX(), anchor.getY(), anchor.getZ()),
                new DemoPlacement.Look(lookDx, lookDz),
                DemoPlacement.Door.of(family),
                new DemoPlacement.Size(family.sizeX(), family.sizeY(), family.sizeZ()),
                null);
    }

    private static void assertPlatformPrepared(GameTestHelper helper, DemoPlacement placement) {
        BlockCoord chest = placement.chest();
        for (BlockCoord cell : placement.platform().cells()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            boolean isChest = cell.equals(chest);
            if (isChest) {
                helper.assertTrue(
                        helper.getLevel().getBlockState(pos).is(Blocks.CHEST),
                        "Клетка сундука обязана держать сундук после подготовки площадки: " + cell);
            } else {
                helper.assertTrue(
                        helper.getLevel().getBlockState(pos).isAir(),
                        "До стройки на площадке не должно быть блоков, кроме сундука, а в " + cell + " стоит "
                                + helper.getLevel().getBlockState(pos).getBlock());
            }
        }
    }

    private static void assertBuiltMatchesSchema(GameTestHelper helper, DemoPlacement placement) {
        Schematic schematic = SchematicResources.load("family_house");
        List<Schematic.PlacedEntry> expected = schematic.entries(placement.origin(), placement.rotation());
        Map<BlockCoord, String> schema = new HashMap<>();
        for (Schematic.PlacedEntry entry : expected) {
            schema.put(entry.pos(), entry.block());
        }
        for (BlockCoord cell : placement.platform().cells()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            var state = helper.getLevel().getBlockState(pos);
            if (cell.equals(placement.chest())) {
                helper.assertTrue(state.is(Blocks.CHEST), "Сундук обязан остаться на площадке после стройки: " + cell);
                continue;
            }
            String block = schema.get(cell);
            if (block == null) {
                helper.assertTrue(
                        state.isAir(),
                        "На площадке не должно быть блоков мимо схемы и сундука, а в " + cell + " стоит "
                                + state.getBlock());
            } else if (BlockItems.hasBlockItem(block)) {
                helper.assertTrue(
                        state.is(BlockStates.blockFor(block)),
                        "Клетка " + cell + " схемы обязана держать " + block + ", а держит " + state.getBlock()
                                + ": поворот или начало постройки разошлись с ожиданием");
            }
        }
    }

    private static void runFamilyHouse(GameTestHelper helper, int yaw, BuildRotation expectedRotation)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "gt_layout_" + yaw);
        player.setYRot(yaw);
        BlockPos anchor = helper.absolutePos(ANCHOR);

        int accepted = dispatcher(helper)
                .execute(
                        "spirit demo family_house " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ(),
                        source(player));
        helper.assertTrue(accepted == 1, "Демо-стройка без явного поворота обязана начаться: результат=" + accepted);

        int lookDx = player.getDirection().getStepX();
        int lookDz = player.getDirection().getStepZ();
        DemoPlacement placement = placement(anchor, lookDx, lookDz);
        helper.assertTrue(
                placement.rotation() == expectedRotation,
                "Без явного поворота вход обязан смотреть на игрока: взгляд " + lookDx + "," + lookDz
                        + " требует поворота " + expectedRotation + ", а выбран " + placement.rotation());
        assertPlatformPrepared(helper, placement);

        List<SpiritEntity> spawned = spiritsNear(helper, anchor, 5);
        helper.assertTrue(!spawned.isEmpty(), "Дух-строитель обязан появиться на площадке");
        SpiritEntity spirit = spawned.get(0);
        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.isRemoved(), "После «Готово» дух обязан пропасть: он остался у постройки");
            assertBuiltMatchesSchema(helper, placement);
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] 147 блоков дома семьи дух ставит по одному за тик с подходом к каждой клетке; потолок с запасом,
    // иначе тест меряет скорость стройки, а не её результат.
    @GameTest(template = "demo_arena", batch = "demo_layout", timeoutTicks = 12000)
    public static void familyHouse_LookSouth_TurnsEntryToPlayerAndClearsPlatform(GameTestHelper helper)
            throws CommandSyntaxException {
        runFamilyHouse(helper, 0, BuildRotation.NONE);
    }

    // [флак] 147 блоков дома семьи дух ставит по одному за тик с подходом к каждой клетке; потолок с запасом.
    @GameTest(template = "demo_arena", batch = "demo_layout", timeoutTicks = 12000)
    public static void familyHouse_LookWest_TurnsEntryToPlayerAndClearsPlatform(GameTestHelper helper)
            throws CommandSyntaxException {
        runFamilyHouse(helper, 90, BuildRotation.CLOCKWISE_90);
    }

    // [флак] 147 блоков дома семьи дух ставит по одному за тик с подходом к каждой клетке; потолок с запасом.
    @GameTest(template = "demo_arena", batch = "demo_layout", timeoutTicks = 12000)
    public static void familyHouse_LookNorth_TurnsEntryToPlayerAndClearsPlatform(GameTestHelper helper)
            throws CommandSyntaxException {
        runFamilyHouse(helper, 180, BuildRotation.CLOCKWISE_180);
    }

    // [флак] 147 блоков дома семьи дух ставит по одному за тик с подходом к каждой клетке; потолок с запасом.
    @GameTest(template = "demo_arena", batch = "demo_layout", timeoutTicks = 12000)
    public static void familyHouse_LookEast_TurnsEntryToPlayerAndClearsPlatform(GameTestHelper helper)
            throws CommandSyntaxException {
        runFamilyHouse(helper, 270, BuildRotation.COUNTERCLOCKWISE_90);
    }
}
