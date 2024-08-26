package com.oauth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.oauth.dto.AuthServerDTO;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface MemberMapper {

    public List<AuthServerDTO> getUserList();
    public List<AuthServerDTO> getUserByHp(String userHp);
    public List<AuthServerDTO> getDeviceInfoByUserID(String userId);
    public List<AuthServerDTO> getDeviceIdListByUserId(String userId);
    public List<AuthServerDTO> getUserByDeviceId(String deviceId);
    public List<AuthServerDTO> getHouseMembersByUserId(List<AuthServerDTO> members);
    public List<AuthServerDTO> getDeviceIdByUserId(String userId);
    public List<AuthServerDTO> getInvitationList (String requestUserId);
    public List<AuthServerDTO> getPushInfoList(AuthServerDTO params);
    public List<AuthServerDTO> getNoticeList();
    public List<AuthServerDTO> getPushCodeStatus(@Param("userId") String params, @Param("deviceIds") String deviceIds);
    public List<AuthServerDTO> getWorkTime(AuthServerDTO param);
    public List<AuthServerDTO> getUserIdsByDeviceId(String deviceId);
    public List<AuthServerDTO> getPushYnStatusByUserIds(List<AuthServerDTO> userIds);
    public List<AuthServerDTO> getUserIdByGroupKey(String requestUserId);
    public List<AuthServerDTO> getDeviceIdFromRegistTable(String userId);
    public AuthServerDTO getPushYnStatus(AuthServerDTO userId);
    public AuthServerDTO getUserByUserId(String userId);
    public AuthServerDTO getAccountByUserId(String userId);
    public AuthServerDTO getUserByUserIdAndHp(AuthServerDTO member);
    public AuthServerDTO getUserByUserIdAndHpAndDeviceId(AuthServerDTO member);
    public AuthServerDTO getPasswordByUserId(String userId);
    public AuthServerDTO accessTokenCheck(AuthServerDTO member);
    public AuthServerDTO getNextHouseholderUserId(AuthServerDTO member);
    public AuthServerDTO identifyRKey(String deviceId);
    public AuthServerDTO getHpByUserId(String userId);
    public String deleteMemberFromService(String userId);
    public String deleteControllerMapping(AuthServerDTO member);
    public int insertDeviceRegistFromSelect(AuthServerDTO member);
    public int UpdateSafeAlarmSet(AuthServerDTO member);
    public int insertPushHistory(AuthServerDTO member);
    public int updatePushToken(AuthServerDTO member);
    public int insertUserDevicePush(AuthServerDTO member);
    public int insertMember(AuthServerDTO member);
    public int insertAccount(AuthServerDTO member);
    public int updatePassword(AuthServerDTO member);
    public int updateUserNicknameAndHp(AuthServerDTO member);
    public int inviteHouseMember(AuthServerDTO member);
    public int acceptInvite(AuthServerDTO member);
    public int insertNewHouseMember(List<AuthServerDTO> members);
    public int delHouseMember(String userId);
    public int changeHouseholdStatus(AuthServerDTO member);
    public int updatePushCodeStatus(AuthServerDTO params);
    public int insertInitPushCode(AuthServerDTO member); // Device 등록 시 사용
    public int updateHouseholdTbrOprUser(String userId);
    public int updateHouseholdTbrOprUserDevice(String userId);
    public int updateDeviceLocationNicknameDeviceDetail(AuthServerDTO member);
    public int updateDeviceLocationNicknameDeviceRegist(AuthServerDTO member);
    public int insertCommandHistory(AuthServerDTO member);
    public int updateLoginDatetime(AuthServerDTO member);
}
