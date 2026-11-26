package com.meridiantel.bss.ums.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.meridiantel.bss.ums.core.UsageRecord;
import com.meridiantel.bss.ums.process.StateFiles;

/**
 * Read-only check of the state directory, for the Java 21 cutover runbook (docs/JAVA21_CUTOVER.md).
 * Never writes to the state directory.
 *
 *   java -cp ums.jar com.meridiantel.bss.ums.tools.StateFileTool check ./data/state
 *   java -cp ums.jar com.meridiantel.bss.ums.tools.StateFileTool dump  ./data/state   # also lists keys/partials
 *
 * Exit code 0 if every state file present can be loaded, 2 if any cannot, 1 on usage error.
 */
public class StateFileTool {

    public static void main(String[] args) {
        if (args.length < 1 || !("check".equals(args[0]) || "dump".equals(args[0]))) {
            System.err.println("usage: StateFileTool check|dump [stateDir]");
            System.exit(1);
        }
        boolean dump = "dump".equals(args[0]);
        File dir = new File(args.length > 1 ? args[1] : "./data/state");
        System.out.println("java.version=" + System.getProperty("java.version") + " stateDir=" + dir.getAbsolutePath());
        boolean ok = checkDedup(new File(dir, "dedup.ser"), dump);
        ok &= checkPending(new File(dir, "pending.ser"), dump);
        ok &= checkSequences(dir);
        System.out.println(ok ? "RESULT OK" : "RESULT FAILED");
        System.exit(ok ? 0 : 2);
    }

    private static boolean checkDedup(File f, boolean dump) {
        if (!f.exists()) {
            System.out.println("dedup.ser: not present (UMS would start with an empty dedup store)");
            return true;
        }
        try {
            Collection keys = (Collection) StateFiles.read(f);
            System.out.println("dedup.ser: OK " + keys.size() + " keys (" + f.length() + " bytes, " + keys.getClass().getName() + ")");
            if (dump) {
                List sorted = new ArrayList(keys);
                Collections.sort(sorted);
                for (int i = 0; i < sorted.size(); i++) {
                    System.out.println("  " + sorted.get(i));
                }
            }
            return true;
        } catch (Exception e) {
            System.out.println("dedup.ser: FAILED " + e);
            return false;
        }
    }

    private static boolean checkPending(File f, boolean dump) {
        if (!f.exists()) {
            System.out.println("pending.ser: not present");
            return true;
        }
        try {
            Map pending = (Map) StateFiles.read(f);
            int partials = 0;
            Date oldest = null;
            List lines = new ArrayList();
            for (Iterator it = pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry e = (Map.Entry) it.next();
                List parts = (List) e.getValue();
                for (int i = 0; i < parts.size(); i++) {
                    UsageRecord u = (UsageRecord) parts.get(i);
                    partials++;
                    if (u.getStartTime() != null && (oldest == null || u.getStartTime().before(oldest))) {
                        oldest = u.getStartTime();
                    }
                    lines.add("  " + e.getKey() + " #" + u.getPartialSeq() + " " + u);
                }
            }
            System.out.println("pending.ser: OK " + pending.size() + " sessions, " + partials + " partials, oldest start "
                    + (oldest == null ? "-" : utc(oldest)) + " (" + f.length() + " bytes)");
            if (dump) {
                Collections.sort(lines);
                for (int i = 0; i < lines.size(); i++) {
                    System.out.println(lines.get(i));
                }
            }
            return true;
        } catch (Exception e) {
            System.out.println("pending.ser: FAILED " + e);
            return false;
        }
    }

    private static boolean checkSequences(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            System.out.println("state directory not readable: " + dir);
            return false;
        }
        List names = new ArrayList();
        for (int i = 0; i < files.length; i++) {
            String n = files[i].getName();
            if (n.startsWith("seq_") && n.endsWith(".txt")) {
                names.add(n);
            }
        }
        Collections.sort(names);
        boolean ok = true;
        for (int i = 0; i < names.size(); i++) {
            String n = (String) names.get(i);
            try {
                BufferedReader r = new BufferedReader(new FileReader(new File(dir, n)));
                try {
                    String line = r.readLine();
                    System.out.println(n + ": " + (line == null ? "empty (UMS restarts numbering at 1)" : String.valueOf(Long.parseLong(line.trim()))));
                } finally {
                    r.close();
                }
            } catch (Exception e) {
                System.out.println(n + ": FAILED " + e);
                ok = false;
            }
        }
        return ok;
    }

    private static String utc(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }
}
