package owim;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** A set of objects grouped by class: the objects O_t of a frame, or the object set X of an answer. */
public final class ObjectSet {

    /** The objects of one class, with sorted identifiers. */
    public static final class ClassIds {
        public final String clazz;
        public final List<String> ids;

        public ClassIds(String clazz, List<String> ids) {
            this.clazz = clazz;
            this.ids = ids;
        }
    }

    public final List<ClassIds> classes;

    public ObjectSet(List<ClassIds> classes) {
        this.classes = classes;
    }

    /** All identifiers, sorted. */
    public TreeSet<String> ids() {
        TreeSet<String> out = new TreeSet<>();
        for (ClassIds c : classes) out.addAll(c.ids);
        return out;
    }

    public int size() {
        int n = 0;
        for (ClassIds c : classes) n += c.ids.size();
        return n;
    }

    /** Same format as the input files, e.g. {@code car: <car1, car2>; person: <person7>}. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        for (ClassIds c : classes) parts.add(c.clazz + ": <" + String.join(", ", c.ids) + ">");
        return String.join("; ", parts);
    }
}
