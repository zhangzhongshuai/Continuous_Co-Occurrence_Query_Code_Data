package owim;

/** An answer (X, S) of the query: an MCP with |X| >= k and |S| >= d. */
public final class Answer {
    /** The object set X. */
    public final ObjectSet objects;
    /** The frame set S, as increasing frame numbers. */
    public final int[] frames;

    Answer(ObjectSet objects, int[] frames) {
        this.objects = objects;
        this.frames = frames;
    }
}
