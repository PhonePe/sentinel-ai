#!/bin/bash

set -e

if [ "$BUILD" == "true" ]; then
  mvn clean install -DskipTests
fi

java "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" -jar target/sentinel-ai-examples-*-cli.jar