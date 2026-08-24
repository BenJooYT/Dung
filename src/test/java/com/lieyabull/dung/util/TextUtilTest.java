package com.lieyabull.dung.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextUtilTest {

    @Test
    void translatesColorCodes() {
        assertEquals("\u00A7aHello", TextUtil.translateAmp("&aHello"));
        assertEquals("A\u00A7lB\u00A7rC", TextUtil.translateAmp("A&lB&rC"));
        assertEquals("\u00A75x", TextUtil.translateAmp("&5x"));
    }

    @Test
    void translatesFormatAndHexMarkers() {
        assertEquals("\u00A7k\u00A7o\u00A7n\u00A7m", TextUtil.translateAmp("&k&o&n&m"));
        assertEquals("\u00A7x", TextUtil.translateAmp("&x"));
        assertEquals("\u00A7F\u00A7R", TextUtil.translateAmp("&F&R"));
    }

    @Test
    void leavesNonCodesAlone() {
        assertEquals("fish & chips", TextUtil.translateAmp("fish & chips"));
        assertEquals("AT&T rocks & rolls", TextUtil.translateAmp("AT&T rocks & rolls"));
    }

    @Test
    void passesThroughWhenNoAmpersand() {
        String s = "plain \u00A7a text";
        assertEquals(s, TextUtil.translateAmp(s));
        assertNull(TextUtil.translateAmp(null));
    }
}
