package com.meridiantel.bss.ums.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MsisdnUtilTest {

    @Test
    public void testInternational() {
        assertEquals("442071234567", MsisdnUtil.normalize("442071234567", true, "SG"));
        assertEquals("442071234567", MsisdnUtil.normalize("+442071234567", false, "SG"));
        assertEquals("442071234567", MsisdnUtil.normalize("00442071234567", false, "SG"));
    }

    @Test
    public void testNational() {
        assertEquals("6591234567", MsisdnUtil.normalize("91234567", false, "SG"));
        assertEquals("6281234567890", MsisdnUtil.normalize("081234567890", false, "ID"));
    }

    @Test
    public void testShortCode() {
        assertEquals("1688", MsisdnUtil.normalize("1688", false, "SG"));
    }

    @Test
    public void testInvalid() {
        assertNull(MsisdnUtil.normalize("ABC", false, "SG"));
        assertNull(MsisdnUtil.normalize("", false, "SG"));
    }
}
