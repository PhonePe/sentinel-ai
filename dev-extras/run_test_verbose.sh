#!/bin/sh

OUTPUT_FILE=./test_output.log
case $# in
  1)
    TEST=$1
    ;;
  2)
    TEST=$1
    OUTPUT_FILE=$2
     ;;
  *)
    echo "Usage: $0 <test class name> [<output file>]"
    exit 1
    ;;
esac

mvn -Preal-tests test -Dsurefire.failIfNoSpecifiedTests=false  -Dtest=${TEST} | tee ${OUTPUT_FILE}
