package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Разбор движения (решение владельца 20.10): дух в глубине готового большого дома — на втором этаже, в дальней
 * комнате первого этажа, на балконе — обязан дойти до сундука у входа. Сцена отделена от стройки: дом ставится
 * целиком сразу, двери открыты (как во время стройки — двери ставятся последним шагом), дух берёт один предмет.
 * Точка старта выбирается обходом в ширину от сундука: самая дальняя клетка своего вида, до которой тело 1×2
 * доходит шагами вверх на 1 и спусками до 3, — значит, путь к ней по постройке есть.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HouseMovementGameTests {
    // [флак] путь из глубины дома — до 60 шагов по лестнице (≈ 600 тиков), под нагрузкой полного прогона запас 4×.
    private static final int SCENE_TIMEOUT_TICKS = 2400;
    private static final int SETTLE_TICKS = 5;
    private static final int MAX_DROP = 3;
    private static final int UPPER_FLOOR_RISE = 4;
    private static final int BALCONY_RISE = 3;
    private static final int GROUND_FLOOR_RISE = 2;
    // Фасад больших домов — ряд 2 схемы (ряд 1 — крыльцо, ряд 0 — перила балкона): балкон — перед ним, над землёй.
    private static final int FRONT_WALL_Z = 2;
    private static final double CARPET_HEIGHT = 0.1;
    private static final double STEP_HEIGHT = 0.6;
    private static final double JUMP_HEIGHT = 1.125;
    private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private enum Spot {
        UPPER_FLOOR,
        GROUND_FAR,
        BALCONY
    }

    private HouseMovementGameTests() {}

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_MansionUpperFloor_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "player_house", 4, Spot.UPPER_FLOOR);
    }

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_MansionGroundFar_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "player_house", 4, Spot.GROUND_FAR);
    }

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_MansionBalcony_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "player_house", 4, Spot.BALCONY);
    }

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_ManorUpperFloor_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "family_house", 4, Spot.UPPER_FLOOR);
    }

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_ManorGroundFar_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "family_house", 4, Spot.GROUND_FAR);
    }

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_ManorBalcony_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "family_house", 4, Spot.BALCONY);
    }

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_PlayerHouse3UpperFloor_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "player_house", 3, Spot.UPPER_FLOOR);
    }

    @SceneStep(template = "big_arena", timeoutTicks = SCENE_TIMEOUT_TICKS)
    public static void move_PlayerHouse3Balcony_ReachesEntranceChest(GameTestHelper helper) {
        scene(helper, "player_house", 3, Spot.BALCONY);
    }

    private static void scene(GameTestHelper helper, String building, int level, Spot spot) {
        BuildRotation rotation = BuildRotation.NONE;
        String scheme = DemoBuildings.require(building).level(level).scheme();
        Schematic schematic = SchematicResources.load(scheme);
        int clearHeight = DemoAuditGameTests.clearHeight(schematic);
        int ring = DemoAuditGameTests.SUPPLY_RING;
        int floorSize = DemoAuditGameTests.floorSize(schematic) + 2 * ring;
        GameTestFixtures.reset(helper, clearHeight);
        DemoAuditGameTests.floor(helper, floorSize, clearHeight);
        BlockPos anchor = helper.absolutePos(new BlockPos(ring, 1, ring));
        BlockCoord origin = new BlockCoord(anchor.getX(), anchor.getY(), anchor.getZ());
        placeWhole(helper.getLevel(), schematic, origin, rotation);
        DemoAuditGameTests.SupplyPoint supply = DemoAuditGameTests.supplyPoint(scheme, schematic, rotation, ring);
        BlockPos chest = chest(helper, supply);
        BlockPos front = helper.absolutePos(
                supply.base().relative(supply.partner().getOpposite(), DemoAuditGameTests.SUPPLY_SIDE_OFFSET - 1));
        int sizeX = rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ());
        int sizeZ = rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ());
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        Area area = new Area(anchor, sizeX, sizeZ, schematic.sizeY(), floorSize);
        BlockPos start = farthest(helper, area, front, spot, parents);
        BlockPos relative = start.subtract(helper.absolutePos(BlockPos.ZERO));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.setDemo(true);
        String broken = brokenStep(helper, spirit, start, parents);
        spirit.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        String label = "'" + scheme + "' " + spot + " с " + relative.toShortString() + " к сундуку "
                + supply.base().toShortString();
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "withdraw",
                        Map.of(
                                "container",
                                chest.getX() + "," + chest.getY() + "," + chest.getZ(),
                                "item",
                                "minecraft:oak_planks",
                                "count",
                                1,
                                "radius",
                                8)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Дух " + label + " обязан дойти и взять доску: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", стоит на "
                            + spirit.blockPosition()
                                    .subtract(helper.absolutePos(BlockPos.ZERO))
                                    .toShortString()
                            + "; " + broken);
            if (helper.getBlockEntity(supply.base()) instanceof Container container) {
                container.clearContent();
            }
            DemoAuditGameTests.clearArea(helper, floorSize, clearHeight);
            GameTestFixtures.finish(helper, clearHeight);
        });
    }

    private static BlockPos chest(GameTestHelper helper, DemoAuditGameTests.SupplyPoint supply) {
        BlockState left = Blocks.CHEST
                .defaultBlockState()
                .setValue(ChestBlock.FACING, supply.partner().getCounterClockWise())
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        helper.setBlock(supply.base().relative(supply.partner()), left.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(supply.base(), left);
        ((Container) helper.getBlockEntity(supply.base())).setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
        return helper.absolutePos(supply.base());
    }

    // Готовый дом целиком: без обновлений соседей (факел у ещё не поставленной стены не осыпается), затем формы
    // ступенек, панелей и заборов по соседям. Двери открыты: так проём во время стройки, до последнего шага.
    static void placeWhole(ServerLevel level, Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        Rotation mc = switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        };
        List<BlockPos> placed = new ArrayList<>();
        for (Schematic.PlacedEntry entry : schematic.entries(origin, rotation)) {
            BlockPos pos =
                    new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            BlockState state =
                    BlockStates.stateFor(entry.block(), entry.state()).rotate(level, pos, mc);
            if (state.getBlock() instanceof DoorBlock) {
                state = state.setValue(DoorBlock.OPEN, true);
            }
            level.setBlock(pos, state, SILENT);
            placed.add(pos);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(
                        pos.above(),
                        state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                        SILENT);
                placed.add(pos.above());
            }
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                BlockPos head = pos.relative(state.getValue(BedBlock.FACING));
                level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), SILENT);
                placed.add(head);
            }
        }
        for (BlockPos pos : placed) {
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), SILENT);
        }
    }

    private record Area(BlockPos anchor, int sizeX, int sizeZ, int sizeY, int floorSize) {}

    private static BlockPos farthest(
            GameTestHelper helper, Area area, BlockPos front, Spot spot, Map<BlockPos, BlockPos> parents) {
        BlockPos anchor = area.anchor();
        int sizeX = area.sizeX();
        int sizeZ = area.sizeZ();
        int sizeY = area.sizeY();
        int floorSize = area.floorSize();
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        distance.put(front, 0);
        queue.add(front);
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -MAX_DROP; dy--) {
                    BlockPos next = cell.relative(direction).above(dy);
                    if (distance.containsKey(next)
                            || next.getX() < min.getX()
                            || next.getZ() < min.getZ()
                            || next.getX() >= min.getX() + floorSize
                            || next.getZ() >= min.getZ() + floorSize
                            || next.getY() <= min.getY()
                            || !step(level, cell, next)) {
                        continue;
                    }
                    distance.put(next, distance.get(cell) + 1);
                    parents.put(next, cell);
                    queue.add(next);
                    break;
                }
            }
        }
        int ground = front.getY();
        BlockPos best = null;
        int bestDistance = -1;
        for (Map.Entry<BlockPos, Integer> entry : distance.entrySet()) {
            BlockPos cell = entry.getKey();
            boolean inside = cell.getX() >= anchor.getX()
                    && cell.getZ() >= anchor.getZ()
                    && cell.getX() < anchor.getX() + sizeX
                    && cell.getZ() < anchor.getZ() + sizeZ;
            if (!inside) {
                continue;
            }
            boolean covered = covered(level, cell, anchor.getY() + sizeY);
            boolean fits = switch (spot) {
                case UPPER_FLOOR -> covered && cell.getY() >= ground + UPPER_FLOOR_RISE;
                case GROUND_FAR -> covered && cell.getY() <= ground + GROUND_FLOOR_RISE;
                case BALCONY -> cell.getZ() < anchor.getZ() + FRONT_WALL_Z && cell.getY() >= ground + BALCONY_RISE;
            };
            if (fits
                    && (entry.getValue() > bestDistance
                            || (entry.getValue() == bestDistance && cell.compareTo(best) < 0))) {
                best = cell;
                bestDistance = entry.getValue();
            }
        }
        helper.assertTrue(best != null, "В постройке нет клетки вида " + spot + ", достижимой обходом от входа");
        return best;
    }

    // Маршрут обхода от старта к входу: шаг, который поиск пути игры не проходит, или первый отрезок от старта, до
    // конца которого путь игры не доходит (так нашлась дальность пути: 32 шага, а не 32 блока по прямой).
    private static String brokenStep(
            GameTestHelper helper, SpiritEntity spirit, BlockPos start, Map<BlockPos, BlockPos> parents) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        BlockPos cell = start;
        int steps = 0;
        while (parents.containsKey(cell)) {
            BlockPos next = parents.get(cell);
            Path path = pathFrom(spirit, cell, next);
            if (path == null || !path.canReach()) {
                return "шаг " + steps + " маршрута " + cell.subtract(zero).toShortString() + " -> "
                        + next.subtract(zero).toShortString() + " поиск пути игры не проходит: " + nodes(path, zero);
            }
            cell = next;
            steps++;
        }
        BlockPos probe = start;
        for (int k = 1; parents.containsKey(probe); k++) {
            probe = parents.get(probe);
            Path part = pathFrom(spirit, start, probe);
            if (part == null || !part.canReach()) {
                return "от старта не дойти до шага " + k + " из " + steps + " "
                        + probe.subtract(zero).toShortString() + ": " + nodes(part, zero);
            }
        }
        return "маршрут из " + steps + " шагов поиск пути игры проходит целиком";
    }

    private static @Nullable Path pathFrom(SpiritEntity spirit, BlockPos from, BlockPos to) {
        spirit.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        spirit.setOnGround(true);
        return spirit.getNavigation().createPath(Set.of(to), 0);
    }

    private static String nodes(@Nullable Path path, BlockPos zero) {
        if (path == null) {
            return "нет";
        }
        StringBuilder text = new StringBuilder();
        for (int i = Math.max(0, path.getNodeCount() - 6); i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            text.append(node.asBlockPos().subtract(zero).toShortString())
                    .append(' ')
                    .append(node.type)
                    .append(" | ");
        }
        return text.toString();
    }

    private static boolean covered(ServerLevel level, BlockPos cell, int top) {
        for (int y = cell.getY() + 2; y <= top; y++) {
            if (!level.getBlockState(new BlockPos(cell.getX(), y, cell.getZ())).isAir()) {
                return true;
            }
        }
        return false;
    }

    // Шаг в обе стороны: клетка, куда тело спрыгнуло, но откуда не выбраться (полублок у полного блока — подъём
    // 1,5), — ловушка, а не стоянка; старт из неё мерил бы схему, а не движение.
    private static boolean step(ServerLevel level, BlockPos from, BlockPos to) {
        return oneWay(level, from, to) && oneWay(level, to, from);
    }

    private static boolean oneWay(ServerLevel level, BlockPos from, BlockPos to) {
        if (!stand(level, to) || !stand(level, from)) {
            return false;
        }
        double rise = floorTop(level, to) - floorTop(level, from);
        if (rise > JUMP_HEIGHT) {
            return false;
        }
        if (rise > STEP_HEIGHT && !passable(level, from.above(2))) {
            return false;
        }
        for (int y = from.getY() + 1; y > to.getY(); y--) {
            if (!passable(level, new BlockPos(to.getX(), y, to.getZ()))) {
                return false;
            }
        }
        return true;
    }

    private static double floorTop(ServerLevel level, BlockPos feet) {
        BlockState state = level.getBlockState(feet);
        VoxelShape own = state.getCollisionShape(level, feet);
        if (!own.isEmpty() && !(state.getBlock() instanceof DoorBlock)) {
            return feet.getY() + own.max(Direction.Axis.Y);
        }
        return feet.getY()
                - 1
                + level.getBlockState(feet.below())
                        .getCollisionShape(level, feet.below())
                        .max(Direction.Axis.Y);
    }

    private static boolean stand(ServerLevel level, BlockPos feet) {
        if (!passable(level, feet) || !passable(level, feet.above())) {
            return false;
        }
        BlockState floor = level.getBlockState(feet.below());
        VoxelShape shape = floor.getCollisionShape(level, feet.below());
        return !passable(level, feet.below())
                && shape.max(Direction.Axis.Y) <= 1.0
                && !(floor.getBlock() instanceof DoorBlock)
                && !CampfireBlock.isLitCampfire(floor);
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        // Ковёр (1/16 блока) — пол под ногами, а не преграда: ноги стоят в его клетке.
        VoxelShape shape = state.getCollisionShape(level, pos);
        return state.getBlock() instanceof DoorBlock || shape.isEmpty() || shape.max(Direction.Axis.Y) <= CARPET_HEIGHT;
    }
}
