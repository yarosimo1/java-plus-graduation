package ru.practicum.ewm.stats.collector;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import java.util.Map;

@Configuration
public class KafkaConfig {
    @Bean
    ProducerFactory<String, byte[]> producerFactory(@Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        return new DefaultKafkaProducerFactory<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
    }
    @Bean KafkaTemplate<String, byte[]> kafkaTemplate(ProducerFactory<String, byte[]> pf) { return new KafkaTemplate<>(pf); }
}
