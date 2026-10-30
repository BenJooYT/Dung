package com.lieyabull.dung.dungeon;

/** Graph purpose of a room, independent of its content type. */
public enum RoomRole {
    NORMAL(false),
    HUB(true),
    REQUIRED(true),
    OPTIONAL(false);

    private final boolean required;

    RoomRole(boolean required) {
        this.required = required;
    }

    public boolean required() {
        return required;
    }
}
