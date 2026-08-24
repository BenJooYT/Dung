package com.lieyabull.dung.util;

import java.util.regex.Pattern;

/** Shared text formatting utilities used across the plugin. */
public final class TextUtil {

    /** Matches an ampersand color/format code: &0-&9, &a-&f, &k-&o, &r (and hex &x). */
    private static final Pattern AMP_CODE = Pattern.compile("&([0-9a-fk-orxA-FK-ORX])");

    private TextUtil() {}

    /** Translate legacy ampersand color codes (&a) into section codes (§a). */
    public static String translateAmp(String s) {
        if (s == null || s.indexOf('&') < 0) return s;
        return AMP_CODE.matcher(s).replaceAll("\u00A7$1");
    }

    /** Format a double: show as integer if whole, otherwise one decimal place. */
    public static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    /** Capitalize the first character of a string. */
    public static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}