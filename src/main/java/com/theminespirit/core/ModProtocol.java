package com.theminespirit.core;

import java.util.Objects;

/**
 * Сборка мода, которой принадлежит сетевой протокол. Клиент и сервер обязаны совпасть:
 * иначе индексы синхронизированных полей сущности разъезжаются и клиент падает на set_entity_data.
 */
public record ModProtocol(String build) {
    public ModProtocol {
        Objects.requireNonNull(build, "Сборка мода не может быть null: протокол сверяется по её имени");
        build = build.strip();
        if (build.isEmpty()) {
            throw new IllegalArgumentException("Сборка мода пустая: без неё нельзя отличить старый клиент от нового");
        }
    }

    public boolean matches(ModProtocol other) {
        Objects.requireNonNull(other, "Другая сборка не может быть null: сравнивать нечего");
        return build.equals(other.build);
    }

    public static String refusal(String clientBuild, String serverBuild) {
        Objects.requireNonNull(clientBuild, "Сборка клиента не может быть null: отказ называет обе стороны");
        Objects.requireNonNull(serverBuild, "Сборка сервера не может быть null: отказ называет обе стороны");
        return "Сборки мода The Mine Spirit не совпадают: клиент '" + clientBuild + "', сервер '" + serverBuild
                + "'. Обновите клиент до сборки сервера и перезапустите игру.";
    }
}
