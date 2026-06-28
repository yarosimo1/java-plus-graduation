package ru.practicum.ewm.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import ru.practicum.client.StatsClient;

@Configuration
public class StatsClientConfig {

    private String statsServerUrl;

    @Bean
    @LoadBalanced
    public RestTemplate statsRestTemplate() {
        return new RestTemplate();
    }

    public StatsClient statsClient(@Value("${stats.server.url:http://stats-server}") String statsServerUrl,
                                   RestTemplate statsRestTemplate) {
        return new StatsClient(statsServerUrl, statsRestTemplate);
    }
}
