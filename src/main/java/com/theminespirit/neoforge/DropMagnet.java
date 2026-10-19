package com.theminespirit.neoforge;

import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.MagnetRules;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Магнит дропа духа (упрощение 44.1): раз в период из таблицы {@code pickup/magnet.json} свободные предметы в
 * радиусе сами ложатся в инвентарь, если есть место. Пока дух сам отдаёт вещи (deliver, give, drop_to_player,
 * unload), магнит молчит — иначе он подбирал бы только что отданное.
 */
final class DropMagnet {
    private static final Set<String> GIVING_SCRIPTS = Set.of("deliver", "give", "drop_to_player", "unload", "deposit");

    private static final int REMEMBERED_PULLS = 256;

    private record Pull(String item, Vec3 at, long tick, int count) {}

    private final SpiritEntity spirit;
    private final Map<UUID, Pull> recent = new LinkedHashMap<>();
    private int ticks;
    private int pulls;

    DropMagnet(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    void tick() {
        MagnetRules rules = DataTables.INSTANCE.magnet().current();
        if (++ticks < rules.intervalTicks()) {
            return;
        }
        ticks = 0;
        if (spirit.currentTaskScript().filter(GIVING_SCRIPTS::contains).isPresent()) {
            return;
        }
        double radius = rules.radius();
        List<ItemEntity> drops = spirit.level()
                .getEntitiesOfClass(
                        ItemEntity.class,
                        spirit.getBoundingBox().inflate(radius),
                        drop -> drop.isAlive() && drop.distanceToSqr(spirit) <= radius * radius);
        for (ItemEntity drop : drops) {
            if (rules.attracts(facts(drop))) {
                pull(drop);
            }
        }
    }

    int pulls() {
        return pulls;
    }

    int claim(AABB area, IdFilter items, long since) {
        int total = 0;
        Iterator<Pull> pulls = recent.values().iterator();
        while (pulls.hasNext()) {
            Pull pull = pulls.next();
            if (pull.tick() >= since && area.contains(pull.at()) && items.matches(pull.item())) {
                total += pull.count();
                pulls.remove();
            }
        }
        return total;
    }

    private MagnetRules.Drop facts(ItemEntity drop) {
        Entity owner = drop.getOwner();
        UUID target = drop.getTarget();
        BlockPos pos = drop.blockPosition();
        Optional<UUID> holder = WorkReserveHub.leasesFor(spirit.level())
                .owner(new WorkTarget(WorkKind.DROP, new BlockCoord(pos.getX(), pos.getY(), pos.getZ())));
        return new MagnetRules.Drop(
                drop.getAge(),
                drop.hasPickUpDelay(),
                owner instanceof Player,
                owner != null && owner.getUUID().equals(spirit.getUUID()),
                target != null && !target.equals(spirit.getUUID()),
                holder.isPresent() && !holder.get().equals(spirit.getUUID()));
    }

    private void pull(ItemEntity drop) {
        ItemStack stack = drop.getItem();
        ItemStack rest = spirit.getInventory().addItem(stack.copy());
        int taken = stack.getCount() - rest.getCount();
        if (taken <= 0) {
            return;
        }
        spirit.take(drop, taken);
        pulls++;
        recent.merge(
                drop.getUUID(),
                new Pull(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        drop.position(),
                        spirit.level().getGameTime(),
                        taken),
                (old, added) -> new Pull(old.item(), old.at(), old.tick(), old.count() + added.count()));
        if (recent.size() > REMEMBERED_PULLS) {
            recent.remove(recent.keySet().iterator().next());
        }
        if (rest.isEmpty()) {
            drop.discard();
        } else {
            drop.setItem(rest);
        }
    }
}
