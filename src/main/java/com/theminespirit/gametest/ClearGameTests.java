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

    private ClearGameTests() {}

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
        return new ScriptCall(
                "clear_area",
                Map.of(
                        "x1",
                        from.getX(),
                        "y1",
                        from.getY(),
                        "z1",
                        from.getZ(),
                        "x2",
                        to.getX(),
                        "y2",
                        to.getY(),
                        "z2",
                        to.getZ()));
    }

    private static void placeByPlayer(GameTestHelper helper, ServerPlayer player, BlockPos support) {
        BlockPos absolute = helper.absolutePos(support);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.COBBLESTONE.asItem()));
        BlockHitResult hit =
                new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);
        player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND,
                hit);
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void clear_WallOfCobble_ClearsAndCollectsDrops(GameTestHelper helper) {
        prepare(helper);
        for (int y = 1; y <= 3; y++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.COBBLESTONE);
            }
        }
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, clearCall(helper, new BlockPos(5, 1, 4), new BlockPos(5, 3, 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
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
            helper.assertTrue(left == 0, "все 9 блоков стенки обязаны исчезнуть, осталось " + left);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 9,
                    "дроп обязан подобраться в инвентарь: 9 булыжников, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void clear_AlreadyEmptyArea_Succeeds(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, clearCall(helper, new BlockPos(5, 1, 4), new BlockPos(6, 3, 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "уже чистый участок — расчистка выполнена, а не провал «не убран ни один блок» (CLEAR_GROUNDS "
                            + "приёмки «джунгли» падал на чистой четверти); статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void clear_OnlyProtectedTorch_SucceedsAndKeepsIt(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.TORCH);
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, clearCall(helper, new BlockPos(5, 1, 4), new BlockPos(6, 3, 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED
                            && helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.TORCH),
                    "факел поселения под защитой: расчистка его сохраняет и завершается, а не падает «убрано 0 … "
                            + "защищено: 1» (CLEAR_GROUNDS приёмки «джунгли»); статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void clear_DirtBlockAroundSealedPocket_FailsFastWithUnreachableCount(GameTestHelper helper) {
        prepare(helper);
        for (int x = 3; x <= 5; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 3; z <= 5; z++) {
                    if (x == 4 && z == 4) {
                        continue;
                    }
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                if (x >= 3 && x <= 5 && z >= 3 && z <= 5) {
                    continue;
                }
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, clearCall(helper, new BlockPos(3, 1, 3), new BlockPos(5, 2, 5)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "шестнадцать блоков земли вокруг запечатанной стоянки недобываемы: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("недостижимо: 10"),
                    "причина обязана назвать 10 недостижимых клеток (8 земли кольца и 2 воздуха кармана "
                            + "с той же запечатанной стоянкой), фактически: " + reason);
            int left = 0;
            for (int x = 3; x <= 5; x++) {
                for (int y = 1; y <= 2; y++) {
                    for (int z = 3; z <= 5; z++) {
                        if (x == 4 && z == 4) {
                            continue;
                        }
                        if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) {
                            left++;
                        }
                    }
                }
            }
            helper.assertTrue(
                    left == 16,
                    "запечатанную землю трогать нельзя: в мире обязано остаться 16 блоков, осталось " + left);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIRT) == 0,
                    "добыть из запечатанного блока нечего: в инвентаре обязано быть 0 земли, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIRT));
            helper.assertTrue(
                    run.spiritTicks() <= 200,
                    "отказ по запечатанной стоянке обязан наступить за 200 тиков духа "
                            + "(общая стоянка исключается после трёх неудачных подходов), заняло "
                            + run.spiritTicks());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
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
        SpiritRun run = SpiritRun.launch(
                helper, spirit, clearCall(helper, new BlockPos(4, 1, 4), new BlockPos(6, 1, 5)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "расчистка с пропусками обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 1, 4)).isAir()
                            && helper.getBlockState(new BlockPos(6, 1, 4)).isAir(),
                    "ломаемые блоки стенки обязаны убраться");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, 1, 4)).is(Blocks.BEDROCK),
                    "бедрок неразрушим: обязан остаться, а не ронять задачу");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.COBBLESTONE),
                    "блок рядом с лавой небезопасен: обязан остаться, иначе жидкость затечёт в полость");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "в инвентаре обязан лежать дроп двух убранных блоков, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void clear_BlockUnderFeet_VetoSkipsAndSpiritStays(GameTestHelper helper) {
        prepare(helper);
        helper.assertTrue(
                helper.getBlockState(new BlockPos(1, 0, 1)).is(Blocks.STONE),
                "пол обязан лежать в (1,0,1): без опоры под ногами проверять вето не на чем");
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, clearCall(helper, new BlockPos(1, 0, 1), new BlockPos(1, 0, 1)), 0);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) != RunStatus.SUCCEEDED,
                    "пол под ногами сломан: вето обязано было пропустить клетку, а не дать подрубить опору");
            String reason = spirit.taskFailureReason().orElse("");
            if (spirit.taskStatus().orElse(null) == RunStatus.FAILED
                    && reason.contains("не убран ни один блок")
                    && reason.contains("защищено")) {
                helper.assertTrue(
                        helper.getBlockState(new BlockPos(1, 0, 1)).is(Blocks.STONE),
                        "опора под духом обязана остаться целой: без вето дух подрубил бы пол и упал");
                helper.assertTrue(
                        spirit.blockPosition().getY()
                                == helper.absolutePos(new BlockPos(1, 1, 1)).getY(),
                        "дух обязан остаться наверху, а не провалиться вниз");
                GameTestFixtures.finish(helper);
            }
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void clear_OwnWall_RemovedBySpirit(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 2));
        BlockPlacer placer = new BlockPlacer(spirit);
        for (BlockPos cell : new BlockPos[] {new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)}) {
            BlockPlacer.Begin placed =
                    placer.begin(helper.absolutePos(cell), BlockStates.stateFor("cobblestone"), "cobblestone");
            helper.assertTrue(
                    placed instanceof BlockPlacer.Started,
                    "подготовка обязана поставить свой блок в " + cell + ": без метки духа проверять нечего, ответ "
                            + placed);
        }
        helper.assertTrue(
                PlacedBlocksData.get(helper.getLevel()).isSpiritMarked(helper.absolutePos(new BlockPos(2, 1, 2))),
                "поставленный духом блок обязан нести метку духа, иначе разбор своей постройки не отличить от чужой");
        SpiritRun run = SpiritRun.launch(
                helper, spirit, clearCall(helper, new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "свою постройку дух обязан разобрать, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(2, 1, 2)).isAir()
                            && helper.getBlockState(new BlockPos(3, 1, 2)).isAir(),
                    "оба своих блока обязаны исчезнуть");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void clear_PlayerWall_RefusedAsProtected(GameTestHelper helper) {
        prepare(helper);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 4), "Строитель");
        placeByPlayer(helper, player, new BlockPos(2, 0, 2));
        placeByPlayer(helper, player, new BlockPos(3, 0, 2));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(
                helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.COBBLESTONE)
                        && helper.getBlockState(new BlockPos(3, 1, 2)).is(Blocks.COBBLESTONE),
                "игрок обязан поставить стенку из двух блоков: без неё отказ проверять не на чем");
        helper.assertTrue(
                PlacedBlocksData.get(helper.getLevel()).isMarked(helper.absolutePos(new BlockPos(2, 1, 2))),
                "блок игрока обязан отметиться как постройка игрока, иначе защита не сработает");
        SpiritEntity spirit = spawnWithPick(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, clearCall(helper, new BlockPos(2, 1, 2), new BlockPos(3, 1, 2)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "чужую постройку разбирать нельзя: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("защищено"),
                    "причина обязана называть защиту, фактически: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.COBBLESTONE)
                            && helper.getBlockState(new BlockPos(3, 1, 2)).is(Blocks.COBBLESTONE),
                    "стенка игрока обязана остаться целой");
            GameTestFixtures.finish(helper);
        });
    }
}
