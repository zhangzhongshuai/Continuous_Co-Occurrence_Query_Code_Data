package owim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tests that need nothing but this repository:
 *   1. the running example of the paper (Fig. 1), for CQ(5, 3, 3) and CQ(5, 2, 1);
 *   2. random streams: OWIM (X and S) and OWIM-NP (X) against a brute-force evaluation of the definition after every
 *      frame, plus the invariants of the oldest-frame index and of bitmap recycling. Every 4th stream has w up to 200,
 *      so keys span several words and the oldest-frame scan wraps around.
 *
 *   java -cp build owim.OwimTest [streams]      (default 4000)
 */
public class OwimTest {

    static ObjectSet frame(String... ids) {
        List<String> sorted = new ArrayList<>(Arrays.asList(ids));
        Collections.sort(sorted);
        List<ObjectSet.ClassIds> one = new ArrayList<>();
        one.add(new ObjectSet.ClassIds("car", sorted));
        return new ObjectSet(one);
    }

    /** Answers as "X -> S", sorted. */
    static Set<String> pairs(Owim o) {
        Set<String> out = new TreeSet<>();
        for (Answer a : o.answers()) out.add(String.join(",", a.objects.ids()) + " -> " + Arrays.toString(a.frames));
        return out;
    }

    static void expect(String what, Set<String> got, String... want) {
        Set<String> w = new TreeSet<>(Arrays.asList(want));
        if (!got.equals(w)) throw new AssertionError(what + ": expected " + w + ", got " + got);
    }

    static void runningExample() {
        ObjectSet[] fr = {frame("C"), frame("A", "B", "D"), frame("A", "B", "C"), frame("A", "B", "C", "D"),
                frame("A", "B", "D"), frame("A", "B", "D")};

        Owim q = new Owim(5, 3, 3);
        for (int t = 0; t <= 4; t++) { q.feed(fr[t]); q.check(); }
        expect("CQ(5,3,3) at t=4", pairs(q), "A,B,D -> [1, 3, 4]");
        q.feed(fr[5]); q.check();
        expect("CQ(5,3,3) at t=5", pairs(q), "A,B,D -> [1, 3, 4, 5]");

        q = new Owim(5, 2, 1);
        for (int t = 0; t <= 4; t++) { q.feed(fr[t]); q.check(); }
        expect("CQ(5,2,1) at t=4", pairs(q), "A,B -> [1, 2, 3, 4]", "A,B,D -> [1, 3, 4]", "C -> [0, 2, 3]",
                "A,B,C -> [2, 3]");
        q.feed(fr[5]); q.check();
        expect("CQ(5,2,1) at t=5", pairs(q), "A,B -> [1, 2, 3, 4, 5]", "A,B,D -> [1, 3, 4, 5]", "A,B,C -> [2, 3]");

        q = new Owim(5, 1, 1);                       // d = k = 1: the five MCPs of W_4 listed in Fig. 1
        for (int t = 0; t <= 4; t++) { q.feed(fr[t]); q.check(); }
        expect("MCPs of W_4", pairs(q), "A,B -> [1, 2, 3, 4]", "A,B,D -> [1, 3, 4]", "C -> [0, 2, 3]",
                "A,B,C -> [2, 3]", "A,B,C,D -> [3]");
    }

    /** All (X, S) of the window by definition: X is an intersection of frames of the window, S = F(X). */
    static Set<String> brute(List<Set<Integer>> fr, int t, int w, int d, int k) {
        int from = Math.max(0, t - w + 1);
        Set<Set<Integer>> base = new HashSet<>(fr.subList(from, t + 1));
        Set<Set<Integer>> closed = new HashSet<>(base), frontier = new HashSet<>(base);
        while (!frontier.isEmpty()) {
            Set<Set<Integer>> nxt = new HashSet<>();
            for (Set<Integer> x : frontier) for (Set<Integer> y : base) {
                Set<Integer> z = new TreeSet<>(x);
                z.retainAll(y);
                if (!z.isEmpty() && !closed.contains(z)) nxt.add(z);
            }
            closed.addAll(nxt);
            frontier = nxt;
        }
        Set<String> out = new TreeSet<>();
        for (Set<Integer> x : closed) {
            if (x.isEmpty() || x.size() < k) continue;
            List<Integer> s = new ArrayList<>();
            for (int i = from; i <= t; i++) if (fr.get(i).containsAll(x)) s.add(i);
            if (s.size() >= d) out.add(canon(x) + " -> " + s);
        }
        return out;
    }

