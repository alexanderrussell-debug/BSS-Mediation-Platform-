import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * SerCompare a.ser b.ser  (classpath: verify/java classes + UMS fat jar)
 * Deserialises two UMS state files (dedup.ser HashSet / pending.ser HashMap) on the running JVM and
 * compares their CONTENT (set membership, map keys, every field of every UsageRecord, recursively).
 * Byte-identical .ser files are not expected across JDKs: HashSet/HashMap serialise their bucket count
 * and iterate in hash-bucket order, which changed between JDK 7 and 8+. Exit 0 = same content.
 * Java 7 source so it runs on both runtimes (also proves either JDK can read the other's files).
 */
public class SerCompare {
    public static void main(String[] args) throws Exception {
        Object a, b;
        try { a = read(args[0]); b = read(args[1]); }
        catch (Throwable e) { System.out.println("READ ERROR on java " + System.getProperty("java.version") + ": " + e); System.exit(2); return; }
        List<String> diffs = new ArrayList<String>();
        cmp("", a, b, diffs);
        boolean sameOrder = order(a).equals(order(b));
        if (diffs.isEmpty()) {
            System.out.println("same content: " + describe(a) + ", iteration order " + (sameOrder ? "same" : "different")
                    + " (read on java " + System.getProperty("java.version") + ")");
            System.exit(0);
        }
        System.out.println("CONTENT DIFFERS: " + diffs.size() + " difference(s): " + diffs.subList(0, Math.min(5, diffs.size())));
        System.exit(1);
    }
    static Object read(String f) throws Exception {
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)));
        try { return in.readObject(); } finally { in.close(); }
    }
    static String describe(Object o) {
        if (o instanceof Collection) return o.getClass().getSimpleName() + " of " + ((Collection) o).size();
        if (o instanceof Map) return o.getClass().getSimpleName() + " of " + ((Map) o).size();
        return String.valueOf(o);
    }
    static List<String> order(Object o) {
        List<String> l = new ArrayList<String>();
        if (o instanceof Collection) for (Object x : (Collection) o) l.add(String.valueOf(x));
        if (o instanceof Map) for (Object x : ((Map) o).keySet()) l.add(String.valueOf(x));
        return l;
    }
    static boolean simple(Object o) {
        return o instanceof String || o instanceof Number || o instanceof Boolean || o instanceof Character
                || o instanceof Date || o instanceof Enum || o instanceof Class;
    }
    static void cmp(String path, Object a, Object b, List<String> d) throws Exception {
        if (a == null || b == null) { if (a != b) d.add(path + ": " + a + " vs " + b); return; }
        if (a.getClass() != b.getClass()) { d.add(path + ": class " + a.getClass() + " vs " + b.getClass()); return; }
        if (simple(a)) { if (!a.equals(b)) d.add(path + ": " + a + " vs " + b); return; }
        if (a instanceof Set) {
            Set sa = (Set) a, sb = (Set) b;
            if (!sa.equals(sb)) d.add(path + ": set differs, size " + sa.size() + " vs " + sb.size());
            return;
        }
        if (a instanceof Map) {
            Map ma = (Map) a, mb = (Map) b;
            if (!ma.keySet().equals(mb.keySet())) { d.add(path + ": keys differ " + ma.keySet() + " vs " + mb.keySet()); return; }
            for (Object k : ma.keySet()) cmp(path + "[" + k + "]", ma.get(k), mb.get(k), d);
            return;
        }
        if (a instanceof List) {
            List la = (List) a, lb = (List) b;
            if (la.size() != lb.size()) { d.add(path + ": list size " + la.size() + " vs " + lb.size()); return; }
            for (int i = 0; i < la.size(); i++) cmp(path + "[" + i + "]", la.get(i), lb.get(i), d);
            return;
        }
        if (a.getClass().isArray()) {
            int n = Array.getLength(a);
            if (n != Array.getLength(b)) { d.add(path + ": array length"); return; }
            for (int i = 0; i < n; i++) cmp(path + "[" + i + "]", Array.get(a, i), Array.get(b, i), d);
            return;
        }
        for (Class c = a.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                cmp(path + "." + f.getName(), f.get(a), f.get(b), d);
            }
        }
    }
}
