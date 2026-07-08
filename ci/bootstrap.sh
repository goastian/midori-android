#! /bin/bash
set -euo pipefail

type -p shopt && shopt -s expand_aliases
. ci/bootstrap/bootstrap.sh
