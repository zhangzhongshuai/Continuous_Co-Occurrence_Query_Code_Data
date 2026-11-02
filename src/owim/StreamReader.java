package owim;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Reads an object stream: one line per frame, e.g. {@code car: < car2, car1>; person: < person16, person9>}.
 * Only the classes car, person, truck and bus are kept. Lines without objects are skipped and do not take a frame
 * number, so frame t is the (t+1)-th line with objects.
 */
public final class StreamReader {
    private static final List<String> ALLOWED = Arrays.asList("car", "person", "truck", "bus");

    private StreamReader() { }

    public static List<ObjectSet> read(String file) throws IOException {
        List<ObjectSet> frames = new ArrayList<>();
        try (BufferedReader in = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            for (String line; (line = in.readLine()) != null; ) {
                ObjectSet frame = parse(line);
                if (frame != null) frames.add(frame);
            }
        }
        return frames;
    }

    /** Returns null for a line without objects. A frame whose classes are all filtered out stays, as an empty frame. */
    static ObjectSet parse(String line) {
        boolean any = false;
        List<ObjectSet.ClassIds> classes = new ArrayList<>();
        for (String fragment : line.split(";")) {
            String[] pair = fragment.split(":");
            if (pair.length != 2) break;
            String ids = pair[1].trim();
            if (ids.startsWith("<")) ids = ids.substring(1, ids.length() - 1).trim();
            if (ids.isEmpty()) continue;
            any = true;
            String clazz = pair[0].trim();
            if (!ALLOWED.contains(clazz)) continue;
            TreeSet<String> sorted = new TreeSet<>();
            for (String id : ids.split(",")) sorted.add(id.trim());
            classes.add(new ObjectSet.ClassIds(clazz, new ArrayList<>(sorted)));
        }
        if (!any) return null;
        classes.sort(Comparator.comparing((ObjectSet.ClassIds c) -> c.clazz));
        return new ObjectSet(classes);
    }

    /** Puts all objects of each frame into one class, so that the processing order is by identifier alone. */
    public static List<ObjectSet> singleClass(List<ObjectSet> frames) {
        List<ObjectSet> out = new ArrayList<>(frames.size());
        for (ObjectSet f : frames) {
            TreeSet<String> ids = f.ids();
            List<ObjectSet.ClassIds> one = new ArrayList<>();
            if (!ids.isEmpty()) one.add(new ObjectSet.ClassIds("object", new ArrayList<>(ids)));
            out.add(new ObjectSet(one));
        }
        return out;
    }
}
