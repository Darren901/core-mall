package com.coremall.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${order-service.base-url:http://order-service}")
    private String orderServiceBaseUrl;

    @Bean
    public WebClient orderServiceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(orderServiceBaseUrl)
                .build();
    }
}
