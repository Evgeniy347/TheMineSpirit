package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class MineFinish {
    enum State {
        RUNNING,
        DONE,
        FAILED
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double ARRIVAL = 0.6;

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildRotation rotation;
    private final List<Schematic.PlacedEntry> torches;
    private final Map<MineColumn, MineColumn> parents;
    private final Set<MineColumn> open;
    private final List<MineColumn> exitPath = new ArrayList<>();
    private boolean walking;
    private @Nullable String failure;

    MineFinish(
            SpiritEntity spirit,
            BlockPlacer placer,
            Approach approach,
            BuildRotation rotation,
            List<Schematic.PlacedEntry> torches,
            Map<MineColumn, MineColumn> parents,
            Set<MineColumn> open) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому ставить факелы");
        this.level = spirit.level();
        this.placer = Objects.requireNonNull(placer, "Ставильщик не может быть null: без него нет факелов");
        this.approach = Objects.requireNonNull(approach, "Подход не может быть null: без него не выйти");
        this.rotation = Objects.requireNonNull(rotation, "Поворот не может быть null: без него факел ляжет не так");
        this.torches = Objects.requireNonNull(torches, "Список факелов не может быть null: пустой — Set.of()");
        this.parents = Objects.requireNonNull(parents, "Родители ступеней не могут быть null: по ним ищется выход");
        this.open = Objects.requireNonNull(open, "Открытые колонки не могут быть null: по ним ищется выход");
    }

    State torchTick() {
        Schematic.PlacedEntry entry = torches.isEmpty() ? null : torches.get(0);
        if (entry == null || BlockItems.countIn(spirit.getInventory(), entry.block()) < 1) {
            torches.clear();
            beginExit();
            return State.DONE;
        }
        BlockPos pos = toPos(entry.pos());
        MineColumn near = nearestOpen(entry.pos());
        if (!level.getBlockState(pos).isAir() || near == null) {
            torches.remove(0);
            return State.RUNNING;
        }
        BlockCoord stand = near.stand();
        if (!walking) {
            approach.begin(vec(stand), ARRIVAL);
        }
        BlockState wanted = BlockStates.stateFor(entry.block(), entry.state());
        if (rotation != BuildRotation.NONE) {
            wanted = wanted.rotate(level, pos, SchematicSite.mcRotation(rotation));
        }
        return switch (approach.tick()) {
            case MOVING -> {
                walking = true;
                yield State.RUNNING;
            }
            case ARRIVED, STUCK, TIMEOUT -> {
                walking = false;
                torches.remove(0);
                if (placer.begin(pos, wanted, entry.block()) instanceof BlockPlacer.Refused refused) {
                    LOGGER.info("Шахта: факел в {} пропущен: {}", entry.pos(), refused.message());
                }
                yield State.RUNNING;
            }
        };
    }

    State exitTick() {
        if (exitPath.isEmpty()) {
            return State.DONE;
        }
        BlockCoord stand = exitPath.get(0).stand();
        if (!walking) {
            approach.begin(vec(stand), ARRIVAL);
        }
        return switch (approach.tick()) {
            case MOVING -> {
                walking = true;
                yield State.RUNNING;
            }
            case ARRIVED -> {
                walking = false;
                exitPath.remove(0);
                yield State.RUNNING;
            }
            case STUCK, TIMEOUT -> {
                walking = false;
                failure = "выход наверх застрял у ступени " + stand + ": "
                        + (approach.refusal() == null ? "путь не найден" : approach.refusal());
                yield State.FAILED;
            }
        };
    }

    String failure() {
        return failure == null ? "выход из шахты провалился" : failure;
    }

    private void beginExit() {
        exitPath.clear();
        BlockPos feet = spirit.blockPosition();
        MineColumn step = nearestOpen(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
        while (step != null) {
            exitPath.add(step);
            step = parents.get(step);
        }
        walking = false;
    }

    private @Nullable MineColumn nearestOpen(BlockCoord near) {
        MineColumn best = null;
        long bestDistance = Long.MAX_VALUE;
        for (MineColumn column : open) {
            BlockCoord stand = column.stand();
            long dx = (long) stand.x() - near.x();
            long dy = (long) stand.y() - near.y();
            long dz = (long) stand.z() - near.z();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = column;
            }
        }
        return best;
    }

    private static Vec3 vec(BlockCoord stand) {
        return new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5);
    }

    private static BlockPos toPos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
