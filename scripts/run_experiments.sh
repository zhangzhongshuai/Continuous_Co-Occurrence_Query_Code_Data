#!/usr/bin/env bash
# The runs of OWIM and OWIM-NP in the experiments of the paper (Section V). Every run is a fresh JVM. Results go to
# results/<exp>.tsv, one line per run (columns: see `java -cp build owim.Main header`); a run that hits its time limit
# is recorded as TIMEOUT. Re-running the script skips the runs already recorded, so it can be resumed.
#   scripts/run_experiments.sh [exp ...]     exps: d k w po pr mem long   (default: all)
cd "$(dirname "$0")/.."
[ -f build/owim/Main.class ] || ./build.sh
D=data
SETS="visualroad1 visualroad2 MVI_40171 MVI_40751 MOT16-06 MOT16-13"
WARM=${WARM:-1}; REPS=${REPS:-3}; LIMIT=${LIMIT:-3600}
RUN="java -Xmx6g -cp build owim.Main"
mkdir -p results/trace

one() { # exp file w d k method [opts...]
  local exp=$1 file=$2 w=$3 d=$4 k=$5 m=$6; shift 6
  local out=results/$exp.tsv name
  name=$(basename "$file" .txt); case " $* " in *" single-class "*) name="$name+single-class";; esac
  [ -f "$out" ] || $RUN header > "$out"
  if awk -F'\t' -v n="$name" -v w="$w" -v d="$d" -v k="$k" -v m="$m" \
       '$1==n && $2==w && $3==d && $4==k && $5==m {f=1} END {exit !f}' "$out"; then return; fi
  local line
  line=$(timeout "$LIMIT" $RUN run "$file" "$w" "$d" "$k" "$m" "$WARM" "$REPS" "$@" 2>/dev/null | tail -1)
  if [ -z "$line" ] || [ "$(printf '%s' "$line" | cut -f1)" != "$name" ]; then
    line="$name	$w	$d	$k	$m	$WARM	$REPS	-	TIMEOUT>${LIMIT}s"
  fi
  printf '%s\n' "$line" >> "$out"
  echo "$exp $line" | cut -c1-100
}

exps=${*:-d k w po pr mem long}
for e in $exps; do case $e in
  d)   # vary d, with the per-frame traces at the default d = 240; OWIM-NP is the ablation of permanent pruning
       for s in $SETS; do for d in 240 1 30 60 120 180 210 270; do for m in OWIM OWIM-NP; do
         opts=(); [ $d = 240 ] && opts=("trace=results/trace/${s}_${m}.tsv")
         one d $D/$s-filtered-4.txt 300 $d 1 $m "${opts[@]}"
       done; done; done ;;
  k)   # vary k at d = 30 (with d = 240 no dataset has an answer for k >= 5), objects of all classes in one class
       for s in $SETS; do for k in 1 2 3 5 7 9; do
         one k $D/$s-filtered-4.txt 300 30 $k OWIM single-class
       done; done ;;
  w)   # vary w (w = 300 is in the d runs)
       for s in $SETS; do for w in 400 500 600 900 1200 1800; do
         one w $D/$s-filtered-4.txt $w 240 1 OWIM
       done; done ;;
  po)  # occlusion parameter
       for s in $SETS; do for p in 0 1 2 3; do
         one po $D/$s-occ-$p.txt 300 240 1 OWIM
       done; done ;;
  pr)  # removal ratio
       for s in $SETS; do for p in 0.0 0.1 0.2 0.3; do
         one pr $D/$s-filtered-4-reduced-$p.txt 300 240 1 OWIM
       done; done ;;
  mem) # peak live heap, default parameters
       out=results/mem.tsv; [ -f $out ] || printf 'tag\tdataset\tw\td\tk\tmethod\tframes\tpeak_bytes\tmaintained_end\n' > $out
       for s in $SETS; do for m in OWIM OWIM-NP; do
         grep -q "	$s-filtered-4	300	240	1	$m	" $out && continue
         timeout "$LIMIT" $RUN mem $D/$s-filtered-4.txt 300 240 1 $m 2>/dev/null | grep '^mem' >> $out
         echo "mem $s $m"
       done; done ;;
  long) # long videos, default parameters, 1 warm-up run + 1 timed run
       for s in traffic1 traffic2 traffic3; do for m in OWIM OWIM-NP; do
         WARM=1 REPS=1 one long $D/$s.txt 300 240 1 $m
       done; done ;;
  *)   echo "unknown experiment: $e" >&2; exit 2 ;;
esac; done
echo "finished: $exps"
