package com.phonepe.sentinelai.examples.texttosql.tools.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription(
       """
           Request body for the tool that fetches description of tables in the sqlite database. Contains a list of table names.
       """)
public record TableDescRequest (
    @JsonPropertyDescription(
            """
               Names of the tables for which descriptions needs to be fetched.
           """)
    List<String> tableNames
) {

}
