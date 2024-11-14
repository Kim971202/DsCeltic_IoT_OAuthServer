package com.oauth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.oauth.dto.AuthServerDTO;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface MemberMapper {

    public List<AuthServerDTO> getRegistDeviceIdByUserId(AuthServerDTO params);
    public List<AuthServerDTO> getGroupIdxByUserId(String userId);
    public List<AuthServerDTO> getUserByHp(String userHp);
    public List<AuthServerDTO> getDeviceIdByUserId(String userId);
    public List<AuthServerDTO> getInvitationList (String requestUserId);
    public List<AuthServerDTO> getPushInfoList(AuthServerDTO params);
    public List<AuthServerDTO> getNoticeList();
    public List<AuthServerDTO> getPushCodeStatus(@Param("userId") String params, @Param("deviceIds") String deviceIds);
    public List<AuthServerDTO> getWorkTime(AuthServerDTO param);
    public List<AuthServerDTO> getUserIdsByDeviceId(AuthServerDTO params);
    public List<AuthServerDTO> getAllUserIdsByDeviceId(String deviceId);
    public List<AuthServerDTO> getInviteGroupIdx(String deviceId);
    public List<AuthServerDTO> getPushYnStatusByUserIds(List<AuthServerDTO> userIds);
    public List<AuthServerDTO> getFailyMemberByUserId(String userId);
    public List<AuthServerDTO> getFamilyMemberByGroupIdxList(List<String> userId);
    public List<AuthServerDTO> getGroupMemberByUserId(String requestUserId);
    public List<AuthServerDTO> getSafeAlarmSet();
    public List<AuthServerDTO> getUserIdFromDeviceGroup(String deviceId);
    public List<AuthServerDTO> getGroupIdByUserId(String userId);
    public AuthServerDTO getDeviceNicknameByDeviceId(String deviceId);
    public AuthServerDTO getGroupLeaderId(Long idx);
    public AuthServerDTO getGroupLeaderIdByGroupIdx(String groupIdx);
    public AuthServerDTO getUserByUserId(String userId);
    public AuthServerDTO getAccountByUserId(String userId);
    public AuthServerDTO getNextUserId(AuthServerDTO member);
    public AuthServerDTO getUserByUserIdAndHp(AuthServerDTO member);
    public AuthServerDTO getPasswordByUserId(String userId);
    public AuthServerDTO identifyRKey(String deviceId);
    public AuthServerDTO getDeviceIdFromDeviceGroup(String groupId);
    public AuthServerDTO getHpByUserId(String userId);
    public AuthServerDTO getHouseholdByUserId(String userId);
    public AuthServerDTO getNextHouseholder(String userId);
    public AuthServerDTO getUserNickname(String userId);
    public AuthServerDTO getPushTokenByUserId(String userId);
    public AuthServerDTO getPushYnStatusByDeviceIdAndUserId(AuthServerDTO info);
    public AuthServerDTO getFirstDeviceUser(String deviceId);
    public AuthServerDTO getFwhInfo(String deviceId);
    public String deleteMemberFromService(String userId);
    public String deleteControllerMapping(AuthServerDTO member);
    public int updateDeviceRegist(AuthServerDTO info);
    public int updateUserDevice(AuthServerDTO info);
    public int updateInviteGroup(AuthServerDTO info);
    public int insertInviteGroup(AuthServerDTO info);
    public int insertUserDevicePushByList(List<AuthServerDTO> info);
    public int insertInviteGroupMember(AuthServerDTO info);
    public int insertWorkTime(List<AuthServerDTO> member);
    public int updateGrpDeviceInfoTableForNewHousehold(AuthServerDTO member);
    public int updateGrpInfoTableForNewHousehold(AuthServerDTO member);
    public int deleteUserDevicePush(String userId);
    public int deleteUserInviteGroup(AuthServerDTO member);
    public int delHouseholdMember(String userId);
    public int insertHouseholder(AuthServerDTO member);
    public int updateRegistTable(AuthServerDTO member);
    public int InsertSafeAlarmSet(AuthServerDTO member);
    public int insertPushHistory(AuthServerDTO member);
    public int updatePushToken(AuthServerDTO member);
    public int insertUserDevicePush(AuthServerDTO member);
    public int insertMember(AuthServerDTO member);
    public int insertAccount(AuthServerDTO member);
    public int updatePassword(AuthServerDTO member);
    public int updateUserNicknameAndHp(AuthServerDTO member);
    public int updateGrpNick(AuthServerDTO member);
    public int inviteHouseMember(AuthServerDTO member);
    public int acceptInvite(AuthServerDTO member);
    public int updatePushCodeStatus(AuthServerDTO params);
    public int updateDeviceLocationNicknameDeviceDetail(AuthServerDTO member);
    public int updateDeviceLocationNicknameDeviceRegist(AuthServerDTO member);
    public int insertCommandHistory(AuthServerDTO member);
    public int updateLoginDatetime(AuthServerDTO member);
}
