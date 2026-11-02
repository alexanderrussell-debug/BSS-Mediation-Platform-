package com.meridiantel.bss.ums;

import org.apache.log4j.Logger;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.meridiantel.bss.ums.engine.MediationEngine;

/**
 * UMS entry point.
 * <pre>
 *   java -jar ums.jar daemon      # start with Quartz schedule (every 15 min)
 *   java -jar ums.jar run-once    # one cycle then exit (used by ops for re-runs)
 * </pre>
 */
public class Main {

    private static final Logger log = Logger.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "daemon";
        if ("run-once".equals(mode)) {
            System.setProperty("ums.scheduler.autoStart", "false");
        }
        log.info("UMS Usage Mediation System 5.2 starting in " + mode + " mode");

        final ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("ums-context.xml");
        ctx.registerShutdownHook();

        if ("run-once".equals(mode)) {
            MediationEngine engine = (MediationEngine) ctx.getBean("mediationEngine");
            engine.runAll();
            ctx.close();
            System.exit(0);
        }

        log.info("Daemon started, waiting for Quartz triggers");
        Object lock = new Object();
        synchronized (lock) {
            lock.wait();
        }
    }
}
