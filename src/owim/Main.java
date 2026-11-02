package owim;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Command line of OWIM.
 *
 *   answers <file> <w> <d> <k> [single-class]
 *            prints the answers at every frame: frame, then |S|, S and X, tab-separated
 *   run     <file> <w> <d> <k> <method> <warm> <reps> [single-class] [trace=<tsv>]
 *            warm (0/1) untimed warm-up runs, then reps timed runs; prints one TSV line (see header)
 *   mem     <file> <w> <d> <k> <method> [single-class]
 *            one untimed run; full GC every ~1/50 of the stream; prints the peak live heap above the input
 *   header   prints the column names of run
 *
 * Methods: OWIM (Algorithm 2), OWIM-NP (OWIM without permanent pruning).
 * Timing covers feed() only, i.e., reading a frame and producing its answers.
 * maintained = entries of the table after the frame; accessed = entries trimmed at expiration plus intersections of
 * Algorithm 1.
 */
public class Main {
    static final String HEADER = "dataset\tw\td\tk\tmethod\twarm\treps\tframes\ttotal_s\ttotals_s\tmean_us\tp50_us\tp99_us"
            + "\tmax_us\tmaintained\taccessed\tanswers\tresults";

    interface Runner {
        List<ObjectSet> feed(ObjectSet f);
        long maintained();
        long accessed();
    }

    static Runner make(String m, int w, int d, int k) {
        switch (m) {
            case "OWIM": {
                final Owim b = new Owim(w, d, k);
                return new Runner() {
                    public List<ObjectSet> feed(ObjectSet f) { return b.feed(f); }
                    public long maintained() { return b.size(); }
                    public long accessed() { return b.dpOps + b.expireOps; }
                };
            }
            case "OWIM-NP": {
                final OwimNoPrune b = new OwimNoPrune(w, d, k);
                return new Runner() {
                    public List<ObjectSet> feed(ObjectSet f) { return b.feed(f); }
                    public long maintained() { return b.size(); }
                    public long accessed() { return b.inner.dpOps + b.inner.expireOps; }
                };
            }
            default: throw new IllegalArgumentException(m);
        }
    }

    static double pct(long[] sorted, double q) {
        if (sorted.length == 0) return 0;
        int i = (int) Math.ceil(q * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
    }

    public static void main(String[] a) throws IOException {
        if (a.length == 0) { System.err.println("usage: see the README"); System.exit(2); }
        String mode = a[0];
        if (mode.equals("header")) { System.out.println(HEADER); return; }
        String file = a[1];
        int w = Integer.parseInt(a[2]), d = Integer.parseInt(a[3]), k = Integer.parseInt(a[4]);
        int firstOpt = mode.equals("run") ? 8 : mode.equals("mem") ? 6 : 5;
        Set<String> opts = new HashSet<>(Arrays.asList(a).subList(Math.min(a.length, firstOpt), a.length));
        String trace = null;
        for (String o : opts) if (o.startsWith("trace=")) trace = o.substring(6);
        List<ObjectSet> frames = StreamReader.read(file);
        if (opts.contains("single-class")) frames = StreamReader.singleClass(frames);
        String name = file.replaceAll(".*[/\\\\]", "").replaceAll("\\.txt$", "") + (opts.contains("single-class") ? "+single-class" : "");
        int n = frames.size();

        if (mode.equals("answers")) {
            Owim o = new Owim(w, d, k);
            for (int t = 0; t < n; t++) {
                o.feed(frames.get(t));
                for (Answer x : o.answers())
                    System.out.printf("%d\t%d\t%s\t%s%n", t, x.frames.length, Arrays.toString(x.frames), x.objects);
            }
            return;
        }

        String m = a[5];
        if (mode.equals("mem")) {
            Runtime rt = Runtime.getRuntime();
            System.gc(); System.gc();
            long base = rt.totalMemory() - rt.freeMemory(), peak = 0;
            int every = Math.max(1, n / 50);
            Runner r = make(m, w, d, k);
            for (int t = 0; t < n; t++) {
                r.feed(frames.get(t));
                if ((t + 1) % every == 0 || t == n - 1) {
                    System.gc();
                    peak = Math.max(peak, rt.totalMemory() - rt.freeMemory() - base);
                }
            }
            System.out.printf("mem\t%s\t%d\t%d\t%d\t%s\t%d\t%d\t%d%n", name, w, d, k, m, n, peak, r.maintained());
            return;
        }

        // run
        int warm = Integer.parseInt(a[6]), reps = Integer.parseInt(a[7]);
        long[][] ns = new long[reps][n];
        double[] totals = new double[reps];
        long maint = 0, acc = 0, ans = 0;
        for (int r = -warm; r < reps; r++) {
            System.gc();
            Runner run = make(m, w, d, k);
            long mSum = 0, aSum = 0;
            long[] fr = r >= 0 ? ns[r] : new long[n];
            for (int t = 0; t < n; t++) {
                ObjectSet f = frames.get(t);
                long t0 = System.nanoTime();
                List<ObjectSet> res = run.feed(f);
                long t1 = System.nanoTime();
                fr[t] = t1 - t0;
                if (r == reps - 1) {                       // counters are deterministic; take them from the last run
                    mSum += run.maintained();
                    aSum += res.size();
                }
            }
            if (r >= 0) { long s = 0; for (long x : fr) s += x; totals[r] = s / 1e9; }
            if (r == reps - 1) { maint = mSum; ans = aSum; acc = run.accessed(); }
        }
        // per-frame median over the timed runs, then mean / p50 / p99 / max over frames
        long[] med = new long[n];
        long[] col = new long[reps];
        for (int t = 0; t < n; t++) {
            for (int r = 0; r < reps; r++) col[r] = ns[r][t];
            Arrays.sort(col);
            med[t] = col[reps / 2];
        }
        long[] sorted = med.clone();
        Arrays.sort(sorted);
        double mean = 0;
        for (long x : med) mean += x;
        mean /= n;
        double[] tot = totals.clone();
        Arrays.sort(tot);
        List<String> all = new ArrayList<>();
        for (double x : totals) all.add(String.format(Locale.ROOT, "%.6f", x));
        System.out.printf(Locale.ROOT, "%s\t%d\t%d\t%d\t%s\t%d\t%d\t%d\t%.6f\t%s\t%.3f\t%.3f\t%.3f\t%.3f\t%.2f\t%.2f\t%.2f\t%d%n",
                name, w, d, k, m, warm, reps, n, tot[reps / 2], String.join(",", all), mean / 1e3, pct(sorted, 0.5) / 1e3,
                pct(sorted, 0.99) / 1e3, sorted[n - 1] / 1e3, 1.0 * maint / n, 1.0 * acc / n, 1.0 * ans / n, ans);
        if (trace != null) {
            // per-frame counters of one more run, with the per-frame median time
            Runner run = make(m, w, d, k);
            try (PrintWriter pw = new PrintWriter(new FileWriter(trace))) {
                pw.println("frame\tns\tmaintained\taccessed\tanswers");
                long prev = 0;
                for (int t = 0; t < n; t++) {
                    List<ObjectSet> res = run.feed(frames.get(t));
                    long cur = run.accessed();
                    pw.printf("%d\t%d\t%d\t%d\t%d%n", t, med[t], run.maintained(), cur - prev, res.size());
                    prev = cur;
                }
            }
        }
    }
}
