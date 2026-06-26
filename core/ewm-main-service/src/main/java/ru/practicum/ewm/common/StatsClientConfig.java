package ru.practicum.ewm.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.practicum.client.StatsClient;

@Configuration
public class StatsClientConfig {

    private String statsServerUrl;

    @Bean
    public StatsClient statsClient() {
        return StatsClient.create(statsServerUrl);
    }
}
