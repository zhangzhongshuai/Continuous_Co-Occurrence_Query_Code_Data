package owim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OWIM-NP, the ablation of Section V: OWIM without permanent pruning. The table is maintained with d = k = 1, so it
 * holds every MCP of the window; the answers (|S| >= d, |X| >= k) are filtered from the whole table at every frame,
 * when they are reported.
 */
public class OwimNoPrune {
    final Owim inner;
    final int d, k;

    public OwimNoPrune(int w, int d, int k) {
        this.inner = new Owim(w, 1, 1);
        this.d = d;
        this.k = k;
    }

    public List<ObjectSet> feed(ObjectSet frame) {
        inner.advance(frame);
        List<ObjectSet> out = new ArrayList<>();
        for (Owim.State s : inner.table.values())
            if (s.count >= k && Owim.pop(s.bits.v) >= d)
                out.add(s.out != null ? s.out : (s.out = inner.toObjectSet(s.ext)));
        return out.isEmpty() ? Collections.emptyList() : out;
    }

    /** Number of entries of the table, i.e., of MCPs of the window. */
    public int size() { return inner.size(); }
}
