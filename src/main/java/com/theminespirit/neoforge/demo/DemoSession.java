package com.theminespirit.neoforge.demo;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Одна демо-стройка: убирает площадку, ставит сундук с материалами, ведёт духа по уровням и убирает его,
 * когда всё готово (или называет причину отказа). Уровни идут по очереди: загон сначала младший, потом старший.
 */
final class DemoSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEMO_WAIT_TICKS = 100;
    private static final int WITHDRAW_RADIUS = 8;

    private final ServerLevel level;
    private final SpiritEntity spirit;
    private final List<DemoPlan> plans;
    private final UUID owner;
    private int index;

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
        spirit.cancelTask();
        spirit.stripEquipment();
    }

    void begin() {
        prepare(plans.get(0));
        spirit.startTask(node(0));
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
                spirit.startTask(node(index));
                return true;
            }
            spirit.discard();
            return false;
        }
        announce(spirit.taskFailureReason().orElse("стройка не удалась"));
        abort();
        spirit.discard();
        return false;
    }

    private TaskNode node(int planIndex) {
        DemoPlan plan = plans.get(planIndex);
        List<TaskNode> steps = new ArrayList<>();
        if (planIndex == 0) {
            steps.add(new ScriptCall("say", params("text", "Начинаю стройку: " + plan.name())));
        }
        for (Map.Entry<String, Integer> material : plan.materials().entrySet()) {
            int left = material.getValue();
            while (left > 0) {
                int take = Math.min(left, CatalogLimits.COUNT_MAX);
                steps.add(new ScriptCall("withdraw", withdrawParams(plan, material.getKey(), take)));
                left -= take;
            }
        }
        steps.add(new ScriptCall("build_schematic", buildParams(plan)));
        if (planIndex == plans.size() - 1) {
            steps.add(new ScriptCall("say", params("text", "Готово: " + plan.name())));
            steps.add(new ScriptCall("wait", params("ticks", DEMO_WAIT_TICKS)));
        }
        return new SequenceNode(steps);
    }

    private static Map<String, Object> params(String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);
        return params;
    }

    private static Map<String, Object> withdrawParams(DemoPlan plan, String item, int count) {
        Map<String, Object> params = new HashMap<>();
        BlockCoord chest = plan.placement().chest();
        params.put("container", chest.x() + "," + chest.y() + "," + chest.z());
        params.put("item", item);
        params.put("count", count);
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
        fillChest(plan);
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

    private void fillChest(DemoPlan plan) {
        BlockCoord chestCoord = plan.placement().chest();
        BlockPos chestPos = new BlockPos(chestCoord.x(), chestCoord.y(), chestCoord.z());
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            throw new IllegalStateException("Сундук материалов не поставлен в " + chestPos.toShortString()
                    + ": блок сундука не создал блочную сущность");
        }
        int slot = 0;
        for (Map.Entry<String, Integer> material : plan.materials().entrySet()) {
            Item item = BlockItems.itemFor(material.getKey());
            int left = material.getValue();
            while (left > 0) {
                if (slot >= chest.getContainerSize()) {
                    throw new IllegalStateException("Сундук не вмещает материалы схемы '" + plan.scheme()
                            + "': нужно больше " + chest.getContainerSize() + " стопок");
                }
                int take = Math.min(left, item.getDefaultMaxStackSize());
                chest.setItem(slot++, new ItemStack(item, take));
                left -= take;
            }
        }
        chest.setChanged();
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
