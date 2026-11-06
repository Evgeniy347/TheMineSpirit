package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.ShelterMaterials;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class BuildShelterScript implements ScriptInstance {
    static final String SCHEMATIC = "shelter";
    static final String TORCH = "torch";
    private static final int ENTRANCE_X = 3;
    private static final int ENTRANCE_Z = 1;
    private static final String BODY = "cobblestone";
    private static final String PORCH = "cobblestone_slab";

    private final SpiritEntity spirit;
    private final @Nullable BuildSchematicScript builder;
    private final BlockCoord origin;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private @Nullable Approach exitApproach;
    private String failureReason;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private boolean built;
    private boolean leaving;

    BuildShelterScript(SpiritEntity spirit) {
        this.spirit = spirit;
        Schematic base = SchematicResources.load(SCHEMATIC);
        int bodyNeeded = count(base, BODY);
        int porchNeeded = count(base, PORCH);
        Optional<ShelterMaterials.Choice> choice =
                ShelterMaterial.choose(spirit.getInventory(), bodyNeeded, porchNeeded);
        String problem = null;
        Schematic scheme = null;
        if (choice.isEmpty()) {
            problem = "нет дешёвого материала укрытия: нужен полный запас булыжника или досок (" + bodyNeeded
                    + " блоков и " + porchNeeded + " полублок порога); брёвна и гладкий камень дороги";
        } else {
            scheme = materialize(base, choice.get().block(), choice.get().slab());
            Map<String, Integer> needed = requirements(scheme);
            int torches = needed.getOrDefault(TORCH, 0);
            needed.remove(TORCH);
            String lacking = lacking(spirit, needed);
            if (!lacking.isEmpty()) {
                problem = "не хватает материала укрытия до первого блока: " + lacking
                        + "; стройка не начата, недоделки не будет";
            } else if (BlockItems.countIn(spirit.getInventory(), TORCH) < torches) {
                problem = "нельзя начинать укрытие без факела: свет — обязательная часть постройки, нужно "
                        + torches + ", в инвентаре "
                        + BlockItems.countIn(spirit.getInventory(), TORCH) + "; стройка не начата";
            }
        }
        if (problem != null) {
            this.builder = null;
            this.origin = new BlockCoord(0, 0, 0);
            this.sizeX = 0;
            this.sizeY = 0;
            this.sizeZ = 0;
            this.failureReason = problem;
            this.outcome = ScriptStatus.FAILED;
            return;
        }
        BlockPos feet = spirit.blockPosition();
        this.origin = new BlockCoord(
                feet.getX() - scheme.sizeX() / 2, feet.getY() - 1, feet.getZ() - (scheme.sizeZ() - 1) / 2);
        this.sizeX = scheme.sizeX();
        this.sizeY = scheme.sizeY();
        this.sizeZ = scheme.sizeZ();
        this.builder = new BuildSchematicScript(spirit, scheme, SCHEMATIC, origin, BuildRotation.NONE);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (builder == null) {
            return outcome;
        }
        if (!built) {
            ScriptStatus status = builder.tick();
            if (status == ScriptStatus.RUNNING) {
                return status;
            }
            if (status == ScriptStatus.FAILED) {
                failureReason = builder.failureReason();
                outcome = ScriptStatus.FAILED;
                return outcome;
            }
            built = true;
        }
        if (!leaving) {
            if (!inside(spirit.blockPosition())) {
                outcome = ScriptStatus.SUCCEEDED;
                return outcome;
            }
            leaving = true;
            exit().begin(exitPoint(), BuildTuning.STAND_ARRIVAL);
            return ScriptStatus.RUNNING;
        }
        return switch (exit().tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                outcome = ScriptStatus.SUCCEEDED;
                yield outcome;
            }
            case STUCK, TIMEOUT -> {
                failureReason = "укрытие построено, но дух не вышел из него: "
                        + (exit().refusal() == null ? "путь наружу не найден" : exit().refusal());
                outcome = ScriptStatus.FAILED;
                yield outcome;
            }
        };
    }

    private Approach exit() {
        if (exitApproach == null) {
            exitApproach = new Approach(spirit);
        }
        return exitApproach;
    }

    private Vec3 exitPoint() {
        return new Vec3(origin.x() + ENTRANCE_X + 0.5, origin.y() + 1, origin.z() + ENTRANCE_Z - 2 + 0.5);
    }

    private boolean inside(BlockPos feet) {
        return feet.getX() >= origin.x()
                && feet.getX() < origin.x() + sizeX
                && feet.getZ() >= origin.z()
                && feet.getZ() < origin.z() + sizeZ
                && feet.getY() >= origin.y()
                && feet.getY() <= origin.y() + sizeY;
    }

    private static int count(Schematic schematic, String blockId) {
        int total = 0;
        for (Schematic.Entry entry : schematic.blocks()) {
            if (SchematicBlocks.path(entry.block()).equals(blockId)) {
                total++;
            }
        }
        return total;
    }

    private static Schematic materialize(Schematic base, String material, String slab) {
        List<Schematic.Entry> blocks = new ArrayList<>(base.blocks().size());
        for (Schematic.Entry entry : base.blocks()) {
            String block = switch (SchematicBlocks.path(entry.block())) {
                case BODY -> material;
                case PORCH -> slab;
                default -> entry.block();
            };
            blocks.add(new Schematic.Entry(entry.dx(), entry.dy(), entry.dz(), block, entry.state(), entry.text()));
        }
        return new Schematic(base.sizeX(), base.sizeY(), base.sizeZ(), blocks, base.air());
    }

    private static Map<String, Integer> requirements(Schematic schematic) {
        Map<String, Integer> needed = new TreeMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            needed.merge(SchematicBlocks.path(entry.block()), 1, Integer::sum);
        }
        return needed;
    }

    private static String lacking(SpiritEntity spirit, Map<String, Integer> needed) {
        List<String> lacking = new ArrayList<>();
        for (Map.Entry<String, Integer> item : new LinkedHashMap<>(needed).entrySet()) {
            int have = BlockItems.countIn(spirit.getInventory(), item.getKey());
            if (have < item.getValue()) {
                lacking.add(item.getKey() + " " + have + "/" + item.getValue());
            }
        }
        return String.join(", ", lacking);
    }

    @Override
    public Optional<Progress> progress() {
        return builder == null ? Optional.empty() : builder.progress();
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED && builder != null ? builder.result() : Optional.empty();
    }

    @Override
    public void cancel() {
        if (builder != null) {
            builder.cancel();
        }
        if (exitApproach != null) {
            exitApproach.cancel();
        }
    }

    @Override
    public void suspend() {
        if (builder != null) {
            builder.suspend();
        }
        if (exitApproach != null) {
            exitApproach.suspend();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return builder == null ? Map.of() : builder.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (builder != null) {
            builder.restoreState(state);
        }
    }
}
