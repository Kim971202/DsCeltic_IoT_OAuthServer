package com.oauth.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfoDR910W;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class Common {

    private static HashMap<String, String> mymap = new HashMap<>();

    public static String getMyMap(String rKey){
        return mymap.get(rKey);
    }

    public static void setMyMap(String rKey, String srNo){
        mymap.put(rKey, srNo);
    }

    @Autowired
    MemberMapper memberMapper;

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

    public boolean tokenVerify(AuthServerDTO params) {
        System.out.println(params);
        AuthServerDTO member = memberMapper.accessTokenCheck(params);
        if(member == null) return false;

        List<String> userId = Common.extractJson(member.toString(), "userId");
        List<String> accessToken = Common.extractJson(member.toString(), "accessToken");
        System.out.println(userId);
        System.out.println(accessToken);
        return userId != null && accessToken != null;
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
                System.out.println("Unsupported key: " + targetKey);
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

    // DR-910W 주기 보고 메세지 처리
    public String dr910WDeviceRegist(String jsonString) throws Exception{
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonString);

        JsonNode conNode = jsonNode.path("m2m:sgn").path("nev").path("rep").path("m2m:cin").path("con");
        JsonNode rsCfNode = conNode.path("rsCf");

        String rKey = conNode.path("rKey").asText();
        String powr = conNode.path("powr").asText();
        String opMd = conNode.path("opMd").asText();
        String htTp = conNode.path("htTp").asText();
        String wtTp = conNode.path("wtTp").asText();
        String hwTp = conNode.path("hwTp").asText();
        String ftMd = conNode.path("ftMd").asText();
        String bCdt = conNode.path("bCdt").asText();
        String chTp = conNode.path("chTp").asText();
        String cwTp = conNode.path("cwTp").asText();
        String hwSt = conNode.path("hwSt").asText();
        String slCd = conNode.path("slCd").asText();
        String mfDt = conNode.path("mfDt").asText();

        String hr12 = rsCfNode.path("12h").path("hr").asText();
        String mn12 = rsCfNode.path("12h").path("mn").asText();

        System.out.println(rsCfNode.path("24h").path("hs").asText());

        for (JsonNode entry : rsCfNode.path("7wk")){
            String wkValue = entry.path("wk").asText();
            JsonNode hsCode = entry.path("hs");
            System.out.println("wk value: " + wkValue);
            System.out.println("mn value: " + hsCode);
        }
        return conNode.toString();
    }

    public String readCon(String jsonString,String value) throws Exception{
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonString);

        JsonNode baseNode = jsonNode.path("m2m:sgn").path("nev").path("rep").path("m2m:cin");
        JsonNode conNode = jsonNode.path("m2m:sgn").path("nev").path("rep").path("m2m:cin").path("con");
        JsonNode returnNode = conNode.path(value);
        String returnValue = objectMapper.writeValueAsString(returnNode);

        // TODO: 추후 True/False 로 분기 할것
        if(value.equals("rsCf")) return returnValue;
        else return returnValue.replace("\"", "");
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
            e.printStackTrace();
        }
        return modifiedJson;
    }

    public Map<String, Object> changeStringToJson (String json) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String changeStringToJson(DeviceStatusInfoDR910W obj) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "Error converting to JSON";
        }
    }
}
