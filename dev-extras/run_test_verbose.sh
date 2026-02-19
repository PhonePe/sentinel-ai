#!/bin/sh

OUTPUT_FILE=./test_output.log
EXTRA_TARGETS=""
case $# in
  1)
    TEST=$1
    ;;
  2)
    TEST=$1
    EXTRA_TARGETS=$2
    ;;
  3)
    TEST=$1
    EXTRA_TARGETS=$2
    OUTPUT_FILE=$3
     ;;
  *)
    echo "Usage: $0 <test class name> [<output file>]"
    exit 1
    ;;
esac

mvn -Preal-tests ${EXTRA_TARGETS} test -Dspotless.skip=true -Dsurefire.failIfNoSpecifiedTests=false  -Dtest=${TEST} | tee ${OUTPUT_FILE}
