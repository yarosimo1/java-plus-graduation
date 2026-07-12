package ru.practicum.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "ru.practicum.events.client")
@EnableDiscoveryClient
@SpringBootApplication
public class EventsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventsServiceApplication.class, args);
    }
}
