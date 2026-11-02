package com.meridiantel.bss.ums.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.meridiantel.bss.ums.core.ControlTotals;
import com.meridiantel.bss.ums.core.StreamConfig;
import com.meridiantel.bss.ums.core.StreamConfigLoader;

/**
 * Runs every configured stream in its own thread, then waits for all of them.
 * Triggered by Quartz every 15 minutes (or once from the command line).
 */
public class MediationEngine {

    private static final Logger log = Logger.getLogger(MediationEngine.class);

    private StreamConfigLoader streamConfigLoader;
    private StreamProcessor streamProcessor;
    private boolean running = false;

    public List runAll() {
        synchronized (this) {
            if (running) {
                log.warn("Previous run still active - skipping this cycle");
                return Collections.EMPTY_LIST;
            }
            running = true;
        }
        long start = System.currentTimeMillis();
        final List allTotals = Collections.synchronizedList(new ArrayList());
        try {
            List streams = streamConfigLoader.load();
            List threads = new ArrayList();
            for (int i = 0; i < streams.size(); i++) {
                final StreamConfig cfg = (StreamConfig) streams.get(i);
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            allTotals.addAll(streamProcessor.processStream(cfg));
                        } catch (Throwable e) {
                            log.error("Stream " + cfg.getName() + " died", e);
                        }
                    }
                }, "ums-" + cfg.getName());
                t.start();
                threads.add(t);
            }
            for (int i = 0; i < threads.size(); i++) {
                try {
                    ((Thread) threads.get(i)).join();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            long in = 0, out = 0, rej = 0;
            for (int i = 0; i < allTotals.size(); i++) {
                ControlTotals ct = (ControlTotals) allTotals.get(i);
                in += ct.input;
                out += ct.written;
                rej += ct.rejected;
            }
            log.info("Cycle done: " + allTotals.size() + " files, in=" + in + " out=" + out + " rej=" + rej + " in "
                    + (System.currentTimeMillis() - start) + "ms");
        } finally {
            synchronized (this) {
                running = false;
            }
        }
        return allTotals;
    }

    public void setStreamConfigLoader(StreamConfigLoader streamConfigLoader) { this.streamConfigLoader = streamConfigLoader; }
    public void setStreamProcessor(StreamProcessor streamProcessor) { this.streamProcessor = streamProcessor; }
}
