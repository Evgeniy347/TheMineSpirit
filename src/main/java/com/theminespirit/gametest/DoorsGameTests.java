package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DoorsGameTests {
    private static final int NOON = 6000;
    private static final int SUNSET = 12000;
    private static final int PLAYER_IN_OPENING_WAIT_TICKS = 150;
    private static final int EVENING_SETTLE_TICKS = 20;
    private static final int WALL_WEST_X = 3;
    private static final int WALL_EAST_X = 7;
    private static final int WALL_HEIGHT = 3;
    private static final int WALL_DEPTH = 3;
    private static final int WALL_Z = 5;
    private static final int DOOR_ONE_X = 5;
    private static final int DOOR_TWO_X = 7;
    private static final int DOOR_Y = 1;
    private static final int DOOR_TOP_Y = 2;
    private static final int SPIRIT_ONE_X = 5;
    private static final int SPIRIT_TWO_X = 7;
    private static final int SPIRIT_Z = 7;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Z = 8;

    private DoorsGameTests() {}

    @GameTest(template = "arena", batch = "doors_player_left", timeoutTicks = 400)
    public static void door_PlayerGone_ClosesAfterTimeout(GameTestHelper helper) {
        MovementScene.prepare(helper);
        buildWallWithTwoDoors(helper);
        SpiritEntity first = MovementScene.spirit(helper, new BlockPos(SPIRIT_ONE_X, 1, SPIRIT_Z));
        first.setHome(MovementScene.coord(helper, new BlockPos(SPIRIT_ONE_X, 1, SPIRIT_Z)));
        SpiritEntity second = MovementScene.spirit(helper, new BlockPos(SPIRIT_TWO_X, 1, SPIRIT_Z));
        second.setHome(MovementScene.coord(helper, new BlockPos(SPIRIT_TWO_X, 1, SPIRIT_Z)));
        ServerPlayer player = MovementScene.mockPlayer(helper, new BlockPos(PLAYER_X, 1, PLAYER_Z));
        GameTestFixtures.holdDayTime(helper, NOON);
        openDoor(helper, new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    !helper.getBlockState(new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z))
                            .getValue(BlockStateProperties.OPEN),
                    "дверь, открытая игроком и брошенная, обязана быть закрыта духом: иначе ночь найдёт "
                            + "поселение открытым");
            MovementScene.removePlayer(helper, player);
        });
    }

    @GameTest(template = "arena", batch = "doors_player_in_opening", timeoutTicks = 400)
    public static void door_PlayerInOpening_StaysOpen(GameTestHelper helper) {
        MovementScene.prepare(helper);
        buildWallWithTwoDoors(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(SPIRIT_ONE_X, 1, SPIRIT_Z));
        spirit.setHome(MovementScene.coord(helper, new BlockPos(SPIRIT_ONE_X, 1, SPIRIT_Z)));
        ServerPlayer player = MovementScene.mockPlayer(helper, new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z));
        GameTestFixtures.holdDayTime(helper, NOON);
        openDoor(helper, new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    spirit.tickCount > PLAYER_IN_OPENING_WAIT_TICKS,
                    "наблюдение ещё идёт: тик духа " + spirit.tickCount);
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z))
                            .getValue(BlockStateProperties.OPEN),
                    "игрок стоит в проёме: дверь обязана оставаться открытой, иначе дух зажмёт игрока створкой");
            MovementScene.removePlayer(helper, player);
        });
    }

    @GameTest(template = "arena", batch = "doors_evening", timeoutTicks = 400)
    public static void evening_TwoOpenDoors_ClosedByTwoSpirits(GameTestHelper helper) {
        MovementScene.prepare(helper);
        buildWallWithTwoDoors(helper);
        SpiritEntity first = MovementScene.spirit(helper, new BlockPos(SPIRIT_ONE_X, 1, SPIRIT_Z));
        first.setHome(MovementScene.coord(helper, new BlockPos(SPIRIT_ONE_X, 1, SPIRIT_Z)));
        SpiritEntity second = MovementScene.spirit(helper, new BlockPos(SPIRIT_TWO_X, 1, SPIRIT_Z));
        second.setHome(MovementScene.coord(helper, new BlockPos(SPIRIT_TWO_X, 1, SPIRIT_Z)));
        GameTestFixtures.holdDayTime(helper, SUNSET);
        openDoor(helper, new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z));
        openDoor(helper, new BlockPos(DOOR_TWO_X, DOOR_Y, WALL_Z));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    first.tickCount >= EVENING_SETTLE_TICKS || second.tickCount >= EVENING_SETTLE_TICKS,
                    "духи ещё не начали работу: тики " + first.tickCount + " и " + second.tickCount);
            helper.assertTrue(
                    !helper.getBlockState(new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z))
                            .getValue(BlockStateProperties.OPEN),
                    "первая открытая дверь обязана быть закрыта вечером: иначе ночь найдёт поселение открытым");
            helper.assertTrue(
                    !helper.getBlockState(new BlockPos(DOOR_TWO_X, DOOR_Y, WALL_Z))
                            .getValue(BlockStateProperties.OPEN),
                    "вторая открытая дверь обязана быть закрыта другим духом: иначе одна дверь ждёт, "
                            + "пока дух дойдёт до второй");
        });
    }

    private static void buildWallWithTwoDoors(GameTestHelper helper) {
        for (int x = WALL_WEST_X; x <= WALL_EAST_X; x++) {
            for (int y = DOOR_Y; y < DOOR_Y + WALL_HEIGHT; y++) {
                for (int z = WALL_Z - WALL_DEPTH / 2; z <= WALL_Z + WALL_DEPTH / 2; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z), Blocks.AIR);
        helper.setBlock(new BlockPos(DOOR_ONE_X, DOOR_TOP_Y, WALL_Z), Blocks.AIR);
        helper.setBlock(new BlockPos(DOOR_TWO_X, DOOR_Y, WALL_Z), Blocks.AIR);
        helper.setBlock(new BlockPos(DOOR_TWO_X, DOOR_TOP_Y, WALL_Z), Blocks.AIR);
        closeDoor(helper, new BlockPos(DOOR_ONE_X, DOOR_Y, WALL_Z));
        closeDoor(helper, new BlockPos(DOOR_TWO_X, DOOR_Y, WALL_Z));
    }

    private static void openDoor(GameTestHelper helper, BlockPos lower) {
        helper.setBlock(
                lower,
                Blocks.OAK_DOOR
                        .defaultBlockState()
                        .setValue(BlockStateProperties.OPEN, true)
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    }

    private static void closeDoor(GameTestHelper helper, BlockPos lower) {
        helper.setBlock(
                lower,
                Blocks.OAK_DOOR
                        .defaultBlockState()
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    }
}
