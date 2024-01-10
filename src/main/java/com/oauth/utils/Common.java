package com.oauth.utils;

import com.oauth.response.ApiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Common {

    public static ApiResponse.Data.Device createDevice(
            String deviceId,
            String controlAuthKey,
            String nickname,
            String regSort,
            Set<String> usedDeviceIds) {
        // 중복 체크
//        if (usedDeviceIds.contains(deviceId)) {
//            throw new IllegalArgumentException("Duplicate deviceId: " + deviceId);
//        }

        // 중복이 없다면 Set에 추가
        usedDeviceIds.add(deviceId);

        // Device 생성
        ApiResponse.Data.Device device = new ApiResponse.Data.Device();
        device.setDeviceId(deviceId);
        device.setControlAuthKey(controlAuthKey);
        device.setDeviceNickname(nickname);
        device.setRegSort(regSort);

        return device;
    }

    public static List<String> extractJson(String inputList, String inputKey) {

        if(inputList.isEmpty()) return null;

        List<String> userIds = new ArrayList<>();
        String pInput = inputKey + "=([^,]+)";
        Pattern pattern = Pattern.compile(pInput);
        Matcher matcher = pattern.matcher(inputList);

        while (matcher.find()) {
            userIds.add(matcher.group(1));
        }

        return userIds;
    }

}
