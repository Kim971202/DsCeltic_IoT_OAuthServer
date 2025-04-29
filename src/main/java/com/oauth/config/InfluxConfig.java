package com.oauth.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxConfig {

    @Value("${influx.url}")
    private String influxUrl;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String org;

    @Value("${influx.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory
                .create(influxUrl, token.toCharArray(), org, bucket);
    }

    @Bean
    public WriteApi writeApi(InfluxDBClient client) {
        return client.getWriteApi();
    }

    @Bean
    public QueryApi queryApi(InfluxDBClient client) {
        return client.getQueryApi();
    }
}
