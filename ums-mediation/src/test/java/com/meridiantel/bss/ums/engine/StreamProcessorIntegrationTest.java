package com.meridiantel.bss.ums.engine;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.meridiantel.bss.ums.audit.AuditLogger;
import com.meridiantel.bss.ums.collect.CollectorFactory;
import com.meridiantel.bss.ums.core.ControlTotals;
import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.StreamConfigLoader;
import com.meridiantel.bss.ums.decode.DecoderFactory;
import com.meridiantel.bss.ums.process.DuplicateChecker;
import com.meridiantel.bss.ums.process.FraudRule;
import com.meridiantel.bss.ums.process.PartialCdrAggregator;
import com.meridiantel.bss.ums.process.RecordEnricher;
import com.meridiantel.bss.ums.process.RecordFilter;
import com.meridiantel.bss.ums.process.RecordNormalizer;
import com.meridiantel.bss.ums.process.RecordValidator;
import com.meridiantel.bss.ums.reference.RatingZoneTable;
import com.meridiantel.bss.ums.reference.RoamingPartnerTable;
import com.meridiantel.bss.ums.reference.SubscriberCache;
import com.meridiantel.bss.ums.tools.SampleCdrGenerator;

/**
 * Runs the sample files through the whole chain (wired by hand - Spring test context
 * would need Quartz on the test classpath). Checks every file balances and produces output.
 */
public class StreamProcessorIntegrationTest {

    private File dataDir;
    private StreamProcessor processor;
    private List streams;

    @Before
    public void setUp() throws Exception {
        dataDir = new File(System.getProperty("java.io.tmpdir"), "ums-it-" + System.currentTimeMillis());
        dataDir.mkdirs();
        new SampleCdrGenerator(dataDir.getPath()).generateAll();

        SubscriberCache subs = new SubscriberCache();
        subs.setFile("refdata/subscribers.csv");
        subs.load();
        RatingZoneTable zones = new RatingZoneTable();
        zones.setFile("refdata/rating_zones.csv");
        zones.load();
        RoamingPartnerTable plmns = new RoamingPartnerTable();
        plmns.setFile("refdata/roaming_partners.csv");
        plmns.load();

        RecordEnricher enricher = new RecordEnricher();
        enricher.setSubscriberCache(subs);
        enricher.setRatingZoneTable(zones);
        enricher.setRoamingPartnerTable(plmns);

        RecordFilter filter = new RecordFilter();
        filter.setTestMsisdns("6590000001,6590000002");

        DuplicateChecker dedup = new DuplicateChecker();
        dedup.setStateFile(dataDir + "/state/dedup.ser");
        PartialCdrAggregator agg = new PartialCdrAggregator();
        agg.setStateFile(dataDir + "/state/pending.ser");
        AuditLogger audit = new AuditLogger();
        audit.setAuditDir(dataDir + "/audit");

        processor = new StreamProcessor();
        processor.setDataDir(dataDir.getPath());
        processor.setArchiveEnabled(false);
        processor.setCollectorFactory(new CollectorFactory());
        processor.setDecoderFactory(new DecoderFactory());
        processor.setValidator(new RecordValidator());
        processor.setNormalizer(new RecordNormalizer());
        processor.setDuplicateChecker(dedup);
        processor.setAggregator(agg);
        processor.setEnricher(enricher);
        processor.setFilter(filter);
        processor.setFraudRule(new FraudRule());
        processor.setAuditLogger(audit);

        Properties p = new Properties();
        p.setProperty("ums.streams", "MSC_SG,MSC_ID,PGW_SG,SMSC_SG");
        p.setProperty("stream.MSC_SG.type", "MSC_BER");
        p.setProperty("stream.MSC_ID.type", "MSC_BER");
        p.setProperty("stream.MSC_ID.timezone", "Asia/Jakarta");
        p.setProperty("stream.MSC_ID.country", "ID");
        p.setProperty("stream.PGW_SG.type", "PGW_CSV");
        p.setProperty("stream.SMSC_SG.type", "SMSC_PIPE");
        StreamConfigLoader loader = new StreamConfigLoader();
        loader.setProperties(p);
        loader.setDataDir(dataDir.getPath());
        streams = loader.load();
    }

    @Test
    public void testAllSampleFilesBalance() {
        List all = new ArrayList();
        for (int i = 0; i < streams.size(); i++) {
            all.addAll(processor.processStream((StreamConfig) streams.get(i)));
        }
        assertTrue("no files processed", all.size() >= 5);
        for (int i = 0; i < all.size(); i++) {
            ControlTotals ct = (ControlTotals) all.get(i);
            assertTrue(ct.toString(), ct.isBalanced());
            assertTrue(ct.toString(), "OK".equals(ct.status));
            assertTrue(ct.toString(), ct.input > 0);
        }
        String[] billing = new File(dataDir, "output/billing").list();
        assertTrue("billing files written", billing != null && billing.length >= 4);
    }
}
