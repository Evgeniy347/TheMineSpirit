package com.theminespirit.neoforge.demo;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class DemoSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEMO_WAIT_TICKS = 100;
    private static final int MAX_ROUNDS = 20;
    // mc: уровень тикета = 33 - расстояние; сущности тикают при уровне не выше 31.
    private static final int TICKET_DISTANCE = 2;
    private static final TicketType<ChunkPos> DEMO_TICKET =
            TicketType.create("theminespirit_demo", Comparator.comparingLong(ChunkPos::toLong));
    private static final String NO_MATERIAL = "не хватило материала";
    private static final int MINE_TORCHES = 16;
    private static final String INVENTORY_FULL = "инвентарь духа полон";

    private final ServerLevel level;
    private final SpiritEntity spirit;
    private final List<DemoPlan> plans;
    private final UUID owner;
    private int index;
    private int round;
    private List<BlockPos> chests = List.of();
    private final List<ChunkPos> held = new ArrayList<>();
    private final DemoStock stock;

    DemoSession(ServerLevel level, SpiritEntity spirit, List<DemoPlan> plans, UUID owner) {
        this.level = level;
        this.spirit = spirit;
        this.plans = List.copyOf(plans);
        this.owner = owner;
        this.stock = new DemoStock(level, spirit);
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
        spirit.startTask(node(0, stock.withdrawals(plans.get(0), chests)));
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
                spirit.startTask(node(index, stock.withdrawals(plans.get(index), chests)));
                return true;
            }
            releaseChunks();
            spirit.discard();
            return false;
        }
        String reason = spirit.taskFailureReason().orElse("стройка не удалась");
        if (reason.contains(NO_MATERIAL) && round < MAX_ROUNDS) {
            List<DemoStock.Withdrawal> next = stock.withdrawals(plans.get(index), chests);
            if (!next.isEmpty()) {
                round++;
                spirit.startTask(node(index, next));
                return true;
            }
        }
        if (reason.contains(INVENTORY_FULL) && round < MAX_ROUNDS) {
            round++;
            LOGGER.info(
                    "Демо-стройка: дух {} — заход сессии упёрся в полный инвентарь ({}), стройка продолжает" + " сама",
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

    private TaskNode node(int planIndex, List<DemoStock.Withdrawal> withdrawals) {
        DemoPlan plan = plans.get(planIndex);
        List<TaskNode> steps = new ArrayList<>();
        if (planIndex == 0 && round == 0) {
            steps.add(new ScriptCall("say", params("text", "Начинаю стройку: " + plan.name())));
        }
        for (DemoStock.Withdrawal withdrawal : withdrawals) {
            steps.add(new ScriptCall("withdraw", DemoStock.withdrawParams(withdrawal)));
        }
        steps.add(new ScriptCall("build_schematic", buildParams(plan)));
        if (planIndex == plans.size() - 1) {
            steps.add(new ScriptCall("say", params("text", "Готово: " + plan.name())));
            steps.add(new ScriptCall("wait", params("ticks", DEMO_WAIT_TICKS)));
        }
        return new SequenceNode(steps);
    }

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

    private static Map<String, Object> buildParams(DemoPlan plan) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", plan.scheme());
        params.put("x", plan.placement().origin().x());
        params.put("y", plan.placement().origin().y() - (underground(plan) ? 1 : 0));
        params.put("z", plan.placement().origin().z());
        params.put("rotation", plan.placement().rotation().id());
        return params;
    }

    private void prepare(DemoPlan plan) {
        if (underground(plan)) {
            giveMiningKit();
        } else {
            clearVolume(plan);
            flattenFloor(plan);
        }
        round = 0;
        chests = stock.fillChests(plan);
    }

    private static boolean underground(DemoPlan plan) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(plan.scheme())
                .map(preset -> preset.type() == BuildingType.UNDERGROUND)
                .orElse(false);
    }

    private void giveMiningKit() {
        spirit.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().addItem(new ItemStack(Items.TORCH, MINE_TORCHES));
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
