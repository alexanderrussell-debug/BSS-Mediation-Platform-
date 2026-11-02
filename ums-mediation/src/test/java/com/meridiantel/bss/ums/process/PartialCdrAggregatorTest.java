package com.meridiantel.bss.ums.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Date;

import org.junit.Test;

import com.meridiantel.bss.ums.core.UsageRecord;

public class PartialCdrAggregatorTest {

    private UsageRecord partial(String cid, int seq, long dur, long vol, boolean fin, long startMs) {
        UsageRecord u = new UsageRecord();
        u.setSourceStream("PGW_TEST");
        u.setRecordType("GPR");
        u.setChargingId(cid);
        u.setPartialSeq(seq);
        u.setDurationSec(dur);
        u.setVolumeBytes(vol);
        u.setPartialFinal(fin);
        u.setStartTime(new Date(startMs));
        return u;
    }

    @Test
    public void testSingleFinalRecord() {
        PartialCdrAggregator a = new PartialCdrAggregator();
        UsageRecord r = a.add(partial("1", 1, 60, 1000, true, 0));
        assertNotNull(r);
        assertEquals(1000, r.getVolumeBytes());
        assertEquals(1, r.getPartialCount());
    }

    @Test
    public void testThreePartials() {
        PartialCdrAggregator a = new PartialCdrAggregator();
        assertNull(a.add(partial("2", 1, 3600, 100000, false, 1000)));
        assertNull(a.add(partial("2", 2, 3600, 200000, false, 2000)));
        assertEquals(1, a.pendingSessions());
        UsageRecord r = a.add(partial("2", 3, 100, 50000, true, 3000));
        assertEquals(7300, r.getDurationSec());
        assertEquals(350000, r.getVolumeBytes());
        assertEquals(3, r.getPartialCount());
        assertEquals(1000, r.getStartTime().getTime());
        assertEquals(0, a.pendingSessions());
    }

    @Test
    public void testOutOfOrderPartials() {
        PartialCdrAggregator a = new PartialCdrAggregator();
        assertNull(a.add(partial("3", 2, 10, 10, false, 2000)));
        UsageRecord r = a.add(partial("3", 1, 10, 10, true, 1000));
        // final arrived before partial 2 - accepted as is (UMS-233)
        assertEquals(1000, r.getStartTime().getTime());
    }
}
