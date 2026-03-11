package com.coremall.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder inventoryRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient inventoryRestClient(@LoadBalanced RestClient.Builder builder) {
        return builder.baseUrl("http://inventory-service").build();
    }
}
