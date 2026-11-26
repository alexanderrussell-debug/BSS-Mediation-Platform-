package com.meridiantel.bss.ums.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.nio.file.Files;
import java.util.Date;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.meridiantel.bss.ums.core.UsageRecord;

/**
 * Loads dedup.ser / pending.ser produced by the JDK 7 build of UMS 5.2.1 (Zulu 1.7.0_352, SampleCdrGenerator +
 * run-once) from src/test/resources/state/jdk7. Fails if a change to UsageRecord makes production state
 * unreadable (UMS-412).
 */
public class Jdk7StateCompatibilityTest {

    /** Default serialVersionUID of UsageRecord, computed by serialver on JDK 7 and JDK 21 builds. */
    static final long USAGE_RECORD_JDK7_SERIAL_VERSION_UID = 7311000903433512030L;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File fixture(String name) throws Exception {
        InputStream in = getClass().getResourceAsStream("/state/jdk7/" + name);
        assertNotNull(name, in);
        File f = new File(tmp.getRoot(), name);
        try {
            Files.copy(in, f.toPath());
        } finally {
            in.close();
        }
        return f;
    }

    @Test
    public void usageRecordSerialVersionUidUnchanged() {
        assertEquals("UsageRecord shape changed - pending.ser from production would no longer load (UMS-412)",
                USAGE_RECORD_JDK7_SERIAL_VERSION_UID, ObjectStreamClass.lookup(UsageRecord.class).getSerialVersionUID());
    }

    @Test
    public void loadsJdk7DedupState() throws Exception {
        DuplicateChecker d = new DuplicateChecker();
        d.setStateFile(fixture("dedup.ser").getPath());
        d.load();
        assertEquals(32, d.size());

        UsageRecord seen = new UsageRecord();
        seen.setRecordType("VMO");
        seen.setServedMsisdn("6591000001");
        seen.setStartTime(new Date(1795572060000L));
        seen.setDurationSec(120);
        seen.setNodeId("MSC-SG-01");
        assertTrue(d.isDuplicate(seen));

        UsageRecord data = new UsageRecord();
        data.setRecordType("GPR");
        data.setChargingId("10009");
        data.setPartialSeq(1);
        assertTrue(d.isDuplicate(data));
        data.setPartialSeq(2);
        assertFalse(d.isDuplicate(data));
        assertEquals(33, d.size());
    }

    @Test
    public void loadsJdk7PendingStateAndCompletesSession() throws Exception {
        PartialCdrAggregator a = new PartialCdrAggregator();
        a.setStateFile(fixture("pending.ser").getPath());
        a.load();
        assertEquals(1, a.pendingSessions());

        UsageRecord fin = new UsageRecord();
        fin.setSourceStream("PGW_SG");
        fin.setRecordType("GPR");
        fin.setChargingId("10009");
        fin.setPartialSeq(2);
        fin.setDurationSec(300);
        fin.setVolumeBytes(1000);
        fin.setPartialFinal(true);
        fin.setStartTime(new Date(1795575300000L));

        UsageRecord session = a.add(fin);
        assertNotNull(session);
        assertEquals(0, a.pendingSessions());
        assertEquals(2, session.getPartialCount());
        assertEquals(1200 + 300, session.getDurationSec());
        assertEquals(104857600L + 1000L, session.getVolumeBytes());
        assertEquals(1795574100000L, session.getStartTime().getTime());
    }
}
