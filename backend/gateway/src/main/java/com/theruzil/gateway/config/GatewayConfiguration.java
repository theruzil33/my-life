package com.theruzil.gateway.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

import com.theruzil.gateway.routing.ProxyRoutingFilter;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfiguration {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<ProxyRoutingFilter> proxyRoutingFilterRegistration(RestClient restClient,
            GatewayProperties gatewayProperties) {
        FilterRegistrationBean<ProxyRoutingFilter> registration = new FilterRegistrationBean<>(
                new ProxyRoutingFilter(restClient, gatewayProperties));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
