package com.lieyabull.dung.util;

/** Shared text formatting utilities used across the plugin. */
public final class TextUtil {

    private TextUtil() {}

    /** Format a double: show as integer if whole, otherwise one decimal place. */
    public static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    /** Capitalize the first character of a string. */
    public static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}