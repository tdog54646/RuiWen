package com.tongji.llm.rag.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.llm.rag.model.RagProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagIndexManagerTest {

    @Test
    void versionedMappingUsesChineseAnalyzersAndConfiguredVectorDimensions() throws Exception {
        RagProperties properties = new RagProperties();
        properties.getIndex().setAnalyzer("ik_max_word");
        properties.getIndex().setSearchAnalyzer("ik_smart");
        properties.getIndex().setDimensions(1536);

        JsonNode mapping = new ObjectMapper().readTree(
                RagIndexManager.mappingJson(new ObjectMapper(), properties));
        JsonNode fields = mapping.path("mappings").path("properties");

        assertEquals("ik_max_word", fields.path("content").path("analyzer").asText());
        assertEquals("ik_smart", fields.path("content").path("search_analyzer").asText());
        assertEquals("ik_max_word", fields.path("metadata").path("properties")
                .path("title").path("analyzer").asText());
        assertEquals(1536, fields.path("embedding").path("dims").asInt());
        assertEquals("cosine", fields.path("embedding").path("similarity").asText());
    }
}
