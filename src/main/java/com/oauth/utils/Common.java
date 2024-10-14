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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
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

    public static ApiResponse.Data.Device createDevice(
            String deviceId,
            String controlAuthKey,
            String nickname,
            String regSort,
            String tmpRegistKey,
            String latitude,
            String longitude,
            Set<String> usedDeviceIds) {


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
//            String responseUserNick,
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
//        invitation.setResponseUserNick(responseUserNick);
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

    public String readCon(String jsonString, String value) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonString);

        JsonNode serviceNode = jsonNode.get("md");
        JsonNode baseNode = jsonNode.path("m2m:sgn").path("nev").path("rep").path("m2m:cin");
        JsonNode conNode = baseNode.path("con");
        JsonNode surNode = jsonNode.path("m2m:sgn").path("sur");

        switch (value) {
            case "functionId":
                return serializeAndClean(conNode.path("functionId"), objectMapper);
            case "value":
                return serializeAndClean(conNode.path("value"), objectMapper);
            case "deviceId":
                return serializeAndClean(conNode.path("deviceId"), objectMapper);
            case "userId":
                return serializeAndClean(conNode.path("userId"), objectMapper);
            case "wkTm":
                return serializeAndClean(conNode.path("wkTm"), objectMapper);
            case "msDt":
                return serializeAndClean(conNode.path("msDt"), objectMapper);
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
            case "fwh":
                return serializeAndClean(conNode.path("rsCf").path("fwh"), objectMapper);
            case "rsSl":
                return serializeAndClean(conNode.path("rsCf").path("rsSl"), objectMapper);
            case "rsPw":
                return serializeAndClean(conNode.path("rsCf").path("rsPw"), objectMapper);
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

        // 빈 문자열은 따옴표를 제거하지 않도록 처리
        if (serializedValue.equals("\"\"")) {
            return "";
        }

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
//        StringBuilder stringBuilder = new StringBuilder();
//        for(char character : input.toCharArray()){
//            stringBuilder.append(Integer.toHexString((int) character));
//        }
//        return  stringBuilder.toString();
        StringBuilder stringBuilder = new StringBuilder();
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            stringBuilder.append(String.format("%02x", b));
        }
        return  stringBuilder.toString();
    }

    public String convertToJsonFormat(String input) {
        String pattern = "(\\w+):(\\d+|\\[.*?\\])";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(input);
        StringBuffer result = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);

            // 배열 형식의 값을 따로 처리합니다.
            if (value.startsWith("[")) {
                value = value.replaceAll("(\\d+)", "\"$1\"");
            } else {
                value = "\"" + value + "\"";
            }

            String replacement = "\"" + key + "\":" + value;
            m.appendReplacement(result, replacement);
        }
        m.appendTail(result);
        log.info("convertToJsonFormat: " + result.toString());
        return result.toString();
    }

    // null이 아닌 필드와 값을 Map으로 반환하는 메서드
    public Map<String, Object> getNonNullFields(Object obj) {
        Map<String, Object> nonNullFields = new HashMap<>();

        // 객체의 모든 필드에 대해 반복
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true); // private 필드에도 접근 가능하도록 설정
            try {
                Object value = field.get(obj); // 필드 값 가져오기
                // 필드 값이 null이 아닌 경우에만 맵에 추가
                if (value != null) {
                    nonNullFields.put(field.getName(), value);
                }
            } catch (IllegalAccessException e) {
                log.error("", e);
            }
        }
        return nonNullFields;
    }

    public void setCommandParams(Map<String, Object> nonNullField, AuthServerDTO params) {

        System.out.println("Map<String, Object> nonNullField: " + nonNullField);

        // 필드 이름을 키로, 그에 따른 Command 설정을 값으로 갖는 Map 생성
        Map<String, String[]> commandMap = new HashMap<>();
        commandMap.put("powr", new String[]{"powerOnOff", "powr", "전원 ON/OFF"});
        commandMap.put("opMd", new String[]{"ModeChange", "opMd", "홈 IoT 모드"});
        commandMap.put("htTp", new String[]{"TemperatureSet", "htTp", "실내온도설정"});
        commandMap.put("wtTp", new String[]{"BoiledWaterTempertureSet", "wtTp", "난방수 온도 설정"});
        commandMap.put("hwTp", new String[]{"WaterTempertureSet", "hwTp", "온수 온도 설정"});
        commandMap.put("ftMd", new String[]{"FastHotWaterSet", "ftMd", "빠른 온수 설정"});
        commandMap.put("h24", new String[]{"Set24", "24h", "24시간 예약"});
        commandMap.put("h12", new String[]{"Set12", "12h", "12시간 예약"});
        commandMap.put("wk7", new String[]{"SetWeek", "7wk", "주간 예약"});
        commandMap.put("fwh", new String[]{"AwakeAlarmSet", "fwh", "빠른온수 예약"});
        commandMap.put("bCdt", new String[]{"BCDT", "bCdt", "보일러 연소 상태"});
        commandMap.put("chTp", new String[]{"CHTP", "chTp", "현재 실내 온도"});
        commandMap.put("cwTp", new String[]{"CWTP", "cwTp", "현재 난방수 온도"});
        commandMap.put("slCd", new String[]{"SLCD", "slCd", "취침 난방 모드 설정"});
        commandMap.put("mwk", new String[]{"waterTemp", "wtTp", "난방수 온도 설정"});
        commandMap.put("reSt", new String[]{"waterTemp", "wtTp", "난방수 온도 설정"});
        commandMap.put("mfAr", new String[]{"waterTemp", "wtTp", "난방수 온도 설정"});

        System.out.println("commandMap");
        System.out.println(commandMap);

        // nonNullField에서 해당 필드가 존재하는지 확인하고, 해당 Command 설정 적용
        for (Map.Entry<String, String[]> entry : commandMap.entrySet()) {
            if (nonNullField.get(entry.getKey()) != null) {
                System.out.println("entry.getValue()[0]: " + entry.getValue()[0]);
                System.out.println("entry.getValue()[1]: " + entry.getValue()[1]);
                System.out.println("entry.getValue()[2]: " + entry.getValue()[2]);

                params.setCommandId(entry.getValue()[0]);
                params.setControlCode(entry.getValue()[1]);
                params.setControlCodeName(entry.getValue()[2]);
                break; // 첫 번째 일치하는 필드만 처리하고 종료
            }
        }
    }

    public String getModelCode(String modelCode){
        // 01: 보일러
        // 05: 각방
        // 07: 환기
        String code = "";

        if(modelCode.equals("ESCeco13S") || modelCode.equals("DCR-91/WF")) code = "01";
        else if(modelCode.equals("DCR-47/WF")) code = "07";

        return code;
    }
}
