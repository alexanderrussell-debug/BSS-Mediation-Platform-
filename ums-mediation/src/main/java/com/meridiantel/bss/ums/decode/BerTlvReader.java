package com.meridiantel.bss.ums.decode;

import java.io.IOException;
import java.io.InputStream;

/**
 * Minimal ASN.1 BER TLV reader for 3GPP TS 32.205-style MSC CDRs.
 * Hand-written in 2011 because the commercial ASN.1 compiler licence was not renewed.
 *
 * Limitations:
 *  - single-byte tags only (our MSC profile never uses high tag numbers)
 *  - definite length only (indefinite length 0x80 is NOT supported)
 */
public class BerTlvReader {

    private InputStream in;
    private long offset = 0;

    public BerTlvReader(InputStream in) {
        this.in = in;
    }

    public long getOffset() {
        return offset;
    }

    /** @return tag byte, or -1 at end of stream */
    public int readTag() throws IOException {
        int t = in.read();
        if (t >= 0) offset++;
        return t;
    }

    public int readLength() throws IOException {
        int b = read();
        if (b < 0x80) {
            return b;
        }
        if (b == 0x80) {
            throw new IOException("Indefinite length not supported at offset " + offset);
        }
        int n = b & 0x7F;
        if (n > 4) {
            throw new IOException("Length of length too big (" + n + ") at offset " + offset);
        }
        int len = 0;
        for (int i = 0; i < n; i++) {
            len = (len << 8) | read();
        }
        return len;
    }

    public byte[] readValue(int len) throws IOException {
        byte[] buf = new byte[len];
        int pos = 0;
        while (pos < len) {
            int r = in.read(buf, pos, len - pos);
            if (r < 0) {
                throw new IOException("Unexpected EOF: wanted " + len + " bytes, got " + pos + " at offset " + offset);
            }
            pos += r;
        }
        offset += len;
        return buf;
    }

    private int read() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new IOException("Unexpected EOF at offset " + offset);
        }
        offset++;
        return b;
    }

    // ------------------------------------------------------------------ value decoders

    public static boolean isConstructed(int tag) {
        return (tag & 0x20) != 0;
    }

    public static long decodeInteger(byte[] v) {
        long r = 0;
        for (int i = 0; i < v.length; i++) {
            r = (r << 8) | (v[i] & 0xFF);
        }
        return r;
    }

    /**
     * 3GPP AddressString: first octet TON/NPI, then TBCD digits (low nibble first, 0xF filler).
     * @return digits; caller checks isInternational(v)
     */
    public static String decodeAddress(byte[] v) {
        StringBuffer sb = new StringBuffer();
        for (int i = 1; i < v.length; i++) {
            int lo = v[i] & 0x0F;
            int hi = (v[i] >> 4) & 0x0F;
            if (lo == 0x0F) break;
            sb.append((char) ('0' + lo));
            if (hi == 0x0F) break;
            sb.append((char) ('0' + hi));
        }
        return sb.toString();
    }

    public static boolean isInternational(byte[] address) {
        return address.length > 0 && ((address[0] & 0x70) >> 4) == 1;
    }

    /**
     * 3GPP TimeStamp: 9 octets YYMMDDhhmmss (BCD) + sign (ASCII) + hhmm offset (BCD).
     * Returns "yyMMddHHmmss". The UTC offset is IGNORED - Ericsson R12 sends garbage
     * in it (UMS-77), so the stream timezone is used instead.
     */
    public static String decodeTimestamp(byte[] v) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 6 && i < v.length; i++) {
            int b = v[i] & 0xFF;
            sb.append((char) ('0' + ((b >> 4) & 0x0F)));
            sb.append((char) ('0' + (b & 0x0F)));
        }
        return sb.toString();
    }

    public static String decodeString(byte[] v) {
        try {
            return new String(v, "ISO-8859-1");
        } catch (Exception e) {
            return new String(v);
        }
    }

    public static String hex(byte[] v) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < v.length; i++) {
            String h = Integer.toHexString(v[i] & 0xFF);
            if (h.length() == 1) sb.append('0');
            sb.append(h);
        }
        return sb.toString().toUpperCase();
    }
}
