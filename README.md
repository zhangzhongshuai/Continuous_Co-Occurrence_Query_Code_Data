# Continuous Co-Occurrence Query over Object Streams from Videos

This repository contains the implementation of **OWIM** (Object-Wise Incremental Maintenance) and the object streams used in our experiments.

Given a stream of frames, each with the set of objects detected in it, the continuous co-occurrence query `CQ(w, d, k)` reports, at every frame, all maximal co-occurrence patterns (MCPs) of the sliding window of the most recent `w` frames that have at least `k` objects and at least `d` frames. An MCP is a pair `(X, S)` of an object set `X` and the set `S` of all the frames in which the objects of `X` co-occur, where neither `X` nor `S` can be extended.

OWIM scans object by object instead of frame by frame, so it can prune by both `d` and `k` during maintenance. The table it maintains is exactly the answer set at every frame, and at each frame it processes only the entries that contain the expiring or the new frame.

## Repository layout

| Path | Content |
|---|---|
| `src/owim/Owim.java` | OWIM: Algorithm 2 of the paper, with Algorithm 1 as its arrival step |
| `src/owim/OwimNoPrune.java` | OWIM-NP, the ablation without permanent pruning |
| `src/owim/StreamReader.java`, `ObjectSet.java`, `Answer.java` | Input and output types |
| `src/owim/Main.java` | Command line and experiment runner |
| `test/owim/OwimTest.java` | Running example of the paper; random streams against brute force |
| `scripts/run_experiments.sh` | The OWIM and OWIM-NP runs of the experiments |
| `examples/running_example.txt` | The object stream of Fig. 1 of the paper |
| `data/` | The object streams of the experiments (57 files, 30 MB) |

## Requirements

A JDK (8 or later) and, for the scripts, a Bash shell. There are no other dependencies. We tested with Temurin 1.8.0_482 on Windows 11, using Git Bash.

## Quick start

```bash
./build.sh
java -cp build owim.OwimTest
java -cp build owim.Main answers examples/running_example.txt 5 3 3
```

`OwimTest` takes about two minutes and prints:

```text
running example of the paper: OK
random streams: 4000 streams, 259650 frames, 1271344 answers: OWIM and OWIM-NP = brute force
```

The last command runs `CQ(5, 3, 3)` on the running example and prints the frame, `|S|`, `S`, and `X` of every answer:

```text
4	3	[1, 3, 4]	car: <A, B, D>
5	4	[1, 3, 4, 5]	car: <A, B, D>
```

## Input format

One line per frame, with the objects of each class:

```text
car: < car2, car1, car12>; person: < person16, person9>
```

- Only the classes `car`, `person`, `truck`, and `bus` are kept.
- A line without objects is skipped and takes no frame number, so frame `t` is the `(t+1)`-th line with objects.
- The query starts with the stream, so the window grows until it holds `w` frames.

## Command line

```bash
java -cp build owim.Main answers <file> <w> <d> <k> [single-class]
java -Xmx6g -cp build owim.Main run <file> <w> <d> <k> <method> <warm> <reps> [single-class] [trace=<tsv>]
java -Xmx6g -cp build owim.Main mem <file> <w> <d> <k> <method> [single-class]
java -cp build owim.Main header
```

- `method` is `OWIM` or `OWIM-NP`.
- `run` makes `warm` (0 or 1) untimed warm-up runs and `reps` timed runs, then prints one TSV line. `header` prints the column names.
- `mem` makes one untimed run and prints the peak live heap above the input, in bytes.
- `single-class` puts all objects of a frame into one class. It is used in the experiment on `k`.

For example, on D2 with the default parameters:

```bash
java -Xmx6g -cp build owim.Main run data/MVI_40751-filtered-4.txt 300 240 1 OWIM 1 3
```

The main columns of the output are:

| Column | Meaning |
|---|---|
| `total_s`, `totals_s` | Median total time of the timed runs, and the time of each run (seconds); only `feed()` is timed |
| `mean_us`, `p50_us`, `p99_us`, `max_us` | Latency per frame (microseconds); the median over the timed runs is taken for each frame |
| `maintained` | Entries of the table per frame |
| `accessed` | Entries trimmed at expiration plus intersections of Algorithm 1, per frame |
| `answers`, `results` | Answers per frame, and in total |

## Reproducing the experiments

```bash
scripts/run_experiments.sh              # all experiments below
scripts/run_experiments.sh d k          # only some of them
```

Results go to `results/<experiment>.tsv`. Runs already recorded are skipped, so the script can be resumed. Each run uses a fresh JVM with 1 warm-up run and 3 timed runs (1 and 1 for the long videos); set `WARM` and `REPS` to change this for the other experiments.

| Experiment | Parameters | Input files | Methods |
|---|---|---|---|
| `d` | `d` = 1, 30, 60, 120, 180, 210, 240, 270 | `<video>-filtered-4.txt` | OWIM, OWIM-NP |
| `k` | `k` = 1, 2, 3, 5, 7, 9, with `d` = 30 | `<video>-filtered-4.txt`, `single-class` | OWIM |
| `w` | `w` = 400, 500, 600, 900, 1200, 1800 (300 is in `d`) | `<video>-filtered-4.txt` | OWIM |
| `po` | `p_o` = 0, 1, 2, 3 | `<video>-occ-<p_o>.txt` | OWIM |
| `pr` | `p_r` = 0.0, 0.1, 0.2, 0.3 | `<video>-filtered-4-reduced-<p_r>.txt` | OWIM |
| `mem` | default | `<video>-filtered-4.txt` | OWIM, OWIM-NP |
| `long` | default | `traffic1.txt`, `traffic2.txt`, `traffic3.txt` | OWIM, OWIM-NP |

Unless stated otherwise, the parameters are `w` = 300, `d` = 240, and `k` = 1.

### Baselines

This repository does not include the baselines of the paper (NAIVE, MFS, SSG, MFS_O, and SSG_O). They were proposed by Chen et al. [1], and our experiments ran the Java implementation provided by those authors, without modification. To compare against them, please obtain their implementation from its authors.

## Datasets

All object streams come from the data provided by Chen et al. [1], unchanged: we use their detection and tracking results (YOLOv3 and DeepSORT) and their files for the occlusion and removal experiments. They contain object classes and identifiers only, with no video frames.

| Name | File prefix | Source video |
|---|---|---|
| V1, V2 | `visualroad1`, `visualroad2` | Synthetic videos generated with the Visual Road benchmark |
| D1, D2 | `MVI_40171`, `MVI_40751` | UA-DETRAC, static cameras |
| M1, M2 | `MOT16-06`, `MOT16-13` | MOT16, moving cameras |
| L1, L2, L3 | `traffic1`, `traffic2`, `traffic3` | Long videos on YouTube: [wqctLW0Hb_0](https://www.youtube.com/watch?v=wqctLW0Hb_0), [e_WBuBqS9h8](https://www.youtube.com/watch?v=e_WBuBqS9h8), [Inbd-YOdPUE](https://www.youtube.com/watch?v=Inbd-YOdPUE) |

File variants of the six short videos:

- `<video>-filtered-4.txt`: the stream restricted to the four classes above, used by default.
- `<video>-occ-<p_o>.txt`: the identifier of an object that has left the view is reused at most `p_o` times by later objects of the same class, which simulates more occlusions.
- `<video>-filtered-4-reduced-<p_r>.txt`: a fraction `p_r` of the objects is randomly removed.

## References

[1] Yueting Chen, Xiaohui Yu, Nick Koudas, and Ziqiang Yu. Evaluating Temporal Queries Over Video Feeds. SIGMOD 2021.
