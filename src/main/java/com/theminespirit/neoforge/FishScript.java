package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.fishing.FishingSpots;
import com.theminespirit.core.fishing.FishingWait;
import com.theminespirit.core.inventory.InventorySpace;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.FishingParams;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class FishScript implements ScriptInstance {
    private static final double STAND_ARRIVAL = 0.6;

    private enum Phase {
        FIND,
        APPROACH,
        WAIT,
        CATCH
    }

    private final SpiritEntity spirit;
    private final FishingParams params;
    private final FishingData data;
    private final ServerLevel level;
    private final Approach approach;
    private final FishingWaterView water;
    private final BlockCoord origin;
    private final Map<String, Integer> catchByItem = new LinkedHashMap<>();
    private Phase phase = Phase.FIND;
    private FishingSpots.Spot spot;
    private FishingHook emulatedHook;
    private int rodSlot = -1;
    private int waitTicks;
    private int elapsed;
    private int totalCaught;
    private int fishCaught;
    private boolean rodBroken;
    private String failureReason;

    public FishScript(SpiritEntity spirit, FishingParams params) {
        this.spirit = spirit;
        this.params = params;
        this.level = (ServerLevel) spirit.level();
        this.data = DataTables.INSTANCE.fishing().current();
        this.approach = new Approach(spirit);
        BlockPos feet = spirit.blockPosition();
        this.origin = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        this.water = new FishingWaterView(level, origin, params.radius());
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время рыбалки");
        }
        if (fishCaught >= params.count()) {
            return ScriptStatus.SUCCEEDED;
        }
        if (rodBroken) {
            return totalCaught > 0 ? ScriptStatus.SUCCEEDED : fail("удочка сломалась до первой рыбы");
        }
        if (elapsed >= params.durationTicks()) {
            return totalCaught > 0
                    ? ScriptStatus.SUCCEEDED
                    : fail("за " + params.durationTicks() + " тиков не поймано ни одной рыбы: " + waterText());
        }
        elapsed++;
        return switch (phase) {
            case FIND -> find();
            case APPROACH -> approachStep();
            case WAIT -> waitForBite();
            case CATCH -> catchStep();
        };
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(Math.min(fishCaught, params.count()), params.count()));
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of("caught", fishCaught, "total", totalCaught, "ticks", elapsed);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        fishCaught = readCount(state, "caught");
        totalCaught = readCount(state, "total");
        elapsed = readCount(state, "ticks");
    }

    @Override
    public Optional<ScriptResult> result() {
        List<Map<String, Object>> fish = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : catchByItem.entrySet()) {
            fish.add(Map.of("item", entry.getKey(), "count", entry.getValue()));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("fish", List.copyOf(fish));
        fields.put("total", totalCaught);
        fields.put("ticks", elapsed);
        return Optional.of(new ScriptResult.Data(Map.copyOf(fields)));
    }

    private ScriptStatus find() {
        if (!ensureRod()) {
            return fail("рыбалка без удочки невозможна: в инвентаре духа нет удочки");
        }
        water.advance();
        Optional<FishingSpots.Spot> found = FishingSpots.find(origin, water, data);
        if (found.isPresent()) {
            spot = found.get();
            approach.begin(Vec3.atBottomCenterOf(toPos(spot.stand())), STAND_ARRIVAL);
            phase = Phase.APPROACH;
            return ScriptStatus.RUNNING;
        }
        if (water.done()) {
            return fail("рядом нет открытой воды: " + waterText() + " в радиусе " + params.radius());
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approachStep() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> startWait();
            case STUCK, TIMEOUT ->
                fail("не дошёл до берега " + spot.stand()
                        + (approach.refusal() == null ? "" : ": " + approach.refusal()));
        };
    }

    private ScriptStatus startWait() {
        ItemStack rod = spirit.getInventory().getItem(rodSlot);
        int lureSpeed = (int) (EnchantmentHelper.getFishingTimeReduction(level, rod, spirit) * 20.0F);
        FishingWait wait = DataTables.INSTANCE.fishingWait().current();
        int base = Mth.nextInt(spirit.getRandom(), wait.waitMinTicks(), wait.waitMaxTicks()) - lureSpeed;
        int hooked = Mth.nextInt(spirit.getRandom(), wait.hookedMinTicks(), wait.hookedMaxTicks());
        waitTicks = Math.max(1, Math.max(0, base) + hooked);
        phase = Phase.WAIT;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus waitForBite() {
        if (waitTicks > 0) {
            waitTicks--;
            return ScriptStatus.RUNNING;
        }
        phase = Phase.CATCH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus catchStep() {
        ItemStack rod = spirit.getInventory().getItem(rodSlot);
        List<ItemStack> drops = rollLoot(rod);
        StackMapper mapper = new StackMapper();
        List<Stack> inventory = mapper.map(spirit.getInventory());
        List<Stack> incoming = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                incoming.add(mapper.of(drop));
            }
        }
        if (!incoming.isEmpty() && !InventorySpace.holds(inventory, incoming)) {
            return fail("инвентарь полон: не помещается " + incoming.get(0).item());
        }
        for (ItemStack drop : drops) {
            catchByItem.merge(BuiltInRegistries.ITEM.getKey(drop.getItem()).toString(), drop.getCount(), Integer::sum);
            totalCaught += drop.getCount();
            if (drop.is(ItemTags.FISHES)) {
                fishCaught += drop.getCount();
            }
            ItemStack remainder = spirit.getInventory().addItem(drop.copy());
            if (!remainder.isEmpty()) {
                return fail("инвентарь полон: не помещается " + BuiltInRegistries.ITEM.getKey(remainder.getItem()));
            }
        }
        rod.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
        if (rod.isEmpty()) {
            rodBroken = true;
        } else {
            startWait();
        }
        return ScriptStatus.RUNNING;
    }

    private List<ItemStack> rollLoot(ItemStack rod) {
        if (emulatedHook == null) {
            emulatedHook = new FishingHook(EntityType.FISHING_BOBBER, level);
        }
        int luck = EnchantmentHelper.getFishingLuckBonus(level, rod, spirit);
        LootParams lootParams = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(toPos(spot.water())))
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, emulatedHook)
                .withParameter(LootContextParams.ATTACKING_ENTITY, spirit)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        return table.getRandomItems(lootParams);
    }

    private boolean ensureRod() {
        if (rodSlot >= 0 && spirit.getInventory().getItem(rodSlot).is(Items.FISHING_ROD)) {
            return true;
        }
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).is(Items.FISHING_ROD)) {
                rodSlot = slot;
                return true;
            }
        }
        return false;
    }

    private String waterText() {
        return data.minWidth() + "x" + data.minWidth() + " глубиной " + data.minDepth();
    }

    private static int readCount(Map<String, Object> state, String key) {
        Object saved = state.get(key);
        if (saved == null) {
            return 0;
        }
        if (!(saved instanceof Number number) || number.intValue() < 0) {
            throw new IllegalArgumentException("Состояние fish: '" + key + "' = " + saved
                    + ", а ожидалось неотрицательное целое: снимок задачи повреждён, проверьте его");
        }
        return number.intValue();
    }

    private ScriptStatus fail(String reason) {
        cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
