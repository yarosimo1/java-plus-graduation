package ru.practicum.eventsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "ru.practicum.events.client")
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"ru.practicum.events", "ru.practicum.ewm"})
public class EventsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventsServiceApplication.class, args);
    }
}
