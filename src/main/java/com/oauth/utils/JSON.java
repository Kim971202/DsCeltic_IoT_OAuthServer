package com.oauth.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JSON {
    private static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static String toJson(Object object) {
        try {
            return toJson(object, false);
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;

    }

    public static String toJson(Object object, boolean isPretty) {
        try {

            if( isPretty ) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
            } else {
                return objectMapper.writeValueAsString(object);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;

    }

    public static <T>T fromJson(String jsonString, Class<T> clazz) {
        try {
            return objectMapper.readValue(jsonString, clazz);
        } catch(Exception e) {
            System.out.println(e);
        }
        return null;
    }

    public static <T>T fromJson(String jsonString, TypeReference<T> valueTypeRef) {
        try {
            return objectMapper.readValue(jsonString, valueTypeRef);
        } catch(Exception e) {
            System.out.println(e);
        }
        return null;
    }
}
