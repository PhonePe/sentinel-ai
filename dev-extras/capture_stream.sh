#!/bin/bash

# Capture process
# 1. Start docker with: docker run -it --rm -p 8080:8080 --name wiremock wiremock/wiremock:3.13.1
# 2. Point browser to http://localhost:8080/__admin/recorder/
# 3. Set target url to whatever is the value of AZURE_ENDPOINT in test and click "Record"
# 4. Point test model endpoint to http://localhost:8080
# 5. Run the test
# 6. Click "Stop" in the browser
# 7. Run this script to generate files from the recorded mappings
# 8. Stop docker with: docker stop wiremock (Note: this is a destructive operation, all recorded data will be lost)

FILE_PREFIX=${1-events}

i=1
for file in $(docker exec -it wiremock ls /home/wiremock/mappings|tr -d '\r'); do
    echo "Processing File: ${file}"
    GEN_FILE_NAME=${FILE_PREFIX}.${i}.json
    docker exec  -it wiremock cat /home/wiremock/mappings/${file}| jq -r '.response.body' | xargs -0 echo -e > ${GEN_FILE_NAME}
    echo "Generated: ${GEN_FILE_NAME}"
    i=$((i+1))
done
