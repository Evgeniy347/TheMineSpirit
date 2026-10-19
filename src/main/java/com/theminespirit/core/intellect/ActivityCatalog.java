package com.theminespirit.core.intellect;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ActivityCatalog(Map<ActivityId, Activity> adults, Map<ActivityId, Activity> children) {
    public ActivityCatalog {
        adults = checked("adults", adults);
        children = checked("children", children);
    }

    public static ActivityCatalog adultsOnly(List<Activity> activities) {
        EnumMap<ActivityId, Activity> adults = new EnumMap<>(ActivityId.class);
        for (Activity activity : activities) {
            if (activity == null) {
                throw new IllegalArgumentException(
                        "Каталог взрослых с null-деятельностью не собирается: каждая деятельность — класс");
            }
            if (adults.put(activity.id(), activity) != null) {
                throw new IllegalArgumentException("Деятельность " + activity.id()
                        + " дважды во взрослом каталоге: распределитель не решит, чьим спросом пользоваться");
            }
        }
        return new ActivityCatalog(adults, Map.of());
    }

    public Map<ActivityId, Activity> forChild(boolean child) {
        return child ? children : adults;
    }

    public Activity byId(ActivityId id) {
        if (id == null) {
            throw new IllegalArgumentException("Деятельность без id не ищется: id входит в план духа");
        }
        Activity activity = adults.get(id);
        if (activity == null) {
            activity = children.get(id);
        }
        if (activity == null) {
            throw new IllegalArgumentException(
                    "Деятельности " + id + " нет в каталоге: " + "неизвестную работу дух не возьмёт");
        }
        return activity;
    }

    public List<Activity> ordered() {
        return adults.values().stream()
                .sorted(Comparator.comparing(Activity::id))
                .toList();
    }

    private static Map<ActivityId, Activity> checked(String name, Map<ActivityId, Activity> catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "Каталог " + name + " не может быть null: пустой каталог задают пустой картой");
        }
        for (Map.Entry<ActivityId, Activity> entry : catalog.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Каталог " + name + " с null-ключом или null-деятельностью "
                        + "не собирается: каждая деятельность — класс с id");
            }
            if (entry.getValue().id() != entry.getKey()) {
                throw new IllegalArgumentException("Деятельность под ключом " + entry.getKey() + " называет себя "
                        + entry.getValue().id() + ": ключ обязан совпадать с Activity.id()");
            }
        }
        return Map.copyOf(catalog);
    }
}
