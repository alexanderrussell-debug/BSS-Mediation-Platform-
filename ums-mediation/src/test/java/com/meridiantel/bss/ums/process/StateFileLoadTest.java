package com.meridiantel.bss.ums.process;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.meridiantel.bss.ums.core.UsageRecord;

/** Startup behaviour when state/dedup.ser or state/pending.ser exists but cannot be loaded (UMS-412). */
public class StateFileLoadTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @After
    public void clearOverride() {
        System.clearProperty(StateFiles.ALLOW_EMPTY_PROPERTY);
    }

    private static UsageRecord partial(String cid, int seq) {
        UsageRecord u = new UsageRecord();
        u.setSourceStream("PGW_TEST");
        u.setRecordType("GPR");
        u.setChargingId(cid);
        u.setPartialSeq(seq);
        u.setDurationSec(60);
        u.setVolumeBytes(1000);
        u.setPartialFinal(false);
        u.setStartTime(new Date(1000L * seq));
        return u;
    }

    private static UsageRecord voice(String msisdn) {
        UsageRecord u = new UsageRecord();
        u.setRecordType("VMO");
        u.setServedMsisdn(msisdn);
        u.setStartTime(new Date(0));
        u.setDurationSec(60);
        u.setNodeId("MSC-T");
        return u;
    }

    private File write(String name, byte[] content) throws IOException {
        File f = new File(tmp.getRoot(), name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(content);
        out.close();
        return f;
    }

    private static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(o);
        out.close();
        return bos.toByteArray();
    }

    private DuplicateChecker checker(File f) {
        DuplicateChecker d = new DuplicateChecker();
        d.setStateFile(f.getPath());
        return d;
    }

    private PartialCdrAggregator aggregator(File f) {
        PartialCdrAggregator a = new PartialCdrAggregator();
        a.setStateFile(f.getPath());
        return a;
    }

    private void assertDedupLoadFails(File f) throws IOException {
        byte[] before = Files.readAllBytes(f.toPath());
        try {
            checker(f).load();
            fail("expected startup failure");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith(StateFiles.ALERT_TAG));
        }
        assertArrayEquals("state file must be left untouched", before, Files.readAllBytes(f.toPath()));
    }

    private void assertPendingLoadFails(File f) throws IOException {
        byte[] before = Files.readAllBytes(f.toPath());
        try {
            aggregator(f).load();
            fail("expected startup failure");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith(StateFiles.ALERT_TAG));
        }
        assertArrayEquals("state file must be left untouched", before, Files.readAllBytes(f.toPath()));
    }

    /** pending.ser written by a UsageRecord whose serialVersionUID differs - what UMS-412 looks like. */
    private static byte[] pendingWithForeignSerialVersionUid() throws IOException {
        Map m = new HashMap();
        List parts = new ArrayList();
        parts.add(partial("1", 1));
        m.put("PGW_TEST|1", parts);
        byte[] b = serialize(m);
        long uid = ObjectStreamClass.lookup(UsageRecord.class).getSerialVersionUID();
        for (int i = 0; i + 8 <= b.length; i++) {
            long v = 0;
            for (int j = 0; j < 8; j++) {
                v = (v << 8) | (b[i + j] & 0xff);
            }
            if (v == uid) {
                b[i + 7] ^= 0x01;
                return b;
            }
        }
        throw new IllegalStateException("serialVersionUID not found in stream");
    }

    // ---------------------------------------------------------------- dedup.ser

    @Test
    public void dedupMissingFileStartsEmpty() {
        DuplicateChecker d = checker(new File(tmp.getRoot(), "dedup.ser"));
        d.load();
        assertEquals(0, d.size());
    }

    @Test
    public void dedupRoundTrip() {
        File f = new File(tmp.getRoot(), "state/dedup.ser");
        DuplicateChecker d = checker(f);
        d.load();
        assertFalse(d.isDuplicate(voice("6591000001")));
        d.save();
        assertTrue(f.exists());
        assertFalse(new File(f.getPath() + ".tmp").exists());

        DuplicateChecker d2 = checker(f);
        d2.load();
        assertEquals(1, d2.size());
        assertTrue(d2.isDuplicate(voice("6591000001")));
    }

    @Test
    public void dedupCorruptFileFailsStartup() throws IOException {
        assertDedupLoadFails(write("dedup.ser", "not a serialized object".getBytes("ISO-8859-1")));
    }

    @Test
    public void dedupEmptyFileFailsStartup() throws IOException {
        assertDedupLoadFails(write("dedup.ser", new byte[0]));
    }

    @Test
    public void dedupTruncatedFileFailsStartup() throws IOException {
        HashSet keys = new HashSet();
        for (int i = 0; i < 100; i++) {
            keys.add("VMO|65910000" + i + "|0|60|MSC-T");
        }
        byte[] b = serialize(keys);
        byte[] truncated = new byte[b.length / 2];
        System.arraycopy(b, 0, truncated, 0, truncated.length);
        assertDedupLoadFails(write("dedup.ser", truncated));
    }

    @Test
    public void dedupWrongTypeFailsStartup() throws IOException {
        assertDedupLoadFails(write("dedup.ser", serialize(new HashMap())));
    }

    @Test
    public void dedupOverrideStartsEmptyAndKeepsOriginal() throws IOException {
        byte[] garbage = "garbage".getBytes("ISO-8859-1");
        File f = write("dedup.ser", garbage);
        System.setProperty(StateFiles.ALLOW_EMPTY_PROPERTY, "true");
        DuplicateChecker d = checker(f);
        d.load();
        assertEquals(0, d.size());
        assertFalse(f.exists());
        File[] aside = tmp.getRoot().listFiles();
        assertEquals(1, aside.length);
        assertTrue(aside[0].getName(), aside[0].getName().startsWith("dedup.ser.unreadable-"));
        assertArrayEquals(garbage, Files.readAllBytes(aside[0].toPath()));

        d.save();
        assertTrue(f.exists());
        DuplicateChecker d2 = checker(f);
        d2.load();
        assertEquals(0, d2.size());
    }

    // ---------------------------------------------------------------- pending.ser

    @Test
    public void pendingMissingFileStartsEmpty() {
        PartialCdrAggregator a = aggregator(new File(tmp.getRoot(), "pending.ser"));
        a.load();
        assertEquals(0, a.pendingSessions());
    }

    @Test
    public void pendingRoundTrip() {
        File f = new File(tmp.getRoot(), "state/pending.ser");
        PartialCdrAggregator a = aggregator(f);
        a.load();
        assertNull(a.add(partial("7", 1)));
        a.save();
        assertFalse(new File(f.getPath() + ".tmp").exists());

        PartialCdrAggregator a2 = aggregator(f);
        a2.load();
        assertEquals(1, a2.pendingSessions());
        UsageRecord fin = partial("7", 2);
        fin.setPartialFinal(true);
        UsageRecord session = a2.add(fin);
        assertEquals(2, session.getPartialCount());
        assertEquals(120, session.getDurationSec());
        assertEquals(1000, session.getStartTime().getTime());
    }

    @Test
    public void pendingCorruptFileFailsStartup() throws IOException {
        assertPendingLoadFails(write("pending.ser", "not a serialized object".getBytes("ISO-8859-1")));
    }

    @Test
    public void pendingEmptyFileFailsStartup() throws IOException {
        assertPendingLoadFails(write("pending.ser", new byte[0]));
    }

    @Test
    public void pendingIncompatibleClassFailsStartup() throws IOException {
        assertPendingLoadFails(write("pending.ser", pendingWithForeignSerialVersionUid()));
    }

    @Test
    public void pendingWrongTypeFailsStartup() throws IOException {
        assertPendingLoadFails(write("pending.ser", serialize(new HashSet())));
    }

    @Test
    public void pendingWrongContentFailsStartup() throws IOException {
        Map m = new HashMap();
        m.put("PGW_TEST|1", "not a list");
        assertPendingLoadFails(write("pending.ser", serialize(m)));
    }

    @Test
    public void pendingOverrideStartsEmptyAndKeepsOriginal() throws IOException {
        byte[] incompatible = pendingWithForeignSerialVersionUid();
        File f = write("pending.ser", incompatible);
        System.setProperty(StateFiles.ALLOW_EMPTY_PROPERTY, "true");
        PartialCdrAggregator a = aggregator(f);
        a.load();
        assertEquals(0, a.pendingSessions());
        assertFalse(f.exists());
        File[] aside = tmp.getRoot().listFiles();
        assertEquals(1, aside.length);
        assertTrue(aside[0].getName(), aside[0].getName().startsWith("pending.ser.unreadable-"));
        assertArrayEquals(incompatible, Files.readAllBytes(aside[0].toPath()));
    }
}
