package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.rest.AuditGroupsGateway;
import com.theminespirit.neoforge.board.AuditGroupsStore;
import java.util.List;

public final class SpiritAuditGroupsGateway implements AuditGroupsGateway {
    @Override
    public List<Group> groups() {
        return AuditGroupsStore.get();
    }
}
