#!/usr/bin/env bash

sudo chown koda:users /home/koda

cd ~;

source ~/.bashrc

set -e;

echo "[start-tmux.sh] Installing node dependencies"
pushd ~/koda/exporter/
yarn install
popd

tmux -2 new-session -d -s koda

tmux rename-window -t koda:0 'exporter'
tmux select-window -t koda:0
tmux send-keys -t koda 'cd koda/exporter' enter C-l
tmux send-keys -t koda 'rm -f target/app.js*' enter C-l
tmux send-keys -t koda 'clojure -M:dev:shadow-cljs watch main' enter

tmux split-window -v
tmux send-keys -t koda 'cd koda/exporter' enter C-l
tmux send-keys -t koda './scripts/wait-and-start.sh' enter

tmux new-window -t koda:1 -n 'backend'
tmux select-window -t koda:1
tmux send-keys -t koda 'cd koda/backend' enter C-l
tmux send-keys -t koda './scripts/start-dev' enter

tmux -2 attach-session -t koda
