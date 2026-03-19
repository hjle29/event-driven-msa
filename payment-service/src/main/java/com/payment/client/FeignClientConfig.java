package com.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Logger;
import feign.Request;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 고트래픽 환경 Feign 설정:
 * - OkHttp: 커넥션 풀링으로 기본 HttpURLConnection 대비 성능 대폭 향상
 * - connectTimeout 2s / readTimeout 5s: fail-fast로 장애 전파 차단
 * - BASIC 로그: 운영 환경에서 과도한 로그 방지 (FULL은 개발 환경만)
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public OkHttpClient feignOkHttpClient() {
        return new OkHttpClient(new okhttp3.OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build());
    }

    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }

    @Bean
    public Decoder feignDecoder() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new ApiResponseDecoder(objectMapper);
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
