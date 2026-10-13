package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClearGameTests {
    private static final int SETTLE_TICKS = 5;

    private ClearGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWithPick(GameTestHelper helper, BlockPos relative) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE, 1));
        return spirit;
    }

    private static ScriptCall clearCall(GameTestHelper helper, BlockPos first, BlockPos second) {
        BlockPos from = helper.absolutePos(first);
        BlockPos to = helper.absolutePos(second);
        return new ScriptCall("clear_area", Map.of("x1", from.getX(), "y1", from.getY(), "z1", from.getZ(),
                "x2", to.getX(), "y2", to.getY(), "z2", to.getZ()));
    }

    private static void placeByPlayer(GameTestHelper helper, ServerPlayer player, BlockPos support) {
        BlockPos absolute = helper.absolutePos(support);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.COBBLESTONE.asItem()));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = "arena", batch = "clear", timeoutTicks = 400)
    public static void clear_WallOfCobble_ClearsAndCollectsDrops(GameTestHelper helper) {
        prepare(helper);
        for (int y = 1; y <= 3; y++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.COBBLESTONE);
            }
        }
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, clearCall(helper, new BlockPos(5, 1, 4), new BlockPos(5, 3, 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "стенка 3×3 из булыжника обязана убраться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int left = 0;
            for (int y = 1; y <= 3; y++) {
                for (int z = 4; z <= 6; z++) {
                    if (!helper.getBlockState(new BlockPos(5, y, z)).isAir()) {
                        left++;
                    }
                }
            }
            helper.assertTrue(left == 0,
                    "все 9 блоков стенки обязаны исчезнуть, осталось " + left);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 9,
                    "дроп обязан подобраться в инвентарь: 9 булыжников, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "clear", timeoutTicks = 400)
    public static void clear_BedrockAndLavaNeighbour_SkipsWithoutFailing(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(4, 1, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 7), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.LAVA);
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, clearCall(helper, new BlockPos(4, 1, 4), new BlockPos(6, 1, 5)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "расчистка с пропусками обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)).isAir()
                    && helper.getBlockState(new BlockPos(6, 1, 4)).isAir(),
                    "ломаемые блоки стенки обязаны убраться");
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 4)).is(Blocks.BEDROCK),
                    "бедрок неразрушим: обязан остаться, а не ронять задачу");
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.COBBLESTONE),
                    "блок рядом с лавой небезопасен: обязан остаться, иначе жидкость затечёт в полость");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "в инвентаре обязан лежать дроп двух убранных блоков, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "clear", timeoutTicks = 400)
    public static void clear_BlockUnderFeet_VetoSkipsAndSpiritStays(GameTestHelper helper) {
        prepare(helper);
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 0, 1)).is(Blocks.STONE),
                "диагностика: пола нет в (1,0,1)");
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(spirit.blockPosition().equals(helper.absolutePos(new BlockPos(1, 1, 1))),
                "диагностика: дух заспавнился не в (1,1,1): " + spirit.blockPosition());
        SpiritRun run = SpiritRun.launch(helper, spirit, clearCall(helper, new BlockPos(1, 0, 1), new BlockPos(1, 0, 1)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.blockPosition().equals(helper.absolutePos(new BlockPos(1, 1, 1))),
                    "диагностика: дух ушёл с (1,1,1) до проверки вето: " + spirit.blockPosition());
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "пол под ногами рубить нельзя: задача обязана провалиться пропуском, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("не убран ни один блок"),
                    "причина обязана честно сказать, что не убран ни один блок, фактически: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(new BlockPos(1, 0, 1)).is(Blocks.STONE),
                    "опора под духом обязана остаться целой: без вето дух подрубил бы пол и упал");
            helper.assertTrue(spirit.blockPosition().getY() == helper.absolutePos(new BlockPos(1, 1, 1)).getY(),
                    "дух обязан стоять на месте, а не провалиться вниз");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "clear", timeoutTicks = 400)
    public static void clear_OwnWall_RemovedBySpirit(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 2));
        BlockPlacer placer = new BlockPlacer(spirit);
        for (BlockPos cell : new BlockPos[]{new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)}) {
            BlockPlacer.Begin placed = placer.begin(helper.absolutePos(cell), BlockStates.stateFor("cobblestone"),
                    "cobblestone");
            helper.assertTrue(placed instanceof BlockPlacer.Started,
                    "подготовка обязана поставить свой блок в " + cell + ": без метки духа проверять нечего, ответ " + placed);
        }
        helper.assertTrue(PlacedBlocksData.get(helper.getLevel()).isSpiritMarked(helper.absolutePos(new BlockPos(2, 1, 2))),
                "поставленный духом блок обязан нести метку духа, иначе разбор своей постройки не отличить от чужой");
        SpiritRun run = SpiritRun.launch(helper, spirit, clearCall(helper, new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "свою постройку дух обязан разобрать, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).isAir()
                    && helper.getBlockState(new BlockPos(3, 1, 2)).isAir(),
                    "оба своих блока обязаны исчезнуть");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "clear", timeoutTicks = 400)
    public static void clear_PlayerWall_RefusedAsProtected(GameTestHelper helper) {
        prepare(helper);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 4), "Строитель");
        placeByPlayer(helper, player, new BlockPos(2, 0, 2));
        placeByPlayer(helper, player, new BlockPos(3, 0, 2));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.COBBLESTONE)
                && helper.getBlockState(new BlockPos(3, 1, 2)).is(Blocks.COBBLESTONE),
                "игрок обязан поставить стенку из двух блоков: без неё отказ проверять не на чем");
        helper.assertTrue(PlacedBlocksData.get(helper.getLevel()).isMarked(helper.absolutePos(new BlockPos(2, 1, 2))),
                "блок игрока обязан отметиться как постройка игрока, иначе защита не сработает");
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, clearCall(helper, new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "чужую постройку разбирать нельзя: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("защищено"),
                    "причина обязана называть защиту, фактически: " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.COBBLESTONE)
                    && helper.getBlockState(new BlockPos(3, 1, 2)).is(Blocks.COBBLESTONE),
                    "стенка игрока обязана остаться целой");
            GameTestFixtures.reset(helper);
        });
    }
}
