package com.lieyabull.dung.lang;

import java.util.Locale;

/** The UI languages a player can choose. Each carries its own-language display name and a
 *  stable persistence code (also used as the default for {@link java.util.Locale}). */
public enum Language {
    ENGLISH("en", "English"),
    MAGYAR("hu", "Magyar");

    public final String code;
    public final String displayName; // endonym: what the language calls itself

    Language(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /** The default language (English) used before a player picks one. */
    public static Language defaultLang() {
        return ENGLISH;
    }

    /** Resolve a language from a player-supplied string. Accepts the code ("en"/"hu"), the
     *  enum name ("english"/"magyar"), and the display name, all case-insensitively. */
    public static Language parse(String s) {
        if (s == null) return null;
        String q = s.trim().toLowerCase(Locale.ROOT);
        for (Language lang : values()) {
            if (lang.code.equals(q)
                    || lang.name().equalsIgnoreCase(q)
                    || lang.displayName.equalsIgnoreCase(q)) {
                return lang;
            }
        }
        return null;
    }

    /** Stable persistence code ("en" / "hu"). */
    public String code() {
        return code;
    }
}
