package com.oauth.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oauth.dto.AuthServerDTO;
import com.oauth.jwt.ApiTokenUtils;
import com.oauth.jwt.TokenMaterial;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Common {

    @Autowired
    private ApiTokenUtils apiTokenUtils;

    private static HashMap<String, String> mymap = new HashMap<>();

    public static String getMyMap(String rKey){
        return mymap.get(rKey);
    }

    public static void setMyMap(String rKey, String srNo){
        mymap.put(rKey, srNo);
    }

    public static ApiResponse.Data.Device createDevice(
            String deviceId,
            String controlAuthKey,
            String nickname,
            String regSort,
            String tmpRegistKey,
            String latitude,
            String longitude,
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
        device.setTmpRegistKey(tmpRegistKey);
        device.setLatitude(latitude);
        device.setLongitude(longitude);

        return device;
    }

    public static ApiResponse.Data.User createUsers(
            String userNickname,
            String userId,
            String houseHolder,
            Set<String> userIds) {
        // 중복 체크
//        if (userIds.contains(deviceId)) {
//            throw new IllegalArgumentException("Duplicate deviceId: " + deviceId);
//        }

        // 중복이 없다면 Set에 추가
        userIds.add(userId);

        // Device 생성
        ApiResponse.Data.User user = new ApiResponse.Data.User();
        user.setUserNickname(userNickname);
        user.setUserId(userId);
        user.setHouseholder(houseHolder);

        return user;
    }

    public static ApiResponse.Data.Invitation createInvitations(
            String invitationIdx,
            String inviteAcceptYn,
            String requestUserId,
            String requestUserNick,
            String responseUserId,
            String responseUserNick,
            String responseHp,
            String inviteStartDate,
            String inviteEndDate,
            Set<String> invitationIds) {

        invitationIds.add(invitationIdx);

        // Device 생성
        ApiResponse.Data.Invitation invitation = new ApiResponse.Data.Invitation();

        invitation.setInvitationIdx(invitationIdx);
        invitation.setInviteAcceptYn(inviteAcceptYn);
        invitation.setRequestUserId(requestUserId);
        invitation.setRequestUserNick(requestUserNick);
        invitation.setResponseUserId(responseUserId);
        invitation.setResponseUserNick(responseUserNick);
        invitation.setResponseHp(responseHp);
        invitation.setInviteStartDate(inviteStartDate);
        invitation.setInviteEndDate(inviteEndDate);

        return invitation;
    }

    public static ApiResponse.Data.PushInfo createPush(
            String pushIdx,
            String pushTitle,
            String pushContent,
            String pushType,
            String pushDatetime,
            Set<String> pushIds) {

        pushIds.add(pushIdx);

        // Device 생성
        ApiResponse.Data.PushInfo push = new ApiResponse.Data.PushInfo();

        push.setPushIdx(pushIdx);
        push.setPushTitle(pushTitle);
        push.setPushContent(pushContent);
        push.setPushType(pushType);
        push.setPushDatetime(pushDatetime);

        return push;
    }

    public static ApiResponse.Data.NoticeInfo createNotice(
            String noticeIdx,
            String noticeTitle,
            String noticeContent,
            String noticeType,
            String noticeStartDate,
            String noticeEndDate,
            Set<String> noticeIds) {

        noticeIds.add(noticeIdx);

        // Device 생성
        ApiResponse.Data.NoticeInfo notice = new ApiResponse.Data.NoticeInfo();

        notice.setNoticeIdx(noticeIdx);
        notice.setNoticeTitle(noticeTitle);
        notice.setNoticeContent(noticeContent);
        notice.setNoticeType(noticeType);
        notice.setNoticeStartDate(noticeStartDate);
        notice.setNoticeEndDate(noticeEndDate);

        return notice;
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

    /**
     * @param list 중복이 있는 list
     * @param key  중복 여부를 판단하는 키값
     * @param <T>  generic type
     * @return list
     */
    public static <T> List<T> deduplication(final List<T> list, Function<? super T, ?> key) {
        return list.stream()
                .filter(deduplication(key))
                .collect(Collectors.toList());
    }

    private static <T> Predicate<T> deduplication(Function<? super T, ?> key) {
        final Set<Object> set = ConcurrentHashMap.newKeySet();
        return predicate -> set.add(key.apply(predicate));
    }

    public static void updateMemberDTOList(List<AuthServerDTO> authServerDTOList, String targetKey, Object newValue) {
        for (AuthServerDTO authServerDTO : authServerDTOList) {
            try {
                // MemberDTO 클래스의 필드에 따라 해당 필드에 접근해서 원하는 Key인지 확인
                var field = AuthServerDTO.class.getDeclaredField(targetKey);
                field.setAccessible(true);
                field.set(authServerDTO, newValue);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // 원하는 Key가 없을 경우 예외 처리 또는 다른 작업 수행
                log.info("Unsupported key: " + targetKey);
            }
        }
    }

    public static void updateMemberDTOList(AuthServerDTO authServerDTO, String targetKey, Object newValue) {
            try {
                // MemberDTO 클래스의 필드에 따라 해당 필드에 접근해서 원하는 Key인지 확인
                var field = AuthServerDTO.class.getDeclaredField(targetKey);
                field.setAccessible(true);
                field.set(authServerDTO, newValue);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // 원하는 Key가 없을 경우 예외 처리 또는 다른 작업 수행
                log.info("Unsupported key: " + targetKey);
            }
    }

    public LocalDateTime getTimeAsiaSeoulNow() {
        return getTimeNow("Asia/Seoul");
    }

    public LocalDateTime getTimeNow(String zoneId) {
        return LocalDateTime.now(ZoneId.of(zoneId));
    }

    /**
     * 트랜잭션 ID
     * @return
     */
    public String getTransactionId() {
        return getTransactionIdBaseUUID();
    }

    /**
     * UUID 리턴
     * @return a432e21a-54df-4e43-8ef9-99cd274dced8
     */
    private String getTransactionIdBaseUUID() {
        return UUID.randomUUID().toString();
    }

//    public String readCon(String jsonString,String value) throws Exception{
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode jsonNode = objectMapper.readTree(jsonString);
//
//        JsonNode baseNode = jsonNode.path("m2m:sgn").path("nev").path("rep").path("m2m:cin");
//        JsonNode conNode = jsonNode.path("m2m:sgn").path("nev").path("rep").path("m2m:cin").path("con");
//        JsonNode surNode = jsonNode.path("m2m:sgn").path("sur");
//
//        JsonNode rsCf24HNode = jsonNode.path("24h");
//        JsonNode rsCf12HNode = jsonNode.path("12h");
//        JsonNode rsCf7WKNode = jsonNode.path("7wk");
//
//        JsonNode newRsCf24HNode = conNode.path("rsCf").path("24h");
//        JsonNode newRsCf12HNode = conNode.path("rsCf").path("12h");
//        JsonNode newRsCf7WKNode = conNode.path("rsCf").path("7wk");
//
//        JsonNode returnNode = conNode.path(value);
//        JsonNode returnConNode = baseNode.path(value);
//
//        String returnValue = objectMapper.writeValueAsString(returnNode);
//        String returnConValue = objectMapper.writeValueAsString(returnConNode);
//        String returnSurValue = objectMapper.writeValueAsString(surNode);
//        String returnRsCf24HMdNode = objectMapper.writeValueAsString(rsCf24HNode.get("md"));
//
//        String returnRsCf24HNode = objectMapper.writeValueAsString(rsCf24HNode);
//        String returnRsCf12HNode = objectMapper.writeValueAsString(rsCf12HNode);
//        String returnRsCf7WKNode = objectMapper.writeValueAsString(rsCf7WKNode);
//
//
//        String returnNewRsCf24HNode = objectMapper.writeValueAsString(newRsCf24HNode);
//        String returnNewRsCf12HNode = objectMapper.writeValueAsString(newRsCf12HNode);
//        String returnNewRsCf7WKNode = objectMapper.writeValueAsString(newRsCf7WKNode);
//
//        switch (value) {
//            case "rsCf":
//                return returnValue;
//            case "con":
//                return returnConValue.replace("\"", "");
//            case "sur":
//                return returnSurValue.replace("\"", "");
//            case "md":
//                return returnRsCf24HMdNode.replace("\"", "");
//            case "24h":
//                return returnNewRsCf24HNode.replace("\"", "");
//            case "12h":
//                return returnNewRsCf12HNode.replace("\"", "");
//            case "7wk":
//                return returnNewRsCf7WKNode.replace("\"", "");
//            case "24h_old":
//                return returnRsCf24HNode.replace("\"", "");
//            case "12h_old":
//                return returnRsCf12HNode.replace("\"", "");
//            case "7wk_old":
//                return returnRsCf7WKNode.replace("\"", "");
//            default:
//                return returnValue.replace("\"", "");
//        }
//    }

    public String readCon(String jsonString, String value) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonString);

        JsonNode serviceNode = jsonNode.get("md");
        JsonNode baseNode = jsonNode.path("m2m:sgn").path("nev").path("rep").path("m2m:cin");
        JsonNode conNode = baseNode.path("con");
        JsonNode surNode = jsonNode.path("m2m:sgn").path("sur");

        switch (value) {
            case "value":
                return serializeAndClean(conNode.path("value"), objectMapper);
            case "deviceId":
                return serializeAndClean(conNode.path("deviceId"), objectMapper);
            case "userId":
                return serializeAndClean(conNode.path("userId"), objectMapper);
            case "con":
                return serializeAndClean(baseNode, objectMapper);
            case "sur":
                return serializeAndClean(surNode, objectMapper);
            case "md":
                return serializeAndClean(conNode.path("rsCf").path("24h").path("md"), objectMapper);
            case "serviceMd":
                return serializeAndClean(serviceNode, objectMapper);
            case "24h":
                return serializeAndClean(conNode.path("rsCf").path("24h"), objectMapper);
            case "12h":
                return serializeAndClean(conNode.path("rsCf").path("12h"), objectMapper);
            case "7wk":
                return serializeAndClean(conNode.path("rsCf").path("7wk"), objectMapper);
            case "24h_old":
                return serializeAndClean(jsonNode.path("24h"), objectMapper);
            case "12h_old":
                return serializeAndClean(jsonNode.path("12h"), objectMapper);
            case "7wk_old":
                return serializeAndClean(jsonNode.path("7wk"), objectMapper);
            default:
                return serializeAndClean(conNode.path(value), objectMapper);
        }
    }

    private String serializeAndClean(JsonNode node, ObjectMapper mapper) throws Exception {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String serializedValue = mapper.writeValueAsString(node);
        return serializedValue.replace("\"", "");
    }

    public String convertToJsonString(String jsonString) {
        // Replace unquoted field names with quoted ones
        jsonString = jsonString.replaceAll("([\\{,])\\s*(\\w+)\\s*:", "$1\"$2\":");

        // Replace numeric values with leading zeroes or in arrays with quoted ones
        jsonString = jsonString.replaceAll(":\\s*(\\d+)([\\},])", ":\"$1\"$2");
        jsonString = jsonString.replaceAll(":\\s*(\\d+)(\\s*,|\\s*\\])", ":\"$1\"$2");
        jsonString = jsonString.replaceAll("\\[(\\d+)", "[\"$1\"");
        jsonString = jsonString.replaceAll("(\\d+)\\]", "\"$1\"]");
        jsonString = jsonString.replaceAll("(\\d+),", "\"$1\",");

        // Handle empty values properly
        jsonString = jsonString.replaceAll(":\\s*,", ":\"\",");
        jsonString = jsonString.replaceAll(":\\s*\\]", ":\"\"]");

        return jsonString;
    }

    public static String extractMdValue(String jsonString) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonString);

        // Extract the md value from 24h
        JsonNode mdNode = rootNode.path("24h").path("md");

        if (mdNode.isMissingNode()) {
            throw new Exception("md field is missing in the 24h node");
        }

        return mdNode.asText();
    }

    public String addCon(String body, List<String> key, List<String> value) throws Exception{

        String modifiedJson = null;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(body);

            ObjectNode conNode = (ObjectNode) jsonNode
                    .path("m2m:sgn")
                    .path("nev")
                    .path("rep")
                    .path("m2m:cin")
                    .path("con");

            for(int i = 0; i < key.size(); ++i){
                conNode.put(key.get(i), value.get(i));
            }

            modifiedJson = objectMapper.writeValueAsString(jsonNode);

            System.out.println(modifiedJson);
        } catch (Exception e) {
            log.error("", e);
        }
        return modifiedJson;
    }

    public Map<String, Object> changeStringToJson (String json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.error("", e);
        }
        return null;
    }

    public List<String> getUserIdAndFunctionId (String redisValue){
        String[] splitStrings = redisValue.split(",");
        List<String> nameList =  new ArrayList<String>();

        // 결과 확인
        if (splitStrings.length >= 2) {
            nameList.add(splitStrings[0].trim());  // trim() 메서드로 앞뒤 공백 제거
            nameList.add(splitStrings[1].trim());

            log.info("첫번째 문자열: " + nameList.add(splitStrings[0].trim()));
            log.info("두번째 문자열: " + nameList.add(splitStrings[1].trim()));
        } else {
            log.info("적절한 형식의 문자열이 아닙니다.");
        }
        return nameList;
    }

    public String getCurrentDateTime() {
        Date today = new Date();
        Locale currentLocale = new Locale("KOREAN", "KOREA");
        String pattern = "yyyyMMddHHmmss"; //hhmmss로 시간,분,초만 뽑기도 가능
        SimpleDateFormat formatter = new SimpleDateFormat(pattern,
                currentLocale);
        return formatter.format(today);
    }

    public List<String> getHomeViewDataList(List<String> responseList, String inputValue) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> desiredValues = new ArrayList<>();

        // Iterate through the array of JSON strings
        for (String jsonString : responseList) {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            JsonNode deviceNode = jsonNode.path("device");

            // Extract the desired value from the "device" node
            String desiredValue = deviceNode.path(inputValue).asText();
            desiredValues.add(desiredValue);
        }
        return desiredValues;
    }

    public String createJwtToken(String userId, String contentType, String functionId){

        TokenMaterial tokenMaterial = TokenMaterial.builder()
                .header(TokenMaterial.Header.builder()
                        .userId(userId)
                        .contentType("NORMAL")
                        .build())
                .payload(TokenMaterial.Payload.builder()
                        .functionId("AccessTokenRenewal")
                        .timestamp(getCurrentDateTime())
                        .build())
                .build();
        return apiTokenUtils.createJWT(tokenMaterial);
    }

    public String hexToString(String hex){
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            String str = hex.substring(i, i + 2);
            stringBuilder.append((char) Integer.parseInt(str, 16));
        }
        return stringBuilder.toString();
    }

    public String stringToHex(String input){
        StringBuilder stringBuilder = new StringBuilder();
        for(char character : input.toCharArray()){
            stringBuilder.append(Integer.toHexString((int) character));
        }
        return  stringBuilder.toString();
    }

}
