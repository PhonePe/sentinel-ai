#!/bin/bash

# This script generates wiremock test mock json from test output
# Usage: ./gen_http_mocks.sh [prefix]
# Prefix defaults to "out". Files are named as "prefix.1.json", "prefix.2.json", etc.
# Steps:
# 1. Enable DEBUG logging on com.phonepe.sentinelai.models.SimpleOpenAIModel in test/resourcess/logback-test.xml
# 2. Route calls to actual LLM in test and run it
# 3. Copy the output of the test to clipboard
# 4. Run this script with proper prefix
# 5. Paste the output when prompted and press <CTRL-D> to finish input
# 6. The script will generate files named prefix.1.json, prefix.2.json, etc. in the current directory. Move them to the wiremock mappings directory (test/resources/wiremock)
# 7. Double check the test code line containing "TestUtils.setupMocks" to ensure correct number of mock files are specified
# 8. Run the test again to verify the mocks work as expected, this time, routing calls to wiremock instead of actual LLM

# required: csplit, awk, jq

prefix=${1-out}
prefix=$(echo -n "${prefix}.")

tfile=$(mktemp)
echo "{}" > ${tfile}

echo "Paste test output:"

cat | awk '
  /Response from model: \{/ {
    flag = 1;
    sub(/.*Response from model: /, "", $0);
    print;
    next
  }
  /^[0-9]{4}-[0-9]{2}-[0-9]{2}/ {
    if (flag) { flag = 0; print "\n" }
  }
  flag { print }
' >> ${tfile}
csplit -z -f ${prefix} -b %d.json ${tfile} '/^{/' '{*}'
rm -f ${prefix}0.json ${tfile}
