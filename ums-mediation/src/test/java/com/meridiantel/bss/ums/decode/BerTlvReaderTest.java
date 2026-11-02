package com.meridiantel.bss.ums.decode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class BerTlvReaderTest {

    @Test
    public void testShortAndLongLength() throws Exception {
        byte[] data = new byte[] { (byte) 0x80, 0x01, 0x05, (byte) 0x81, (byte) 0x81, (byte) 0x80 };
        BerTlvReader r = new BerTlvReader(new ByteArrayInputStream(data));
        assertEquals(0x80, r.readTag());
        assertEquals(1, r.readLength());
        assertEquals(5, BerTlvReader.decodeInteger(r.readValue(1)));
        assertEquals(0x81, r.readTag());
        assertEquals(128, r.readLength());
    }

    @Test
    public void testIndefiniteLengthRejected() {
        BerTlvReader r = new BerTlvReader(new ByteArrayInputStream(new byte[] { (byte) 0xA0, (byte) 0x80 }));
        try {
            r.readTag();
            r.readLength();
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().indexOf("Indefinite") >= 0);
        }
    }

    @Test
    public void testTbcdAddress() {
        // 0x91 international, digits 6591234567 -> 56 19 32 54 76
        byte[] v = new byte[] { (byte) 0x91, 0x56, 0x19, 0x32, 0x54, 0x76 };
        assertEquals("6591234567", BerTlvReader.decodeAddress(v));
        assertTrue(BerTlvReader.isInternational(v));
    }

    @Test
    public void testTbcdOddDigits() {
        // national, digits 1688 + filler... 3 digits "168" -> 61 F8
        byte[] v = new byte[] { (byte) 0x81, 0x61, (byte) 0xF8 };
        assertEquals("168", BerTlvReader.decodeAddress(v));
        assertFalse(BerTlvReader.isInternational(v));
    }

    @Test
    public void testTimestamp() {
        byte[] v = new byte[] { 0x24, 0x03, 0x15, 0x13, 0x45, 0x30, '+', 0x08, 0x00 };
        assertEquals("240315134530", BerTlvReader.decodeTimestamp(v));
    }
}
