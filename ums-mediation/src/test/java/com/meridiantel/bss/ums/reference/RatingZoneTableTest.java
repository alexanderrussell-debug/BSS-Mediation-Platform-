package com.meridiantel.bss.ums.reference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

public class RatingZoneTableTest {

    private RatingZoneTable table;

    @Before
    public void setUp() throws Exception {
        table = new RatingZoneTable();
        table.setFile("refdata/rating_zones.csv");
        table.load();
    }

    @Test
    public void testAsean() {
        assertEquals("INTL_ASEAN", table.lookup("60123456789"));
        assertEquals("INTL_ASEAN", table.lookup("639171234567"));
    }

    @Test
    public void testLongDistance() {
        assertEquals("INTL_EU", table.lookup("442071234567"));
        assertEquals("INTL_NA", table.lookup("14155550100"));
        assertEquals("INTL_SATELLITE", table.lookup("8821612345678"));
    }

    @Test
    public void testUnknown() {
        assertNull(table.lookup("999123"));
    }
}
