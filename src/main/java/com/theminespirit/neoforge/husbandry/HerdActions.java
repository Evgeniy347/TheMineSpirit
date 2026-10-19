package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.SwordTier;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HerdActions {
    private final SpiritEntity spirit;
    private final Level level;

    public HerdActions(SpiritEntity spirit) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому пасти стадо");
        this.level = spirit.level();
    }

    public int count(Item item) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean consumeOne(Item item) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    public Optional<String> addOrFail(ItemStack stack) {
        ItemStack rest = spirit.getInventory().addItem(stack.copy());
        if (rest.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("инвентарь полон: не помещается " + itemId(stack));
    }

    public SwordSurvey surveySwords() {
        SwordTier best = null;
        ItemStack bestStack = ItemStack.EMPTY;
        List<String> unknown = new ArrayList<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof SwordItem)) {
                continue;
            }
            SwordTier tier;
            try {
                tier = SwordTier.ofItem(itemId(stack));
            } catch (IllegalArgumentException error) {
                unknown.add(itemId(stack));
                continue;
            }
            if (best == null || tier.compareTo(best) > 0) {
                best = tier;
                bestStack = stack;
            }
        }
        return new SwordSurvey(best, bestStack, List.copyOf(unknown));
    }

    public boolean feedAdult(Animal animal, Item feed) {
        Objects.requireNonNull(animal, "Животное не может быть null: без него неизвестно, кого кормить");
        if (!consumeOne(feed)) {
            return false;
        }
        animal.setInLove(null);
        return true;
    }

    public boolean feedBaby(AgeableMob baby, Item feed) {
        Objects.requireNonNull(baby, "Детёныш не может быть null: без него неизвестно, кого растить");
        if (!consumeOne(feed)) {
            return false;
        }
        baby.ageUp(Math.max(1, -baby.getAge() / 20 / 10));
        return true;
    }

    public int shearWool(Sheep sheep, ItemStack shears) {
        Objects.requireNonNull(sheep, "Овца не может быть null: без неё неизвестно, кого стричь");
        sheep.setSheared(true);
        shears.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
        Item wool = BuiltInRegistries.ITEM
                .getOptional(
                        ResourceLocation.parse("minecraft:" + sheep.getColor().getName() + "_wool"))
                .orElseThrow(() -> new IllegalStateException("В реестре нет шерсти цвета " + sheep.getColor()
                        + ": id шерсти неверен для версии игры, сверь его с реестром"));
        int count = 1 + level.random.nextInt(3);
        ItemEntity drop =
                new ItemEntity(level, sheep.getX(), sheep.getY() + 1.0, sheep.getZ(), new ItemStack(wool, count));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        return count;
    }

    public Optional<String> milkIntoInventory() {
        if (!spirit.getInventory().canAddItem(new ItemStack(Items.MILK_BUCKET))) {
            return Optional.of("инвентарь полон: не помещается minecraft:milk_bucket, мир не изменён");
        }
        if (!consumeOne(Items.BUCKET)) {
            return Optional.of("нет пустых вёдер в инвентаре: доить не во что, мир не изменён");
        }
        return addOrFail(new ItemStack(Items.MILK_BUCKET));
    }

    public boolean strike(Animal target, SwordTier tier) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, кого бить");
        Objects.requireNonNull(tier, "Тир меча не может быть null: без него неизвестен урон");
        target.hurt(level.damageSources().mobAttack(spirit), tier.damage());
        return !target.isAlive();
    }

    public void directTowards(Animal animal, Vec3 point, double speed) {
        Objects.requireNonNull(animal, "Животное не может быть null: без него некого вести");
        Objects.requireNonNull(point, "Точка не может быть null: без неё неизвестно, куда вести");
        animal.getNavigation().moveTo(point.x, point.y, point.z, speed);
    }

    public void stopLeading(Animal animal) {
        Objects.requireNonNull(animal, "Животное не может быть null: без него некого останавливать");
        animal.getNavigation().stop();
    }

    public void breedPair(Animal parent, Animal partner) {
        Objects.requireNonNull(parent, "Родитель не может быть null: без него неизвестно, чьё дитя рождается");
        Objects.requireNonNull(partner, "Пара не может быть null: детёныш рождается от двух голов");
        if (!(level instanceof ServerLevel server)) {
            throw new IllegalStateException("Разводить можно только на сервере: клиентский мир детёнышей не создаёт");
        }
        parent.spawnChildFromBreeding(server, partner);
    }

    public ItemStack shearsStack() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(Items.SHEARS)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static Item registryItem(String id) {
        return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.parse(id))
                .orElseThrow(() -> new IllegalStateException("В реестре нет предмета " + id
                        + ": id корма неверен для версии игры, " + "сверь его с реестром"));
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public record SwordSurvey(SwordTier best, ItemStack stack, List<String> unknown) {
        public SwordSurvey {
            Objects.requireNonNull(stack, "Стек меча не может быть null: пустой задают ItemStack.EMPTY");
            Objects.requireNonNull(unknown, "Список неизвестных не может быть null: пустой задают пустым списком");
        }
    }
}
