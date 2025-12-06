#!/usr/bin/env bash

set -e

EMSDK_QUIET=1 . /opt/emsdk/emsdk_env.sh;

usermod -u ${EXTERNAL_UID:-1000} koda;

cp /root/.bashrc /home/koda/.bashrc
cp /root/.vimrc /home/koda/.vimrc
cp /root/.tmux.conf /home/koda/.tmux.conf

chown koda:users /home/koda
rsync -ar --chown=koda:users /opt/cargo/ /home/koda/.cargo/

export PATH="/home/koda/.cargo/bin:$PATH"
export CARGO_HOME="/home/koda/.cargo"

exec "$@"
