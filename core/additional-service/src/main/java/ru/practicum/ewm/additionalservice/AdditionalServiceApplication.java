package ru.practicum.ewm.additionalservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "ru.practicum.ewm")
public class AdditionalServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdditionalServiceApplication.class, args);
    }
}
