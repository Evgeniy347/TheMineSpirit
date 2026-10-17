package com.theminespirit.core.rest;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.policy.UnprotectArea;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Фальшивое хранилище разрешений для тестов REST-контроллера: память и счётчик выдач. */
final class FakePolicyGateway implements PolicyGateway {
    private static final String ID_PREFIX = "unprotect-";

    private final Map<String, UnprotectArea> areas = new LinkedHashMap<>();
    private final List<String> grants = new ArrayList<>();
    private final List<String> revokes = new ArrayList<>();
    private String refusal;

    List<String> grants() {
        return grants;
    }

    List<String> revokes() {
        return revokes;
    }

    FakePolicyGateway with(UnprotectArea area) {
        areas.put(area.id(), area);
        return this;
    }

    FakePolicyGateway refuseWith(String message) {
        refusal = message;
        return this;
    }

    @Override
    public List<UnprotectArea> areas() {
        return List.copyOf(areas.values());
    }

    @Override
    public UnprotectArea grant(UnprotectRequest request) {
        if (refusal != null) {
            throw new IllegalArgumentException(refusal);
        }
        String id = ID_PREFIX + request.requestId();
        grants.add(id);
        long expiresAt = request.ttlSeconds() == 0 ? 0 : 1_000_000L + request.ttlSeconds() * 1000L;
        UnprotectArea area = new UnprotectArea(id, request.dimension(),
                new BlockBox(request.min(), request.max()),
                request.grantedBy(), 1_000_000L, request.reason(), expiresAt);
        areas.put(id, area);
        return area;
    }

    @Override
    public UnprotectArea revoke(String id) {
        revokes.add(id);
        return areas.remove(id);
    }
}
