#!/bin/bash

set -e

if [ "$BUILD" == "true" ]; then
  mvn clean install -DskipTests
fi

if [ "$DEBUG" == "true" ]; then
  export JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
fi

java $JAVA_TOOL_OPTIONS -jar target/sentinel-ai-examples-*-cli.jar