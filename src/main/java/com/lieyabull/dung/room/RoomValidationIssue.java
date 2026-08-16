package com.lieyabull.dung.room;

/**
 * A single validation finding. ERRORs must be fixed before a template can be marked validated;
 * WARNINGs are advisory (they do not block registration but are reported to the author).
 */
public final class RoomValidationIssue {
    public enum Level { ERROR, WARNING }

    public final Level level;
    public final String code;
    public final String message;
    /** Template-relative coordinate the issue refers to, or null. */
    public final int[] at;

    public RoomValidationIssue(Level level, String code, String message, int[] at) {
        this.level = level;
        this.code = code;
        this.message = message;
        this.at = at;
    }

    public static RoomValidationIssue error(String code, String message, int[] at) {
        return new RoomValidationIssue(Level.ERROR, code, message, at);
    }

    public static RoomValidationIssue warn(String code, String message, int[] at) {
        return new RoomValidationIssue(Level.WARNING, code, message, at);
    }

    @Override
    public String toString() {
        String pos = at == null ? "" : " @" + at[0] + "," + at[1] + "," + at[2];
        return "[" + level + "][" + code + "] " + message + pos;
    }
}