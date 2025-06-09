package com.phonepe.sentinelai.toolbox.remotehttp;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 */
class JoltTest {
    @Test
    @Disabled
    void test() {
        final var c = Chainr.fromSpec(JsonUtils.jsonToList("""
                                                                   [
                                                                               {
                                                                                 "operation": "shift",
                                                                                 "spec": {
                                                                                   "rows": {
                                                                                     "*": {
                                                                                       "field": "fields[#2].field",
                                                                                       "type": "fields[#2].type",
                                                                                       "queryState": "fields[#2].queryState"
                                                                                     },
                                                                                     "fields": "[&1]"
                                                                                   }
                                                                                 }
                                                                               }
                                                                             ]"""));
        System.out.println(JsonUtils.toPrettyJsonString(c.transform(JsonUtils.jsonToObject("""
                                                        {
                                                          "headers": [
                                                            {
                                                              "maxLength": 21,
                                                              "name": "creationDate"
                                                            },
                                                            {
                                                              "maxLength": 10,
                                                              "name": "estimatedCardinalityPerDay"
                                                            },
                                                            {
                                                              "maxLength": 134,
                                                              "name": "field"
                                                            },
                                                            {
                                                              "maxLength": 5,
                                                              "name": "indexingMetadata.docValueEnabled"
                                                            },
                                                            {
                                                              "maxLength": 5,
                                                              "name": "indexingMetadata.indexingEnabled"
                                                            },
                                                            {
                                                              "maxLength": 16,
                                                              "name": "indexingMetadata.modifiedBy"
                                                            },
                                                            {
                                                              "maxLength": 21,
                                                              "name": "lastQueriedAt"
                                                            },
                                                            {
                                                              "maxLength": 15,
                                                              "name": "queryState"
                                                            },
                                                            {
                                                              "maxLength": 5,
                                                              "name": "securityMetadata.encrypted"
                                                            },
                                                            {
                                                              "maxLength": 9,
                                                              "name": "type"
                                                            }
                                                          ],
                                                          "rows": [
                                                            {
                                                              "creationDate": "30/04/2024 01:05:47",
                                                              "estimatedCardinalityPerDay": 1,
                                                              "field": "app",
                                                              "indexingMetadata.docValueEnabled": true,
                                                              "indexingMetadata.indexingEnabled": false,
                                                              "indexingMetadata.modifiedBy": "FOXTROT_SYSTEM",
                                                              "lastQueriedAt": "30/05/2025 00:00:00",
                                                              "queryState": "NON_QUERYABLE",
                                                              "securityMetadata.encrypted": false,
                                                              "type": "KEYWORD"
                                                            },
                                                            {
                                                              "creationDate": "30/04/2024 01:05:47",
                                                              "estimatedCardinalityPerDay": 1,
                                                              "field": "date.dayOfMonth",
                                                              "indexingMetadata.docValueEnabled": true,
                                                              "indexingMetadata.indexingEnabled": true,
                                                              "indexingMetadata.modifiedBy": "FOXTROT_SYSTEM",
                                                              "lastQueriedAt": "30/05/2025 00:00:48",
                                                              "queryState": "QUERYABLE",
                                                              "securityMetadata.encrypted": false,
                                                              "type": "LONG"
                                                            }
                                                          ]
                                                        }"""))));
    }
}
