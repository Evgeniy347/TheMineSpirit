package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import com.theminespirit.neoforge.policy.PolicyConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyProtectionGameTests {
    private static final BlockPos SPIRIT_AT = new BlockPos(4, 1, 4);
    private static final int BREAKS = 200;
    private static final int RETURN_LIMIT_TICKS = 200;

    private PolicyProtectionGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static boolean isProtected(BlockBreaker.Begin begin, PolicyId policy) {
        return begin instanceof BlockBreaker.Protected protectedBlock && protectedBlock.policy() == policy;
    }

    private static String tagSelector(String tag) {
        return "@e[type=" + ModIdentity.MOD_ID + ":spirit,tag=" + tag + "]";
    }

    // Селектор по расстоянию видит и духов соседних тестов пачки (структуры стоят в 8 клетках друг от друга),
    // и команда ложится на чужого духа; тег уникален на запуск и выбирает только духа своего теста.
    private static String freshTag(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    private static SpiritEntity spawnTagged(GameTestHelper helper, BlockPos relative, String tag) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.addTag(tag);
        return spirit;
    }

    private static void placeByPlayer(GameTestHelper helper, ServerPlayer player, BlockPos support, Block block) {
        BlockPos absolute = helper.absolutePos(support);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block.asItem()));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
    }

    private static ScriptCall gotoCall(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new ScriptCall("goto", Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ()));
    }

    private static WorkZone zoneAround(GameTestHelper helper, BlockPos relativeCenter, int radius) {
        BlockPos absolute = helper.absolutePos(relativeCenter);
        return new WorkZone(new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()), radius);
    }

    @GameTest(template = "arena", batch = "protect_deny", timeoutTicks = 40)
    public static void breaker_DenyListBlocks_RefusedAsProtectedAndStayIntact(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
        BlockBreaker breaker = new BlockBreaker(spirit);
        BlockPos target = new BlockPos(5, 1, 4);
        for (Block block : List.of(Blocks.BEACON, Blocks.REINFORCED_DEEPSLATE, Blocks.CHEST)) {
            helper.setBlock(target, block);
            BlockBreaker.Begin begin = breaker.begin(helper.absolutePos(target));
            helper.assertTrue(isProtected(begin, PolicyId.PROTECTED_BLOCKS), "блок " + block + " обязан быть защищён: ответ " + begin);
            helper.assertTrue(helper.getBlockState(target).is(block), "защищённый блок " + block + " обязан остаться целым");
        }
        helper.setBlock(target, Blocks.END_PORTAL_FRAME);
        BlockBreaker.Begin frame = breaker.begin(helper.absolutePos(target));
        helper.assertTrue(!(frame instanceof BlockBreaker.Started) && helper.getBlockState(target).is(Blocks.END_PORTAL_FRAME),
                "рамка портала Края неразрушима и защищена: ломка не начата, блок цел, ответ " + frame);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_tended", timeoutTicks = 40)
    public static void breaker_WheatInFarmlandMarkedByPlayer_TendedBreakAllowedUntendedRefused(GameTestHelper helper) {
        prepare(helper);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(x, 0, z, Blocks.FARMLAND);
                helper.setBlock(x, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
            }
        }
        BlockPos crop = new BlockPos(4, 1, 4);
        BlockPos absolute = helper.absolutePos(crop);
        PlacedBlocksData.get(helper.getLevel()).mark(absolute);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 2));
        BlockBreaker breaker = new BlockBreaker(spirit);

        BlockBreaker.Begin untended = breaker.begin(absolute);
        helper.assertTrue(isProtected(untended, PolicyId.PLAYER_BUILDINGS),
                "пшеница, поставленная игроком в поле: обычная ломка обязана быть отвергнута как постройка игрока, ответ " + untended);
        BlockBreaker.Begin tended = breaker.begin(absolute, true);
        helper.assertTrue(tended instanceof BlockBreaker.Started,
                "сбор урожая — задача фермы: поле игрока (отметка, соседи-грядки) не повод отказа, ответ " + tended);
        PlacedBlocksData.get(helper.getLevel()).unmark(absolute);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_player_log", timeoutTicks = 400)
    public static void gather_LogPlacedByPlayerAndNaturalLog_SkipsPlacedAndReportsIt(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(6, 1, 2, Blocks.OAK_LOG);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(2, 1, 6), "gt_protect_log_player");
        placeByPlayer(helper, player, new BlockPos(6, 0, 6), Blocks.OAK_LOG);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 6)).is(Blocks.OAK_LOG),
                "игрок обязан поставить бревно в (6,1,6): без этого проверка ничего не доказывает");
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("gather", Map.of("block", "minecraft:oak_log", "count", 2, "radius", 8)), 2);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "бревна всего два, одно чужое: gather обязан завершиться провалом с итогом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 2)).isAir(), "природное бревно обязано быть срублено");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 6)).is(Blocks.OAK_LOG), "бревно, поставленное игроком, обязано остаться");
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("добыто 1 из 2") && reason.contains("пропущено защищённых: 1"),
                    "итог называет добытое и число пропущенных защищённых блоков: «" + reason + "»");
            GameTestFixtures.reset(helper);
        });
    }

    private static void surround(GameTestHelper helper, BlockPos center, int count, Block block) {
        int placed = 0;
        for (int dx = -1; dx <= 1 && placed < count; dx++) {
            for (int dy = 0; dy <= 1 && placed < count; dy++) {
                for (int dz = -1; dz <= 1 && placed < count; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        helper.setBlock(center.offset(dx, dy, dz), block);
                        placed++;
                    }
                }
            }
        }
    }

    @GameTest(template = "arena", batch = "protect_buildings", timeoutTicks = 40)
    public static void breaker_LogsAroundThresholdAndInHouse_OnlyBuildingLogsAreProtected(GameTestHelper helper) {
        prepare(helper);
        BlockPos five = new BlockPos(1, 1, 1);
        BlockPos six = new BlockPos(7, 1, 1);
        BlockPos forest = new BlockPos(1, 1, 7);
        BlockPos house = new BlockPos(7, 1, 7);
        for (BlockPos log : List.of(five, six, forest, house)) {
            helper.setBlock(log, Blocks.OAK_LOG);
        }
        surround(helper, five, 5, Blocks.OAK_PLANKS);
        surround(helper, six, 6, Blocks.OAK_PLANKS);
        surround(helper, house, 8, Blocks.OAK_PLANKS);
        helper.setBlock(house.offset(1, 1, 1), Blocks.GLASS);
        helper.setBlock(house.offset(-1, 1, 1), Blocks.GLASS);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
        BlockBreaker breaker = new BlockBreaker(spirit);

        helper.assertTrue(breaker.begin(helper.absolutePos(five)) instanceof BlockBreaker.Started, "вокруг бревна 5 искусственных блоков: порог 6 не достигнут, рубить можно");
        helper.assertTrue(isProtected(breaker.begin(helper.absolutePos(six)), PolicyId.PLAYER_BUILDINGS), "вокруг бревна 6 искусственных блоков: это здание, рубить нельзя");
        helper.assertTrue(breaker.begin(helper.absolutePos(forest)) instanceof BlockBreaker.Started, "такое же бревно в лесу рубится");
        helper.assertTrue(isProtected(breaker.begin(helper.absolutePos(house)), PolicyId.PLAYER_BUILDINGS), "бревенчатая колонна дома из досок и стекла защищена");
        breaker.cancel();
        helper.assertTrue(helper.getBlockState(house).is(Blocks.OAK_LOG) && helper.getBlockState(six).is(Blocks.OAK_LOG), "защищённые колонны обязаны остаться целыми");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_zone", timeoutTicks = 40)
    public static void protectedZone_FromConfig_CobbleInsideIsKeptOutsideIsBreakableAndPlacingIsRefused(GameTestHelper helper) {
        prepare(helper);
        BlockPos inside = new BlockPos(5, 1, 4);
        BlockPos outside = new BlockPos(3, 1, 4);
        helper.setBlock(inside, Blocks.COBBLESTONE);
        helper.setBlock(outside, Blocks.COBBLESTONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
        BlockPos from = helper.absolutePos(new BlockPos(5, 0, 3));
        BlockPos to = helper.absolutePos(new BlockPos(6, 3, 5));
        String dimension = helper.getLevel().dimension().location().toString();
        try {
            PolicyConfig.setProtectedZones(List.of(dimension + " " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                    + to.getX() + " " + to.getY() + " " + to.getZ()));
            BlockBreaker breaker = new BlockBreaker(spirit);

            helper.assertTrue(isProtected(breaker.begin(helper.absolutePos(inside)), PolicyId.PROTECTED_ZONE), "булыжник в защищённой зоне ломать нельзя");
            helper.assertTrue(breaker.begin(helper.absolutePos(outside)) instanceof BlockBreaker.Started, "булыжник вне зоны ломается");
            helper.assertTrue(spirit.protectionGate().checkPlace(helper.absolutePos(new BlockPos(6, 1, 4))) instanceof BlockProtection.Refused,
                    "постановка блока в защищённой зоне отвергается");
            helper.assertTrue(spirit.protectionGate().checkPlace(helper.absolutePos(new BlockPos(2, 1, 4))) instanceof BlockProtection.Allowed,
                    "постановка блока вне зоны разрешена");
            breaker.cancel();
        } finally {
            PolicyConfig.setProtectedZones(List.of());
        }
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_zone_goto", timeoutTicks = 100)
    public static void workZone_GotoTargetOutside_FailsWithReason(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setWorkZone(zoneAround(helper, new BlockPos(1, 1, 1), 2));
        SpiritRun run = SpiritRun.launch(helper, spirit, gotoCall(helper, new BlockPos(8, 1, 8)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED, "цель вне рабочей зоны: goto обязан провалиться");
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.startsWith("вне рабочей зоны"), "причина называет рабочую зону: «" + reason + "»");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "protect_zone_return", timeoutTicks = 300)
    public static void workZone_SpiritTeleportedFarOut_ReturnsWithinMargin(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        WorkZone zone = zoneAround(helper, new BlockPos(1, 1, 1), 2);
        spirit.setWorkZone(zone);
        int[] teleportedAt = {-1};
        helper.onEachTick(() -> {
            if (teleportedAt[0] < 0 && spirit.tickCount >= 3) {
                BlockPos far = helper.absolutePos(new BlockPos(8, 1, 1));
                spirit.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
                teleportedAt[0] = spirit.tickCount;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(teleportedAt[0] >= 0, "телепорт ещё не выполнен");
            BlockPos cell = spirit.blockPosition();
            helper.assertTrue(zone.excess(new BlockCoord(cell.getX(), cell.getY(), cell.getZ())) <= 3,
                    "дух за зоной на " + zone.excess(new BlockCoord(cell.getX(), cell.getY(), cell.getZ())) + " блоков: обязан вернуться в допуск 3");
            helper.assertTrue(spirit.tickCount - teleportedAt[0] <= RETURN_LIMIT_TICKS, "возврат обязан уложиться в " + RETURN_LIMIT_TICKS + " тиков");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "protect_zone_follow", timeoutTicks = 300)
    public static void workZone_FollowedPlayerLeavesZone_TaskFailsAndSpiritStays(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        WorkZone zone = zoneAround(helper, new BlockPos(1, 1, 1), 2);
        spirit.setWorkZone(zone);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 2), "gt_protect_zone_follow_player");
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("follow", Map.of("target", player.getName().getString(), "radius", 2)), 2);
        boolean[] left = {false};
        helper.onEachTick(() -> {
            if (!left[0] && run.spiritTicks() >= 20) {
                BlockPos far = helper.absolutePos(new BlockPos(8, 1, 1));
                player.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
                left[0] = true;
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(left[0], "игрок ещё не вышел из зоны");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED, "игрок вышел из зоны: follow обязан провалиться");
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.startsWith("цель вышла за границы рабочей зоны"), "причина называет выход цели за зону: «" + reason + "»");
            BlockPos cell = spirit.blockPosition();
            helper.assertTrue(zone.excess(new BlockCoord(cell.getX(), cell.getY(), cell.getZ())) <= 3, "дух обязан остаться в зоне");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "protect_persist", timeoutTicks = 40)
    public static void persistence_WorkZoneAndPlacedMarks_SurviveSaveAndLoad(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
        WorkZone zone = zoneAround(helper, new BlockPos(2, 1, 2), 7);
        spirit.setWorkZone(zone);
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.assertTrue(zone.equals(reloaded.workZone().orElse(null)), "рабочая зона обязана пережить сохранение духа: было " + zone + ", стало " + reloaded.workZone());
        SpiritEntity withoutZone = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(6, 1, 6));
        CompoundTag plain = new CompoundTag();
        withoutZone.saveWithoutId(plain);
        SpiritEntity plainReloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        plainReloaded.load(plain);
        helper.assertTrue(plainReloaded.workZone().isEmpty(), "дух без зоны остаётся без зоны после перезагрузки");

        PlacedBlocksData data = new PlacedBlocksData();
        BlockPos first = new BlockPos(-17, 64, 30);
        BlockPos second = new BlockPos(5, -20, -3);
        data.mark(first);
        data.mark(second);
        PlacedBlocksData restored = PlacedBlocksData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.isMarked(first) && restored.isMarked(second) && restored.size() == 2,
                "отметки блоков игроков обязаны пережить сохранение мира: найдено " + restored.size());
        helper.assertTrue(!restored.isMarked(first.above()), "соседняя позиция не должна считаться отмеченной");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_dig_down", timeoutTicks = 40)
    public static void breaker_BlockUnderFeetAbovePitOfThree_RefusedAsDropAndSpiritStays(GameTestHelper helper) {
        prepare(helper);
        BlockPos pillar = new BlockPos(4, 3, 4);
        helper.setBlock(pillar, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 4, 4));
        BlockBreaker breaker = new BlockBreaker(spirit);

        BlockBreaker.Begin begin = breaker.begin(helper.absolutePos(pillar));

        helper.assertTrue(isProtected(begin, PolicyId.DROP), "блок под ногами над ямой в 3 блока ломать нельзя: ответ " + begin);
        helper.assertTrue(helper.getBlockState(pillar).is(Blocks.STONE) && spirit.getY() >= helper.absoluteVec(new Vec3(0, 4, 0)).y - 0.01,
                "блок цел и дух стоит на нём");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_dig_floor", timeoutTicks = 40)
    public static void breaker_BlockUnderFeetOverSolidFloor_IsAllowed(GameTestHelper helper) {
        prepare(helper);
        BlockPos dirt = new BlockPos(4, 1, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 2, 4));
        BlockBreaker breaker = new BlockBreaker(spirit);

        helper.assertTrue(breaker.begin(helper.absolutePos(dirt)) instanceof BlockBreaker.Started,
                "блок под ногами с полом сразу под ним (падение на 1) ломать можно");
        breaker.cancel();
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_load", timeoutTicks = 100)
    public static void load_TwoHundredBreaks_ReadTwentySixNeighboursEach(GameTestHelper helper) {
        prepare(helper);
        BlockPos dirt = new BlockPos(5, 1, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
        BlockBreaker breaker = new BlockBreaker(spirit);

        for (int i = 0; i < BREAKS; i++) {
            helper.assertTrue(breaker.begin(helper.absolutePos(dirt)) instanceof BlockBreaker.Started, "земля рядом с духом обязана ломаться, итерация " + i);
            breaker.cancel();
        }

        helper.assertTrue(spirit.protectionGate().checks() == 200, "проверок ломки должно быть 200, было " + spirit.protectionGate().checks());
        helper.assertTrue(spirit.protectionGate().neighbourReads() == 200 * 26,
                "проверка ломки читает ровно 26 соседей: 200 ломок = 5200 чтений, было " + spirit.protectionGate().neighbourReads());
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "protect_command", timeoutTicks = 40)
    public static void zoneCommand_SetThenClear_ChangesTheSpiritWorkZone(GameTestHelper helper) throws CommandSyntaxException {
        prepare(helper);
        String tag = freshTag("zone");
        SpiritEntity spirit = spawnTagged(helper, SPIRIT_AT, tag);
        BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPosition(helper.absoluteVec(Vec3.atCenterOf(SPIRIT_AT)));
        String selector = tagSelector(tag);

        int set = dispatcher.execute("spirit zone " + selector + " " + center.getX() + " " + center.getY() + " " + center.getZ() + " 5", source);

        helper.assertTrue(set == 1, "команда zone обязана затронуть одного духа, затронуто " + set);
        helper.assertTrue(new WorkZone(new BlockCoord(center.getX(), center.getY(), center.getZ()), 5).equals(spirit.workZone().orElse(null)),
                "зона духа обязана совпасть с заданной командой: " + spirit.workZone());
        int cleared = dispatcher.execute("spirit zone " + selector + " clear", source);
        helper.assertTrue(cleared == 1 && spirit.workZone().isEmpty(), "zone clear обязана снять зону: " + spirit.workZone());
        GameTestFixtures.reset(helper);
        helper.succeed();
    }
}
