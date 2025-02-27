package com.oauth.utils;

import java.util.Map;

public class DataFactory {
    /**
     * 하나의 메서드로 서로 다른 클래스의 객체를 생성하고,
     * setData(Map<String, String>)를 호출해 필드를 세팅한 뒤 반환.
     */
    public static <T extends DataSettable> T buildData(Class<T> clazz, Map<String, String> data) {
        try {
            // 1) 클래스의 인스턴스 생성 (기본 생성자 필요)
            T instance = clazz.getDeclaredConstructor().newInstance();
            // 2) setData()로 Map에 담긴 내용을 세팅
            instance.setData(data);
            // 3) 최종 객체 반환
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
