package io.github.hjle.settlement.config;

import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class ShedLockConfig {

    @Bean
    public RedisLockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "settlement-service");
    }
}
