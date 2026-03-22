package io.github.hjle.settlement.config;

import com.hjle.common.event.OrderCancelledEvent;
import com.hjle.common.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ── OrderCreatedEvent ──────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory() {
        JsonDeserializer<OrderCreatedEvent> deserializer = new JsonDeserializer<>(OrderCreatedEvent.class);
        deserializer.addTrustedPackages("com.hjle.common.event");
        deserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(consumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCreatedConsumerFactory());
        factory.setCommonErrorHandler(defaultErrorHandler());
        return factory;
    }

    // ── OrderCancelledEvent ────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, OrderCancelledEvent> orderCancelledConsumerFactory() {
        JsonDeserializer<OrderCancelledEvent> deserializer = new JsonDeserializer<>(OrderCancelledEvent.class);
        deserializer.addTrustedPackages("com.hjle.common.event");
        deserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(consumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> orderCancelledListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCancelledConsumerFactory());
        factory.setCommonErrorHandler(defaultErrorHandler());
        return factory;
    }

    // ── DLT producer (settlement-service has no KafkaTemplate by default) ─

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    // ── Shared ─────────────────────────────────────────────────────────────

    private Map<String, Object> consumerProps() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return config;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> stringListenerContainerFactory() {
        Map<String, Object> props = consumerProps();
        // Override value deserializer for plain string messages (DLT payloads)
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()));
        return factory;
    }

    private DefaultErrorHandler defaultErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(com.hjle.common.exception.BusinessException.class);
        return handler;
    }
}
