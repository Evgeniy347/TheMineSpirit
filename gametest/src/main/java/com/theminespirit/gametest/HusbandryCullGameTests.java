package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HusbandryCullGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos START = new BlockPos(1, 1, 1);

    private HusbandryCullGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void husbandry_CullHuntFishWait_HandlersWork(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(2, 1, 5));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(4, 1, 6));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(6, 1, 4));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(6, 1, 6));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(5, 1, 2));
        HusbandrySceneSupport.spawnBaby(helper, EntityType.COW, new BlockPos(1, 1, 7), true);
        HusbandrySceneSupport.spawnStill(helper, EntityType.SHEEP, new BlockPos(8, 1, 5));
        for (int x = 0; x <= 1; x++) {
            for (int z = 3; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
            }
        }
        SpiritEntity spirit = HusbandrySceneSupport.spawnWith(
                helper, START, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.FISHING_ROD));
        SpiritRun hunt = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("hunt", Map.of("species", "sheep", "radius", 8, "count", 1)),
                SETTLE_TICKS);
        int[] phase = {0};
        int[] waited = {0};

        helper.onEachTick(() -> {
            RunStatus status = spirit.taskStatus().orElse(null);
            if (status == null) {
                return;
            }
            switch (phase[0]) {
                case 0 -> {
                    if (status == RunStatus.RUNNING) {
                        return;
                    }
                    hunt.assertStarted();
                    helper.assertTrue(
                            status == RunStatus.SUCCEEDED,
                            "охота на пустой еде обязана добить овцу: статус " + status + ", причина "
                                    + spirit.taskFailureReason().orElse("нет"));
                    helper.assertTrue(
                            GameTestFixtures.countInInventory(spirit.getInventory(), Items.MUTTON) > 0
                                    || GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL) > 0,
                            "охота обязана принести мясо или шерсть в инвентарь духа");
                    phase[0] = 1;
                    spirit.startTask(new ScriptCall("cull", Map.of("species", "cow", "keep", 3, "radius", 8)));
                }
                case 1 -> {
                    if (status == RunStatus.RUNNING) {
                        return;
                    }
                    HusbandrySceneSupport.assertSucceeded(helper, spirit, "из пяти коров лишние две обязаны забиться");
                    List<Cow> cows = HusbandrySceneSupport.alive(helper, Cow.class);
                    helper.assertTrue(
                            cows.size() == 4,
                            "остаться обязаны 3 взрослые и телёнок (всего 4), фактически " + cows.size());
                    helper.assertTrue(
                            cows.stream().filter(Cow::isBaby).count() == 1,
                            "телёнок обязан остаться цел: детёнышей не трогают никогда");
                    int beef = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BEEF);
                    int leather = GameTestFixtures.countInInventory(spirit.getInventory(), Items.LEATHER);
                    helper.assertTrue(
                            beef >= 2 && beef + leather >= 2,
                            "говядина и кожа с двух туш обязаны подобраться (говядины " + beef + ", кожи " + leather
                                    + ")");
                    phase[0] = 2;
                    waited[0] = 0;
                    spirit.startTask(new ScriptCall("fish", Map.of("count", 1, "duration_ticks", 200, "radius", 8)));
                }
                case 2 -> {
                    if (status == RunStatus.RUNNING && waited[0] < 10) {
                        waited[0]++;
                        return;
                    }
                    helper.assertTrue(
                            waited[0] >= 10,
                            "рыбалка обязана продержаться больше мгновенного запуска: тиков " + waited[0]);
                    helper.assertTrue(
                            status == RunStatus.RUNNING,
                            "рыбалка у воды обязана идти, а не падать сразу: статус " + status + ", причина "
                                    + spirit.taskFailureReason().orElse("нет"));
                    helper.assertTrue(
                            "fish".equals(spirit.currentTaskScript().orElse("")),
                            "обработчик еды запускает именно рыбацкий скрипт: " + spirit.currentTaskScript());
                    spirit.cancelTask();
                    phase[0] = 3;
                    spirit.startTask(new ScriptCall("scan_blocks", Map.of("radius", 8)));
                }
                case 3 -> {
                    if (status == RunStatus.RUNNING) {
                        return;
                    }
                    helper.assertTrue(
                            status == RunStatus.SUCCEEDED,
                            "ожидание еды — осмотр места без ходьбы: статус " + status + ", причина "
                                    + spirit.taskFailureReason().orElse("нет"));
                    phase[0] = 4;
                    GameTestFixtures.finish(helper);
                    helper.succeed();
                }
                default -> throw new IllegalStateException("Лишняя фаза проверки стада и еды: " + phase[0]);
            }
        });
    }
}
