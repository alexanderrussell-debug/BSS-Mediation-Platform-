package com.meridiantel.bss.ums.process;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

/**
 * Read/write helpers for the Java-serialized state files in data/state (dedup.ser, pending.ser).
 *
 * An existing state file that cannot be read stops startup instead of silently starting empty (UMS-412):
 * an empty dedup store re-bills duplicates, an empty pending store loses partial data sessions.
 * To deliberately start empty, move the file aside, or start once with
 * -Dums.state.allowEmptyOnLoadFailure=true (the unreadable file is then renamed, never overwritten).
 */
public final class StateFiles {

    public static final String ALLOW_EMPTY_PROPERTY = "ums.state.allowEmptyOnLoadFailure";

    /** Grep-able marker for monitoring. */
    public static final String ALERT_TAG = "UMS-STATE-LOAD-FAILURE";

    private StateFiles() {
    }

    public static Object read(File f) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)));
        try {
            return in.readObject();
        } finally {
            in.close();
        }
    }

    /** Writes to a temp file then renames, so a kill during save never leaves a truncated state file. */
    public static void writeAtomically(File f, Object state) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        dir.mkdirs();
        File tmp = new File(dir, f.getName() + ".tmp");
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
        try {
            out.writeObject(state);
        } finally {
            out.close();
        }
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Called when f exists but could not be loaded. Throws (fails bean init, so UMS does not start) unless
     * the override property is set, in which case f is renamed aside and the caller starts empty.
     */
    static void handleLoadFailure(Logger log, File f, String what, Throwable cause) {
        String msg = ALERT_TAG + ": " + what + " state file " + f.getAbsolutePath()
                + " exists but cannot be loaded (" + cause + ")";
        if (!Boolean.getBoolean(ALLOW_EMPTY_PROPERTY)) {
            log.error(msg + ". Refusing to start with empty state. Fix or restore the file, or move it aside"
                    + " (or set -D" + ALLOW_EMPTY_PROPERTY + "=true) to accept starting empty.", cause);
            throw new IllegalStateException(msg, cause);
        }
        File aside = new File(f.getPath() + ".unreadable-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        if (!f.renameTo(aside)) {
            log.error(msg + ". Could not move it aside to " + aside, cause);
            throw new IllegalStateException(msg + "; could not move it aside to " + aside, cause);
        }
        log.error(msg + ". -D" + ALLOW_EMPTY_PROPERTY + "=true is set: STARTING EMPTY. Original kept as "
                + aside.getAbsolutePath(), cause);
    }
}
