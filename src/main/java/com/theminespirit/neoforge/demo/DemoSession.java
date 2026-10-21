package com.theminespirit.neoforge.demo;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Одна демо-стройка: убирает площадку, ставит сундук с материалами, ведёт духа по уровням и убирает его,
 * когда всё готово (или называет причину отказа). Уровни идут по очереди: загон сначала младший, потом старший.
 */
final class DemoSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEMO_WAIT_TICKS = 100;
    private static final int WITHDRAW_RADIUS = 8;
    private static final int MAX_ROUNDS = 20;
    // mc: уровень тикета = 33 - расстояние; сущности тикают при уровне не выше 31.
    private static final int TICKET_DISTANCE = 2;
    private static final TicketType<ChunkPos> DEMO_TICKET =
            TicketType.create("theminespirit_demo", Comparator.comparingLong(ChunkPos::toLong));
    private static final String NO_MATERIAL = "не хватило материала";
    private static final String INVENTORY_FULL = "инвентарь духа полон";

    private final ServerLevel level;
    private final SpiritEntity spirit;
    private final List<DemoPlan> plans;
    private final UUID owner;
    private int index;
    private int round;
    private List<BlockPos> chests = List.of();
    private final List<ChunkPos> held = new ArrayList<>();

    DemoSession(ServerLevel level, SpiritEntity spirit, List<DemoPlan> plans, UUID owner) {
        this.level = level;
        this.spirit = spirit;
        this.plans = List.copyOf(plans);
        this.owner = owner;
    }

    SpiritEntity spirit() {
        return spirit;
    }

    UUID owner() {
        return owner;
    }

    void abort() {
        releaseChunks();
        spirit.cancelTask();
        spirit.stripEquipment();
    }

    void begin() {
        holdChunks();
        prepare(plans.get(0));
        spirit.startTask(node(0, withdrawals(plans.get(0))));
    }

    boolean tick() {
        if (spirit.isRemoved()) {
            abort();
            return false;
        }
        RunStatus status = spirit.taskStatus().orElse(null);
        if (status == null || status == RunStatus.RUNNING) {
            return true;
        }
        if (status == RunStatus.SUCCEEDED) {
            placeWithoutItem(plans.get(index));
            if (index + 1 < plans.size()) {
                index++;
                prepare(plans.get(index));
                spirit.startTask(node(index, withdrawals(plans.get(index))));
                return true;
            }
            releaseChunks();
            spirit.discard();
            return false;
        }
        String reason = spirit.taskFailureReason().orElse("стройка не удалась");
        if (reason.contains(NO_MATERIAL) && round < MAX_ROUNDS) {
            List<Withdrawal> next = withdrawals(plans.get(index));
            if (!next.isEmpty()) {
                round++;
                spirit.startTask(node(index, next));
                return true;
            }
        }
        // Заход сессии считает свободные слоты до похода к сундуку; по дороге дух подбирает выпавшее, и последний
        // слот занят (игра 21.10: «инвентарь духа полон: перенесено 0 из 64»). Это не конец стройки: стройка сама
        // берёт порции и в демо выбрасывает лишнее (BuildSupplyTrip) — продолжить её без захода сессии.
        if (reason.contains(INVENTORY_FULL) && round < MAX_ROUNDS) {
            round++;
            LOGGER.info(
                    "Демо-стройка: дух {} — заход сессии упёрся в полный инвентарь ({}), стройка продолжает сама",
                    spirit.getStringUUID(),
                    reason);
            spirit.startTask(node(index, List.of()));
            return true;
        }
        announce(reason);
        abort();
        spirit.discard();
        return false;
    }

    private TaskNode node(int planIndex, List<Withdrawal> withdrawals) {
        DemoPlan plan = plans.get(planIndex);
        List<TaskNode> steps = new ArrayList<>();
        if (planIndex == 0 && round == 0) {
            steps.add(new ScriptCall("say", params("text", "Начинаю стройку: " + plan.name())));
        }
        for (Withdrawal withdrawal : withdrawals) {
            steps.add(new ScriptCall("withdraw", withdrawParams(withdrawal)));
        }
        steps.add(new ScriptCall("build_schematic", buildParams(plan)));
        if (planIndex == plans.size() - 1) {
            steps.add(new ScriptCall("say", params("text", "Готово: " + plan.name())));
            steps.add(new ScriptCall("wait", params("ticks", DEMO_WAIT_TICKS)));
        }
        return new SequenceNode(steps);
    }

    // Игра 21.10: на витрине city 5 владелец стоял на другом конце улицы — чанки дальних участков выгружались, дух
    // уходил в чанк (isRemoved), и сессия кончалась без стройки. Сессия держит чанки своих площадок до конца.
    private void holdChunks() {
        for (DemoPlan plan : plans) {
            var platform = plan.placement().platform();
            for (int cx = platform.minX() >> 4; cx <= platform.maxX() >> 4; cx++) {
                for (int cz = platform.minZ() >> 4; cz <= platform.maxZ() >> 4; cz++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    if (!held.contains(pos)) {
                        held.add(pos);
                        level.getChunkSource().addRegionTicket(DEMO_TICKET, pos, TICKET_DISTANCE, pos, true);
                    }
                }
            }
        }
        LOGGER.info("Демо-стройка: дух {} держит чанков {} на время сессии", spirit.getStringUUID(), held.size());
    }

    private void releaseChunks() {
        for (ChunkPos pos : held) {
            level.getChunkSource().removeRegionTicket(DEMO_TICKET, pos, TICKET_DISTANCE, pos, true);
        }
        held.clear();
    }

    private static Map<String, Object> params(String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);
        return params;
    }

    private static Map<String, Object> withdrawParams(Withdrawal withdrawal) {
        Map<String, Object> params = new HashMap<>();
        BlockPos chest = withdrawal.chest();
        params.put("container", chest.getX() + "," + chest.getY() + "," + chest.getZ());
        params.put("item", withdrawal.item());
        params.put("count", withdrawal.count());
        params.put("radius", WITHDRAW_RADIUS);
        return params;
    }

    private static Map<String, Object> buildParams(DemoPlan plan) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", plan.scheme());
        params.put("x", plan.placement().origin().x());
        params.put("y", plan.placement().origin().y());
        params.put("z", plan.placement().origin().z());
        params.put("rotation", plan.placement().rotation().id());
        return params;
    }

    private void prepare(DemoPlan plan) {
        clearVolume(plan);
        flattenFloor(plan);
        round = 0;
        chests = fillChests(plan);
    }

    private void clearVolume(DemoPlan plan) {
        for (var cell : plan.placement().platform().cells()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private void flattenFloor(DemoPlan plan) {
        for (var cell : plan.placement().floorBox().cells()) {
            level.setBlock(
                    new BlockPos(cell.x(), cell.y(), cell.z()), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private void placeWithoutItem(DemoPlan plan) {
        Rotation rotation = mcRotation(plan.placement().rotation());
        for (DemoPlan.Placement placement : plan.preplaced()) {
            BlockPos pos = new BlockPos(
                    placement.pos().x(), placement.pos().y(), placement.pos().z());
            BlockState state = BlockStates.stateFor(placement.block()).rotate(level, pos, rotation);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    private record Withdrawal(BlockPos chest, String item, int count) {}

    // Решение владельца 20.10: материалы — в двойных сундуках (столбиком пар), сколько нужно; одиночный сундук —
    // только если рядом с точкой сундука нет места под вторую половину.
    private List<BlockPos> fillChests(DemoPlan plan) {
        BlockCoord base = plan.placement().chest();
        Direction partner = partnerSide(plan);
        placeSign(plan);
        List<BlockPos> filled = new ArrayList<>();
        ChestBlockEntity chest = null;
        int slot = 0;
        for (Map.Entry<String, Integer> material : plan.materials().entrySet()) {
            Item item = BlockItems.itemFor(material.getKey());
            int left = material.getValue();
            while (left > 0) {
                if (chest == null || slot >= chest.getContainerSize()) {
                    chest = nextChest(plan, base, partner, filled);
                    slot = 0;
                }
                int take = Math.min(left, item.getDefaultMaxStackSize());
                chest.setItem(slot++, new ItemStack(item, take));
                left -= take;
            }
        }
        if (filled.isEmpty()) {
            nextChest(plan, base, partner, filled);
        }
        return List.copyOf(filled);
    }

    // Половины идут парами: нижняя пара — у точки сундука, следующая — над ней. Крышка смотрит от постройки.
    private ChestBlockEntity nextChest(
            DemoPlan plan, BlockCoord base, @Nullable Direction partner, List<BlockPos> filled) {
        int index = filled.size();
        BlockPos main = new BlockPos(base.x(), base.y() + (partner == null ? index : index / 2), base.z());
        DemoPlacement.Look look = plan.placement().chestFacing();
        Direction facing = Direction.fromDelta(look.dx(), 0, look.dz());
        if (facing == null || facing.getAxis().isVertical()) {
            facing = Direction.NORTH;
        }
        if (partner == null) {
            filled.add(main);
            return placeChest(main, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), plan);
        }
        ChestType mainType = partner == facing.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;
        BlockState mainState = Blocks.CHEST
                .defaultBlockState()
                .setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.TYPE, mainType);
        BlockState otherState = mainState.setValue(ChestBlock.TYPE, mainType.getOpposite());
        BlockPos other = main.relative(partner);
        if (index % 2 == 0) {
            level.setBlock(other, otherState, Block.UPDATE_ALL);
            ChestBlockEntity first = placeChest(main, mainState, plan);
            filled.add(main);
            return first;
        }
        filled.add(other);
        if (!(level.getBlockEntity(other) instanceof ChestBlockEntity second)) {
            throw new IllegalStateException("Вторая половина сундука схемы '" + plan.scheme() + "' не поставлена в "
                    + other.toShortString() + ": блок сундука не создал блочную сущность");
        }
        return second;
    }

    // Сторону второй половины выбрала раскладка (DemoPlacement): вдоль фасада, вне схемы, при любом повороте.
    private static @Nullable Direction partnerSide(DemoPlan plan) {
        BlockCoord base = plan.placement().chest();
        BlockCoord partner = plan.placement().chestPartner();
        if (partner == null) {
            return null;
        }
        return Direction.fromDelta(partner.x() - base.x(), 0, partner.z() - base.z());
    }

    private ChestBlockEntity placeChest(BlockPos pos, BlockState state, DemoPlan plan) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            throw new IllegalStateException("Сундук материалов схемы '" + plan.scheme() + "' не поставлен в "
                    + pos.toShortString() + ": блок сундука не создал блочную сущность");
        }
        return chest;
    }

    // Табличка — справа от сундука вдоль фасада (клетку выбрала раскладка), лицом туда же, куда крышка.
    private void placeSign(DemoPlan plan) {
        BlockCoord cell = plan.placement().sign();
        if (cell == null) {
            return;
        }
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!level.getBlockState(pos).isAir()) {
            return;
        }
        DemoPlacement.Look look = plan.placement().chestFacing();
        Direction facing = Direction.fromDelta(look.dx(), 0, look.dz());
        BlockState state = Blocks.OAK_SIGN.defaultBlockState();
        if (facing != null && facing.getAxis().isHorizontal()) {
            state = state.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(facing));
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal(plan.name())), true);
            sign.setChanged();
        }
    }

    // Духу хватает слотов не на всё: каждый заход берёт то, чего в мире ещё нет, сколько влезет в инвентарь.
    private List<Withdrawal> withdrawals(DemoPlan plan) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : plan.entries()) {
            if (SchematicBlocks.isSecondaryPart(entry.block(), entry.state())
                    || !BlockItems.hasBlockItem(entry.block())) {
                continue;
            }
            BlockPos pos =
                    new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            if (!level.getBlockState(pos).is(BlockStates.blockFor(entry.block()))) {
                need.merge(BlockItems.itemFor(entry.block()), 1, Integer::sum);
            }
        }
        int freeSlots = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                freeSlots++;
            } else {
                need.computeIfPresent(
                        stack.getItem(), (item, count) -> count > stack.getCount() ? count - stack.getCount() : null);
            }
        }
        List<Withdrawal> picked = new ArrayList<>();
        for (BlockPos pos : chests) {
            if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
                continue;
            }
            for (int slot = 0; slot < chest.getContainerSize() && freeSlots > 0; slot++) {
                ItemStack stack = chest.getItem(slot);
                Integer wanted = stack.isEmpty() ? null : need.get(stack.getItem());
                if (wanted == null || wanted <= 0) {
                    continue;
                }
                int take = Math.min(Math.min(wanted, stack.getCount()), CatalogLimits.COUNT_MAX);
                picked.add(new Withdrawal(
                        pos, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), take));
                need.put(stack.getItem(), wanted - take);
                freeSlots -= (take + stack.getMaxStackSize() - 1) / stack.getMaxStackSize();
            }
        }
        return List.copyOf(picked);
    }

    private static Rotation mcRotation(BuildRotation rotation) {
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        };
    }

    private void announce(String text) {
        String line = "<" + spirit.getDisplayName().getString() + "> " + text;
        Component message = Component.literal(line);
        int heard = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(spirit) > CatalogLimits.SAY_HEARING_RADIUS) {
                continue;
            }
            player.sendSystemMessage(message);
            heard++;
        }
        LOGGER.info("Демо-стройка: дух {} сообщил '{}', слышат {} игроков", spirit.getStringUUID(), text, heard);
    }
}