    static String canon(Collection<Integer> x) {
        TreeSet<String> s = new TreeSet<>();
        for (int o : x) s.add("o" + o);
        return String.join(",", s);
    }

    static Set<String> objectSets(Set<String> pairs) {
        Set<String> out = new TreeSet<>();
        for (String p : pairs) out.add(p.substring(0, p.indexOf(" -> ")));
        return out;
    }

    static Set<String> objectSets(List<ObjectSet> res) {
        Set<String> out = new TreeSet<>();
        for (ObjectSet s : res) out.add(String.join(",", s.ids()));
        return out;
    }

    static void parser() {
        ObjectSet f = StreamReader.parse("person: < p2, p1, p2>; car: < c1>; dog: < d1>");
        Map<String, List<String>> got = new TreeMap<>();
        for (ObjectSet.ClassIds c : f.classes) got.put(c.clazz, c.ids);
        if (!got.toString().equals("{car=[c1], person=[p1, p2]}")) throw new AssertionError("parser: " + got);
        if (StreamReader.parse("") != null) throw new AssertionError("parser: blank line");
        if (StreamReader.parse("dog: < d1>").size() != 0) throw new AssertionError("parser: filtered frame");
    }

    public static void main(String[] a) {
        parser();
        runningExample();
        System.out.println("running example of the paper: OK");

        int n = a.length > 0 ? Integer.parseInt(a[0]) : 4000;
        long frames = 0, answers = 0;
        for (int seed = 0; seed < n; seed++) {
            Random rng = new Random(seed);
            int objs = 2 + rng.nextInt(7), len = 5 + rng.nextInt(seed % 4 == 0 ? 400 : 40);
            int w = 1 + rng.nextInt(Math.min(seed % 4 == 0 ? 200 : 10, len));
            int d = 1 + rng.nextInt(w), k = 1 + rng.nextInt(objs);
            double pr = 0.3 + 0.6 * rng.nextDouble();
            List<Set<Integer>> fr = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                Set<Integer> f = new TreeSet<>();
                for (int o = 0; o < objs; o++) if (rng.nextDouble() < pr) f.add(o);
                if (!f.isEmpty()) fr.add(f);                 // lines without objects take no frame number
            }
            Owim owim = new Owim(w, d, k);
            OwimNoPrune np = new OwimNoPrune(w, d, k);
            for (int t = 0; t < fr.size(); t++) {
                List<String> ids = new ArrayList<>();
                for (int o : fr.get(t)) ids.add("o" + o);
                ObjectSet f = frame(ids.toArray(new String[0]));
                Set<String> x1 = objectSets(owim.feed(f));
                Set<String> x2 = objectSets(np.feed(f));
                owim.check();
                np.inner.check();
                Set<String> truth = brute(fr, t, w, d, k);
                if (!pairs(owim).equals(truth) || !x1.equals(objectSets(truth)) || !x2.equals(objectSets(truth)))
                    throw new AssertionError(String.format("seed=%d t=%d w=%d d=%d k=%d%n truth=%s%n owim=%s%n owim-np=%s",
                            seed, t, w, d, k, truth, pairs(owim), x2));
                frames++;
                answers += truth.size();
            }
        }
        System.out.printf("random streams: %d streams, %d frames, %d answers: OWIM and OWIM-NP = brute force%n",
                n, frames, answers);
    }
}
