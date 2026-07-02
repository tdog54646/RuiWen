package com.tongji.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(EsProperties.class)
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final EsProperties props;

    /**
     * Jackson ObjectMapper 实例，供 ES JSON 序列化/反序列化以及业务代码共同使用。
     */
    @Bean
    public ObjectMapper elasticsearchObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 1. 注册 Java 8 时间模块
        mapper.registerModule(new JavaTimeModule());
        // 2. 禁用纳秒级时间戳，强制输出纯整数毫秒
        mapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);

        return mapper;
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ObjectMapper elasticsearchObjectMapper) {
        BasicCredentialsProvider creds = new BasicCredentialsProvider();

        if (StringUtils.hasText(props.getUsername())) {
            creds.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(props.getUsername(), props.getPassword()));
        }

        RestClientBuilder builder = RestClient.builder(org.apache.http.HttpHost.create(props.getHost()))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(creds));

        RestClient restClient = builder.build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(elasticsearchObjectMapper));

        return new ElasticsearchClient(transport);
    }
}