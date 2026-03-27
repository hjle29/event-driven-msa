package com.payment.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.Decoder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * product-service는 ApiResponse<T>로 래핑해서 응답한다.
 * Feign 기본 디코더는 이를 인식 못하므로 data 필드를 직접 추출한다.
 */
public class ApiResponseDecoder implements Decoder {

    private final ObjectMapper objectMapper;

    public ApiResponseDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.body() == null) {
            return null;
        }

        JavaType mapType = objectMapper.getTypeFactory()
                .constructMapType(Map.class, String.class, Object.class);
        Map<String, Object> body = objectMapper.readValue(response.body().asInputStream(), mapType);

        Object data = body.get("data");
        if (data == null) {
            return null;
        }

        JavaType targetType = objectMapper.getTypeFactory().constructType(type);
        return objectMapper.convertValue(data, targetType);
    }
}
