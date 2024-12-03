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
import com.oauth.mapper.DeviceMapper;
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
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private DeviceMapper deviceMapper;

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
                return serializeAndClean(conNode.path("functionId"), objectMapper, "functionId");
            case "value":
                return serializeAndClean(conNode.path("value"), objectMapper, "value");
            case "deviceId":
                return serializeAndClean(conNode.path("deviceId"), objectMapper, "deviceId");
            case "userId":
                return serializeAndClean(conNode.path("userId"), objectMapper, "userId");
            case "wkTm":
                return serializeAndClean(conNode.path("wkTm"), objectMapper, "wkTm");
            case "msDt":
                return serializeAndClean(conNode.path("msDt"), objectMapper, "msDt");
            case "con":
                return serializeAndClean(baseNode, objectMapper, "con");
            case "sur":
                return serializeAndClean(surNode, objectMapper, "sur");
            case "md":
                return serializeAndClean(conNode.path("rsCf").path("24h").path("md"), objectMapper, "md");
            case "serviceMd":
                return serializeAndClean(serviceNode, objectMapper, "serviceMd");
            case "24h":
                return serializeAndClean(conNode.path("rsCf").path("24h"), objectMapper, "24h");
            case "12h":
                return serializeAndClean(conNode.path("rsCf").path("12h"), objectMapper, "12h");
            case "7wk":
                return serializeAndClean(conNode.path("rsCf").path("7wk"), objectMapper, "7wk");
            case "fwh":
                return serializeAndClean(conNode.path("rsCf").path("fwh"), objectMapper, "fwh");
            case "rsSl":
                return serializeAndClean(conNode.path("rsCf").path("rsSl"), objectMapper, "rsSl");
            case "rsPw":
                return serializeAndClean(conNode.path("rsCf").path("rsPw"), objectMapper, "rsPw");
            case "24h_old":
                return serializeAndClean(jsonNode.path("24h"), objectMapper, "24h_old");
            case "12h_old":
                return serializeAndClean(jsonNode.path("12h"), objectMapper, "12h_old");
            case "7wk_old":
                return serializeAndClean(jsonNode.path("7wk"), objectMapper, "7wk_old");
            default:
                return serializeAndClean(conNode.path(value), objectMapper, "DEFAULT");
        }
    }

    private String serializeAndClean(JsonNode node, ObjectMapper mapper, String key) throws Exception {
        if (node == null || node.isMissingNode()) {
            return null;
        }

        String serializedValue = mapper.writeValueAsString(node);

        // fwh, rsSl, rsPw 인 경우 그대로 반환
        if ("fwh".equals(key) || "rsSl".equals(key) || "rsPw".equals(key)) {
            return serializedValue;  // 변형 없이 그대로 반환
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

        log.info("Map<String, Object> nonNullField: " + nonNullField);

        // 필드 이름을 키로, 그에 따른 Command 설정을 값으로 갖는 Map 생성
        Map<String, String[]> commandMap = new HashMap<>();
        commandMap.put("powr", new String[]{"powerOnOff", "powr", "전원 ON/OFF"});
        commandMap.put("opMd", new String[]{"ModeChange", "opMd", "모드 변경"});
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
        commandMap.put("ecOp", new String[]{"EcoOperation", "ecOp", "절약난방 상태정보 알림"});
        commandMap.put("hwSt", new String[]{"HotWaterStatus", "hwSt", "온수 사용 상태"});
        commandMap.put("fcLc", new String[]{"DeviceLock", "fcLc", "화면 잠금 설정"});
        commandMap.put("mwk", new String[]{"waterTemp", "wtTp", "난방수 온도 설정"});
        commandMap.put("reSt", new String[]{"waterTemp", "wtTp", "난방수 온도 설정"});
        commandMap.put("mfAr", new String[]{"waterTemp", "wtTp", "난방수 온도 설정"});
        commandMap.put("vtSp", new String[]{"VentilationSpeed", "vtSp", "환기 풍량 설정"});
        commandMap.put("rsPw", new String[]{"VentilationOnOffSet", "rsPw", "환기 켜짐 꺼짐 예약"});

        log.info("commandMap: " + commandMap);

        // nonNullField에서 해당 필드가 존재하는지 확인하고, 해당 Command 설정 적용
        for (Map.Entry<String, String[]> entry : commandMap.entrySet()) {
            if (nonNullField.get(entry.getKey()) != null) {
                log.info("entry.getValue()[0]: " + entry.getValue()[0]);
                log.info("entry.getValue()[1]: " + entry.getValue()[1]);
                log.info("entry.getValue()[2]: " + entry.getValue()[2]);

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

    public String getModelCodeFromDeviceId(String deviceId){
        String[] modelCode = deviceId.split("\\.");
        return hexToString(modelCode[5]);
    }

    /** 기기별칭 쿼리 함수 */
    public String returnDeviceNickname(String deviceId){ return stringToHex(memberMapper.getDeviceNicknameByDeviceId(deviceId).getDeviceNickname()); }

    /** 공통 로그 출력 함수 */
    public void logParams(Object params) {
        if (params == null) {
            log.info("Params are null");
            return;
        }

        try {
            Field[] fields = params.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(params);
                if (value != null && !value.toString().isEmpty()) {
                    if ("userPassword".equals(field.getName())) {
                        log.info(field.getName() + ": [PROTECTED]"); // 비밀번호는 직접 출력 X
                    } else {
                        log.info(field.getName() + ": " + value);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            log.error("Error accessing field values", e);
        }
    }

    /** 기기없는 그룹 삭제 함수 */
    public void deleteNoDeviceGroup(){
        // Branch Test
        List<String> inviteIdxList = deviceMapper.getInviteGroupIdxList();
        log.info("inviteIdxList: " + inviteIdxList);

        List<String> registIdxList= deviceMapper.getRegistGroupIdxList();
        log.info("registIdxList: " + registIdxList);

        // registIdxList를 Set으로 변환 (검색 시간 최적화)
        Set<String> registIdxSet = new HashSet<>(registIdxList);

        // Stream + Set을 사용해 리스트 변환
        List<String> uniqueInviteIdxList = inviteIdxList.stream()
                .filter(idx -> !registIdxSet.contains(idx)) // Set에서 검색
                .collect(Collectors.toList());

        log.info("inviteIdxList에서 registIdxList에 없는 값: " + uniqueInviteIdxList);
        if(!uniqueInviteIdxList.isEmpty()){
            int result = deviceMapper.deleteNoDeviceGroupByList(uniqueInviteIdxList);
            log.info("DELETE GROUP RESULT: " + result);
        }
    }

    public String putQuotes(String conValue ) throws JsonProcessingException {
        // 1. Key-Value 파싱을 위한 정규식
        Pattern pattern = Pattern.compile("([a-zA-Z0-9]+):([^,}]+)");
        Matcher matcher = pattern.matcher(conValue);

        // 2. Key-Value를 LinkedHashMap에 저장
        Map<String, String> map = new LinkedHashMap<>();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();

            // 값에서 공백이 포함된 경우 처리 (e.g., 날짜/시간 값)
            if (value.contains(" ")) {
                value = value.replace(" ", "T"); // ISO 8601 형식으로 변경 가능
            }

            map.put(key, value);
        }
        // 3. Jackson ObjectMapper로 JSON 변환
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.writeValueAsString(map);
    }
}
