package com.chefcontrol.domain.user;

public enum RoleName {
    OWNER, MANAGER, KITCHEN, READONLY;

    /**
     * Returns whether a user with {@code assignerRole} is allowed to assign this role to another user.
     * OWNER can assign any role. MANAGER can only assign non-privileged roles.
     */
    public boolean canBeAssignedBy(RoleName assignerRole) {
        if (assignerRole == OWNER) return true;
        if (assignerRole == MANAGER) return this == KITCHEN || this == READONLY;
        return false;
    }
}
