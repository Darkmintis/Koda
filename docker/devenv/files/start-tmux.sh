#!/usr/bin/env bash

sudo chown koda:users /home/koda

cd ~;

source ~/.bashrc

echo "[start-tmux.sh] Installing node dependencies"
pushd ~/koda/frontend/
corepack install;
yarn install;
yarn playwright install chromium
popd
pushd ~/koda/exporter/
corepack install;
yarn install
yarn playwright install chromium
popd

tmux -2 new-session -d -s koda

tmux rename-window -t koda:0 'frontend watch'
tmux select-window -t koda:0
tmux send-keys -t koda 'cd koda/frontend' enter C-l
tmux send-keys -t koda 'yarn run watch' enter

tmux new-window -t koda:1 -n 'frontend shadow'
tmux select-window -t koda:1
tmux send-keys -t koda 'cd koda/frontend' enter C-l
tmux send-keys -t koda 'yarn run watch:app' enter

tmux new-window -t koda:2 -n 'frontend storybook'
tmux select-window -t koda:2
tmux send-keys -t koda 'cd koda/frontend' enter C-l
tmux send-keys -t koda 'yarn run watch:storybook' enter

tmux new-window -t koda:3 -n 'exporter'
tmux select-window -t koda:3
tmux send-keys -t koda 'cd koda/exporter' enter C-l
tmux send-keys -t koda 'rm -f target/app.js*' enter C-l
tmux send-keys -t koda 'yarn run watch' enter

tmux split-window -v
tmux send-keys -t koda 'cd koda/exporter' enter C-l
tmux send-keys -t koda './scripts/wait-and-start.sh' enter

tmux new-window -t koda:4 -n 'backend'
tmux select-window -t koda:4
tmux send-keys -t koda 'cd koda/backend' enter C-l
tmux send-keys -t koda './scripts/start-dev' enter

tmux -2 attach-session -t koda
