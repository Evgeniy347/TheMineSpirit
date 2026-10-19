package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.fishing.FishingSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.FishingWorkPointPicker;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FishingGameTests {
    private static final int POND_MIN = 4;
    private static final int POND_MAX = 6;
    private static final BlockPos STATION_CELL = new BlockPos(3, 1, 1);

    private FishingGameTests() {}

    // [флак] поклёвка случайна (ванильное ожидание до ~380 тиков), плюс подход к берегу — потолок с запасом
    @GameTest(template = "arena", batch = "fishing_pond", timeoutTicks = 900)
    public static void fish_RodBesidePond_ReelsInFish(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        pond(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, lureRod(helper));
        SpiritRun.launch(
                helper, spirit, new ScriptCall("fish", Map.of("count", 1, "duration_ticks", 600, "radius", 16)), 1);

        helper.succeedWhen(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(
                    status == RunStatus.SUCCEEDED,
                    "дух с удочкой у пруда обязан поймать рыбу за отведённое время: статус " + status + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    fishCount(spirit) >= 1,
                    "после успешной рыбалки в инвентаре обязана быть рыба: иначе ScriptResult врёт");
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "fishing_no_rod", timeoutTicks = 200)
    public static void fish_NoRod_Refuses(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        pond(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        SpiritRun.launch(
                helper, spirit, new ScriptCall("fish", Map.of("count", 1, "duration_ticks", 600, "radius", 16)), 1);

        helper.succeedWhen(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(status == RunStatus.FAILED, "без удочки рыбалка обязана отказать, статус: " + status);
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("удочк"),
                    "причина отказа обязана называть отсутствие удочки, фактическая: '" + reason + "'");
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "fishing_smoker", timeoutTicks = 400)
    public static void smelt_CodInSmoker_CookedCod(GameTestHelper helper) {
        CraftTestSupport.field(helper);
        helper.setBlock(STATION_CELL, Blocks.SMOKER);
        SpiritEntity spirit = CraftTestSupport.prepare(
                helper, CraftTestSupport.stack(Items.COD, 2), CraftTestSupport.stack(Items.COAL, 1));
        CraftTestSupport.speedUpFurnaces(helper);
        SpiritRun run = CraftTestSupport.launch(helper, spirit, "smelt", "cooked_cod", 2);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            CraftTestSupport.assertCount(helper, spirit, Items.COOKED_COD, 2, "жареная треска из коптильни");
            CraftTestSupport.assertCount(helper, spirit, Items.COD, 0, "сырая треска после копчения");
            CraftTestSupport.cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "fishing_smoker_puffer", timeoutTicks = 200)
    public static void smelt_PufferfishRequested_Refused(GameTestHelper helper) {
        CraftTestSupport.field(helper);
        helper.setBlock(STATION_CELL, Blocks.SMOKER);
        SpiritEntity spirit = CraftTestSupport.prepare(
                helper, CraftTestSupport.stack(Items.PUFFERFISH, 2), CraftTestSupport.stack(Items.COAL, 1));
        SpiritRun run = CraftTestSupport.launch(helper, spirit, "smelt", "pufferfish", 2);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.FAILED);
            CraftTestSupport.assertReason(helper, spirit, "нет рецепта");
            CraftTestSupport.assertCount(
                    helper, spirit, Items.PUFFERFISH, 2, "иглобрюх не жарится и остаётся сырым на складе");
            CraftTestSupport.cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "fishing_bucket", timeoutTicks = 400)
    public static void bucketfish_CodAtPond_BucketsCod(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        pond(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.BUCKET));
        helper.spawn(EntityType.COD, new BlockPos(POND_MIN, 1, POND_MIN));
        SpiritRun.launch(helper, spirit, new ScriptCall("bucket_fish", Map.of("count", 1, "radius", 16)), 1);

        helper.succeedWhen(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(
                    status == RunStatus.SUCCEEDED,
                    "дух с пустым ведром у пруда обязан поймать рыбу: статус " + status + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    countItem(spirit, Items.COD_BUCKET) >= 1,
                    "после ловли ведром в инвентаре обязано быть ведро трески: иначе скрипт соврал");
            helper.assertTrue(
                    countItem(spirit, Items.BUCKET) == 0,
                    "пустое ведро обязано уйти на рыбу: остаток " + countItem(spirit, Items.BUCKET));
            spirit.discard();
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "fishing_workpoint", timeoutTicks = 100)
    public static void workpoint_OpenWaterNearBuilding_ChoosesFarShore(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        deepPond(helper);
        BlockCoord origin = coord(helper, new BlockPos(0, 1, 0));
        BlockCoord pondMin = coord(helper, new BlockPos(2, 0, 2));
        BlockCoord pondMax = coord(helper, new BlockPos(POND_MIN + 2, 0, POND_MIN + 2));

        Optional<FishingSpots.Spot> picked =
                FishingWorkPointPicker.pick(helper.getLevel(), origin, FishingData.loadDefault(), 16, List.of(), 3.0);

        helper.assertTrue(
                picked.isPresent(),
                "У открытой воды рабочая точка обязана найтись: без неё рыбалка не станет занятием " + "(" + origin
                        + "); пруд: " + describePond(helper));
        FishingSpots.Spot spot = picked.orElseThrow();
        helper.assertTrue(
                spot.water().x() >= pondMin.x()
                        && spot.water().x() <= pondMax.x()
                        && spot.water().z() >= pondMin.z()
                        && spot.water().z() <= pondMax.z(),
                "Цель заброса обязана лежать в пятне воды, а не в постройке: " + spot.water() + " вне [" + pondMin
                        + ", " + pondMax + "]");

        Optional<FishingSpots.Spot> nearBuilding = FishingWorkPointPicker.pick(
                helper.getLevel(), origin, FishingData.loadDefault(), 16, List.of(spot.stand()), 3.0);

        helper.assertTrue(
                nearBuilding.isEmpty(),
                "Рабочая точка не выбирается у постройки: занятая постройкой стоянка " + spot.stand()
                        + " не должна стать местом рыбалки, а выбрана " + nearBuilding.orElse(null));
        helper.succeed();
    }

    private static void pond(GameTestHelper helper) {
        for (int x = POND_MIN; x <= POND_MAX; x++) {
            for (int z = POND_MIN; z <= POND_MAX; z++) {
                helper.setBlock(x, 1, z, Blocks.WATER);
            }
        }
    }

    private static void deepPond(GameTestHelper helper) {
        for (int x = 2; x <= POND_MIN + 2; x++) {
            for (int z = 2; z <= POND_MIN + 2; z++) {
                helper.setBlock(x, 0, z, Blocks.WATER);
                helper.setBlock(x, 1, z, Blocks.WATER);
            }
        }
    }

    private static String describePond(GameTestHelper helper) {
        StringBuilder report = new StringBuilder();
        for (int y = 0; y <= 2; y++) {
            report.append("y=").append(y).append(':');
            for (int x = 1; x <= 7; x++) {
                report.append(' ')
                        .append(helper.getBlockState(new BlockPos(x, y, 2)).getBlock());
            }
            report.append(" | ");
        }
        return report.toString();
    }

    private static BlockCoord coord(GameTestHelper helper, BlockPos local) {
        BlockPos absolute = helper.absolutePos(local);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    private static int countItem(SpiritEntity spirit, Item item) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ItemStack lureRod(GameTestHelper helper) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        rod.enchant(
                helper.getLevel()
                        .registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.LURE),
                3);
        return rod;
    }

    private static int fishCount(SpiritEntity spirit) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(ItemTags.FISHES)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
