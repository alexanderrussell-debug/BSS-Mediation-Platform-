package com.meridiantel.bss.ums.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.audit.AuditLogger;
import com.meridiantel.bss.ums.collect.CollectorFactory;
import com.meridiantel.bss.ums.collect.FileCollector;
import com.meridiantel.bss.ums.core.ControlTotals;
import com.meridiantel.bss.ums.core.RejectException;
import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.UmsConstants;
import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.decode.Decoder;
import com.meridiantel.bss.ums.decode.DecoderFactory;
import com.meridiantel.bss.ums.output.OutputSet;
import com.meridiantel.bss.ums.process.DuplicateChecker;
import com.meridiantel.bss.ums.process.FraudRule;
import com.meridiantel.bss.ums.process.PartialCdrAggregator;
import com.meridiantel.bss.ums.process.RecordEnricher;
import com.meridiantel.bss.ums.process.RecordFilter;
import com.meridiantel.bss.ums.process.RecordNormalizer;
import com.meridiantel.bss.ums.process.RecordValidator;
import com.meridiantel.bss.ums.util.FileUtil;

/**
 * The mediation chain for one stream:
 * collect -> decode -> validate -> normalise -> dedup -> aggregate -> enrich -> filter -> fraud -> distribute -> audit -> archive
 */
public class StreamProcessor {

    private static final Logger log = Logger.getLogger(StreamProcessor.class);

    private String dataDir;
    private boolean archiveEnabled = true;

    private CollectorFactory collectorFactory;
    private DecoderFactory decoderFactory;
    private RecordValidator validator;
    private RecordNormalizer normalizer;
    private DuplicateChecker duplicateChecker;
    private PartialCdrAggregator aggregator;
    private RecordEnricher enricher;
    private RecordFilter filter;
    private FraudRule fraudRule;
    private AuditLogger auditLogger;

    /** @return control totals of the files processed */
    public List processStream(StreamConfig cfg) {
        List totals = new ArrayList();
        List files;
        String workDir = dataDir + "/work/" + cfg.getName();
        try {
            FileCollector c = collectorFactory.getCollector(cfg.getCollector());
            files = c.collect(cfg, workDir);
        } catch (Exception e) {
            log.error(cfg.getName() + ": collection failed", e);
            return totals;
        }
        for (int i = 0; i < files.size(); i++) {
            totals.add(processFile(cfg, (File) files.get(i)));
        }
        return totals;
    }

    public ControlTotals processFile(StreamConfig cfg, File file) {
        ControlTotals ct = new ControlTotals();
        ct.stream = cfg.getName();
        ct.fileName = file.getName();
        log.info(cfg.getName() + ": processing " + file.getName());

        OutputSet out = null;
        try {
            out = OutputSet.open(dataDir, cfg);
            Decoder decoder = decoderFactory.getDecoder(cfg.getType());
            List records = decoder.decode(file, cfg);

            for (int i = 0; i < records.size(); i++) {
                UsageRecord u = (UsageRecord) records.get(i);
                ct.input++;
                try {
                    validator.validate(u);
                    normalizer.normalize(u, cfg);

                    if (duplicateChecker.isDuplicate(u)) {
                        ct.duplicates++;
                        continue;
                    }

                    if (UmsConstants.RT_DATA.equals(u.getRecordType())) {
                        UsageRecord session = aggregator.add(u);
                        if (session == null) {
                            ct.held++;
                            continue;
                        }
                        u = session;
                    }

                    enricher.enrich(u, cfg);

                    String reason = filter.filter(u);
                    if (reason != null) {
                        ct.filtered++;
                        log.debug("Filtered (" + reason + "): " + u);
                        continue;
                    }

                    if (fraudRule.isSuspicious(u)) {
                        out.fraud.write(u);
                        ct.fraud++;
                    }

                    out.dwh.write(u);
                    if (UmsConstants.PREPAID.equals(u.getPaymentType())) {
                        out.ocs.write(u);
                    } else {
                        out.billing.write(u);
                    }
                    ct.written++;
                    ct.totalDurationSec += u.getDurationSec();
                    ct.totalVolumeBytes += u.getVolumeBytes();

                } catch (RejectException re) {
                    ct.rejected++;
                    out.reject.write(u, re);
                }
            }
        } catch (Exception e) {
            log.error(cfg.getName() + ": error processing " + file.getName(), e);
            ct.status = "ERROR: " + e.getMessage();
        } finally {
            if (out != null) {
                out.closeAll();
            }
        }

        ct.endMs = System.currentTimeMillis();
        auditLogger.write(ct);
        if (!ct.isBalanced()) {
            log.warn("CONTROL TOTALS DO NOT BALANCE: " + ct);
        } else {
            log.info(ct.toString());
        }

        duplicateChecker.save();
        aggregator.save();

        if (ct.status.startsWith("ERROR")) {
            FileUtil.moveTo(file, dataDir + "/error/" + cfg.getName());
        } else if (archiveEnabled) {
            FileUtil.archive(file, dataDir + "/archive/" + cfg.getName());
        }
        return ct;
    }

    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public void setArchiveEnabled(boolean archiveEnabled) { this.archiveEnabled = archiveEnabled; }
    public void setCollectorFactory(CollectorFactory collectorFactory) { this.collectorFactory = collectorFactory; }
    public void setDecoderFactory(DecoderFactory decoderFactory) { this.decoderFactory = decoderFactory; }
    public void setValidator(RecordValidator validator) { this.validator = validator; }
    public void setNormalizer(RecordNormalizer normalizer) { this.normalizer = normalizer; }
    public void setDuplicateChecker(DuplicateChecker duplicateChecker) { this.duplicateChecker = duplicateChecker; }
    public void setAggregator(PartialCdrAggregator aggregator) { this.aggregator = aggregator; }
    public void setEnricher(RecordEnricher enricher) { this.enricher = enricher; }
    public void setFilter(RecordFilter filter) { this.filter = filter; }
    public void setFraudRule(FraudRule fraudRule) { this.fraudRule = fraudRule; }
    public void setAuditLogger(AuditLogger auditLogger) { this.auditLogger = auditLogger; }
}
