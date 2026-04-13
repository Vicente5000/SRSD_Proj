#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  run_two_at_timestamp.sh <epoch_ms|+delay_seconds> "<command1>" "<command2>"

Examples:
  run_two_at_timestamp.sh +5 "echo cmd1" "echo cmd2"
  run_two_at_timestamp.sh 1767225600000 "./task_a" "./task_b --flag"

Notes:
  - Absolute "same exact" start time cannot be guaranteed by any OS scheduler.
  - This script minimizes skew by starting both commands behind a FIFO barrier,
    then releasing both at the target timestamp.
EOF
}

if [[ $# -ne 3 ]]; then
  usage
  exit 1
fi

target_input="$1"
cmd1="$2"
cmd2="$3"

now_ms() {
  perl -MTime::HiRes=time -e 'print int(time()*1000)'
}

sleep_ms() {
  local ms="$1"
  if (( ms <= 0 )); then
    return
  fi
  perl -MTime::HiRes=sleep -e "sleep(${ms}/1000)"
}

if [[ "$target_input" == +* ]]; then
  delay_s="${target_input#+}"
  if [[ ! "$delay_s" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid delay format: $target_input" >&2
    exit 1
  fi
  target_ms=$(perl -MTime::HiRes=time -e "print int((time()+${delay_s})*1000)")
else
  if [[ ! "$target_input" =~ ^[0-9]+$ ]]; then
    echo "Target timestamp must be epoch milliseconds or +delay_seconds" >&2
    exit 1
  fi
  target_ms="$target_input"
fi

fifo="$(mktemp -u /tmp/run-two-at-ts.XXXXXX)"
mkfifo "$fifo"

cleanup() {
  kill "$p1" "$p2" 2>/dev/null || true
  rm -f "$fifo"
}
trap cleanup INT TERM EXIT

# Keep FIFO opened read/write in the parent so children never block indefinitely
# on opening the FIFO endpoints.
exec 9<>"$fifo"

(
  IFS= read -r _ < "$fifo"
  exec /bin/zsh -c "$cmd1"
) &
p1=$!

(
  IFS= read -r _ < "$fifo"
  exec /bin/zsh -c "$cmd2"
) &
p2=$!

while true; do
  current_ms="$(now_ms)"
  remaining_ms=$((target_ms - current_ms))
  if (( remaining_ms <= 0 )); then
    break
  fi
  if (( remaining_ms > 50 )); then
    sleep_ms 25
  else
    sleep_ms 1
  fi
done

# Release both commands as close together as possible.
printf 'go\n' >&9
printf 'go\n' >&9
exec 9>&-

wait "$p1"
rc1=$?
wait "$p2"
rc2=$?

if (( rc1 != 0 || rc2 != 0 )); then
  echo "Command exit codes: cmd1=${rc1}, cmd2=${rc2}" >&2
  exit 1
fi

echo "Both commands were released at target timestamp ${target_ms}."