package owim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * OWIM: Algorithm 2 of the paper (Section IV), which answers the continuous co-occurrence query CQ(w, d, k).
 * The query starts with the stream, so the window and the table D start empty.
 *
 * Data structures:
 *   appearance bitmaps  F(o): circular encoding, frame i at bit pos(i) = i mod w, with |F(o)| kept in step;
 *                       only objects of the current window have one (bitmaps that become empty are recycled);
 *   table D             frame set S (bitmap) -> object set X; after every frame D = Ans_t;
 *   oldest-frame index  I[p]: the keys of D whose oldest frame is at position p (each key in exactly one list);
 *   frame ring          ring[pos(i)] = O_i, used only to find O_{t-w}.
 * Per frame t, p = t mod w:
 *   1. expiration (t >= w): clear bit p in F(o) for o in O_{t-w}, recycle empty bitmaps; trim the entries of I[p]
 *      (clear bit p, put X back with INSERT if |S'| >= d), then I[p] = {};
 *   2. arrival: set bit p in F(o) for o in O_t; run Algorithm 1 on {F(o) | o in O_t} with its own working table D';
 *      for each answer (X, S), remove S \ {t} from D and I if |D[S \ {t}]| = |X|, then INSERT(D, S, X).
 *   INSERT keeps the larger object set and registers a new key in I under its oldest frame.
 */
public class Owim {
    final int w, d, k, words;
    long time = 0;

    /** Objects of the current window, by id; an object leaves this map when its bitmap is recycled. */
    final Map<String, Obj> index = new HashMap<>();
    final Obj[][] ring;
    final Map<Bits, State> table = new HashMap<>();
    final ArrayList<State>[] oldest;

    /** dpOps: intersections in the arrival DP; expireOps: entries trimmed at expiration. */
    public long dpOps = 0, expireOps = 0;

    @SuppressWarnings("unchecked")
    public Owim(int w, int d, int k) {
        this.w = w;
        this.d = d;
        this.k = k;
        this.words = (w + 63) / 64;
        this.ring = new Obj[w][];
        this.oldest = new ArrayList[w];
        for (int i = 0; i < w; i++) oldest[i] = new ArrayList<>();
    }

    static final class Bits {
        final long[] v;
        final int hash;
        Bits(long[] v) { this.v = v; this.hash = Arrays.hashCode(v); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) { return o instanceof Bits && Arrays.equals(v, ((Bits) o).v); }
    }

    /** An object of the window: its circular appearance bitmap F(o) and |F(o)|. */
    static final class Obj {
        final String id, clazz;
        final long[] bits;
        int cnt;
        Obj(String id, String clazz, int words) { this.id = id; this.clazz = clazz; this.bits = new long[words]; }
    }

    /** An entry S -> X of D or D'. bucket/slot locate it in I (bucket = -1: not registered). */
    static final class State {
        Bits bits;
        int count;
        Obj[] ext;
        ObjectSet out;
        int bucket = -1, slot;
        State(Bits bits, int count, Obj[] ext) { this.bits = bits; this.count = count; this.ext = ext; }
    }

    static int pop(long[] a) { int c = 0; for (long x : a) c += Long.bitCount(x); return c; }
    static boolean test(long[] a, int p) { return (a[p >>> 6] & (1L << p)) != 0; }
    static void set(long[] a, int p) { a[p >>> 6] |= 1L << p; }
    static void clear(long[] a, int p) { a[p >>> 6] &= ~(1L << p); }

    /** Position of the oldest frame of a non-empty key: the first 1 at or after position q, cyclically
     *  (q = position of the oldest frame of the window), found with trailing-zero counts. */
    int oldestPos(long[] v, int q) {
        int qi = q >>> 6;
        long hi = -1L << (q & 63);
        long x = v[qi] & hi;
        if (x != 0) return (qi << 6) + Long.numberOfTrailingZeros(x);
        for (int j = qi + 1; j < words; j++) if (v[j] != 0) return (j << 6) + Long.numberOfTrailingZeros(v[j]);
        for (int j = 0; j < qi; j++) if (v[j] != 0) return (j << 6) + Long.numberOfTrailingZeros(v[j]);
        x = v[qi] & ~hi;
        if (x != 0) return (qi << 6) + Long.numberOfTrailingZeros(x);
        throw new IllegalStateException("empty key");
    }

    void register(State s, int q) {
        ArrayList<State> l = oldest[s.bucket = oldestPos(s.bits.v, q)];
        s.slot = l.size();
        l.add(s);
    }

    void unregister(State s) {
        ArrayList<State> l = oldest[s.bucket];
        State last = l.remove(l.size() - 1);
        if (last != s) { l.set(s.slot, last); last.slot = s.slot; }
        s.bucket = -1;
    }

    /** INSERT(D, S, X) of Algorithm 2, with S = s.bits and X = s.ext; q = position of the window's oldest frame. */
    void insert(State s, int q) {
        State ex = table.get(s.bits);
        if (ex == null) {
            table.put(s.bits, s);
            register(s, q);
        } else if (ex.count < s.count) {
            ex.count = s.count; ex.ext = s.ext; ex.out = s.out;
        }
    }

    /** Processes the next frame and returns the object sets of Ans_t; {@link #answers()} also gives their frames. */
    public List<ObjectSet> feed(ObjectSet frame) {
        advance(frame);
        if (table.isEmpty()) return Collections.emptyList();
        List<ObjectSet> out = new ArrayList<>(table.size());
        for (State s : table.values()) out.add(s.out != null ? s.out : (s.out = toObjectSet(s.ext)));
        return out;
    }

    /** Expiration and arrival of one frame. */
    void advance(ObjectSet frame) {
        int p = (int) (time % w);
        if (time >= w) expire(p);
        arrive(p, frame);
        time++;
    }

    void expire(int p) {
        int q = (p + 1) % w;                         // pos(t-w+1), the oldest frame of W_t
        for (Obj o : ring[p]) {                      // O_{t-w}
            clear(o.bits, p);
            if (--o.cnt == 0) index.remove(o.id);    // recycle
        }
        List<State> touched = oldest[p];
        for (State s : touched) table.remove(s.bits);
        for (State s : touched) {
            expireOps++;
            long[] nb = s.bits.v.clone();
            clear(nb, p);
            if (pop(nb) < d) continue;               // Rule 1
            s.bits = new Bits(nb);
            insert(s, q);                            // registers under a position other than p
        }
        oldest[p].clear();
    }

    void arrive(int p, ObjectSet frame) {
        int q = (p + 1) % w;
        // set bit p in the bitmaps of O_t (new bitmap for an object not in the window) and store O_t in the ring
        List<Obj> objs = new ArrayList<>();
        for (ObjectSet.ClassIds c : frame.classes)
            for (String id : c.ids) {
                Obj o = index.get(id);
                if (o == null) index.put(id, o = new Obj(id, c.clazz, words));
                if (!test(o.bits, p)) { set(o.bits, p); o.cnt++; objs.add(o); }
            }
        Obj[] cur = objs.toArray(new Obj[0]);
        ring[p] = cur;

        // Algorithm 1 on the objects of O_t, own working table D' (Rule 1: frames >= d; Rule 2: count + remaining >= k)
        List<Obj> cands = new ArrayList<>();
        for (Obj o : cur) if (o.cnt >= d) cands.add(o);
        Map<Bits, State> st = new HashMap<>();
        int r = cands.size();
        for (int i = 0; i < r; i++) {
            Obj c = cands.get(i);
            long[] b = c.bits;
            if (k > 1) {
                int rem = r - i;
                st.values().removeIf(s -> s.count + rem < k);
            }
            List<State> snap = new ArrayList<>(st.values());
            Bits single = new Bits(b.clone());
            if (!st.containsKey(single)) st.put(single, new State(single, 1, new Obj[]{c}));
            for (State s : snap) {
                dpOps++;
                long[] inter = new long[words];
                for (int j = 0; j < words; j++) inter[j] = s.bits.v[j] & b[j];
                if (pop(inter) < d) continue;
                Bits key = new Bits(inter);
                State ex = st.get(key);
                if (ex == null || ex.count < s.count + 1) {
                    Obj[] ext = Arrays.copyOf(s.ext, s.ext.length + 1);
                    ext[s.ext.length] = c;
                    st.put(key, new State(key, s.count + 1, ext));
                }
            }
        }
        // replacement test on S \ {t}, then INSERT (always a new key: no key of D contains bit p after expiration)
        for (State s : st.values()) {
            if (s.count < k) continue;
            long[] old = s.bits.v.clone();
            clear(old, p);
            Bits oldKey = new Bits(old);
            State os = table.get(oldKey);
            if (os != null && os.count == s.count) {
                table.remove(oldKey);
                unregister(os);
            }
            insert(s, q);
        }
    }

    ObjectSet toObjectSet(Obj[] ext) {
        Map<String, List<String>> byClass = new TreeMap<>();
        for (Obj o : ext) byClass.computeIfAbsent(o.clazz, x -> new ArrayList<>()).add(o.id);
        List<ObjectSet.ClassIds> list = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byClass.entrySet()) {
            Collections.sort(e.getValue());
            list.add(new ObjectSet.ClassIds(e.getKey(), e.getValue()));
        }
        return new ObjectSet(list);
    }

    /** The answers (X, S) of the current window. Decodes every key, so it is not part of the timed path. */
    public List<Answer> answers() {
        List<Answer> out = new ArrayList<>(table.size());
        for (State s : table.values()) out.add(new Answer(toObjectSet(s.ext), decode(s.bits.v)));
        return out;
    }

    /** Frame numbers of a key: the frame at position p is the one frame i of the window with i mod w = p. */
    int[] decode(long[] v) {
        int[] frames = new int[pop(v)];
        int n = 0;
        for (long i = Math.max(0, time - w); i < time; i++)
            if (test(v, (int) (i % w))) frames[n++] = (int) i;
        return frames;
    }

    /** Number of entries of D. */
    public int size() { return table.size(); }

    /** Consistency check (tests only): every key of D is in exactly one list of I, under its oldest frame;
     *  the live bitmaps are exactly the objects of the ring, with |F(o)| = popcount. */
    void check() {
        int q = (int) (time % w);                    // after frame time-1: oldest frame of the window is at time mod w
        if (time <= w) q = 0;
        int n = 0;
        for (int b = 0; b < w; b++)
            for (int i = 0; i < oldest[b].size(); i++) {
                State s = oldest[b].get(i);
                if (s.bucket != b || s.slot != i || table.get(s.bits) != s || oldestPos(s.bits.v, q) != b)
                    throw new IllegalStateException("index broken at t=" + (time - 1));
                n++;
            }
        if (n != table.size()) throw new IllegalStateException("index size at t=" + (time - 1));
        Set<Obj> live = new HashSet<>();
        for (Obj[] f : ring) if (f != null) live.addAll(Arrays.asList(f));
        if (!live.equals(new HashSet<>(index.values()))) throw new IllegalStateException("recycle at t=" + (time - 1));
        for (Obj o : live) if (o.cnt != pop(o.bits) || o.cnt == 0) throw new IllegalStateException("count at t=" + (time - 1));
    }
}
