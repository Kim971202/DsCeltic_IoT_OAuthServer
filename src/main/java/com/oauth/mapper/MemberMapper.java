package com.oauth.mapper;

import com.oauth.dto.gw.DeviceStatusInfo;
import org.apache.ibatis.annotations.Mapper;

import com.oauth.dto.AuthServerDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberMapper {

    public List<AuthServerDTO> getGroupInfoByDeviceId(List<AuthServerDTO> deviceIdList);
    public List<AuthServerDTO> getRegistDeviceIdByUserId(AuthServerDTO params);
    public List<AuthServerDTO> getGroupIdxByUserIdAndIdx(AuthServerDTO params);
    public List<AuthServerDTO> getGroupIdxByUserId(String userId);
    public List<AuthServerDTO> getUserByHp(String userHp);
    public List<AuthServerDTO> getDeviceIdByUserId(String userId);
    public List<AuthServerDTO> getInvitationList (String requestUserId);
    public List<AuthServerDTO> getPushInfoList(AuthServerDTO params);
    public List<AuthServerDTO> getNoticeList(AuthServerDTO params);
    public List<AuthServerDTO> getPushCodeStatus(@Param("userId") String params, @Param("deviceIds") String deviceIds);
    public List<AuthServerDTO> getWorkTime(AuthServerDTO param);
    public List<AuthServerDTO> getUserIdsByDeviceId(AuthServerDTO params);
    public List<AuthServerDTO> getAllUserIdsByDeviceId(String deviceId);
    public List<AuthServerDTO> getDeviceIdByUserIds(String userId);
    public List<AuthServerDTO> getPushYnStatusByUserIds(List<AuthServerDTO> userIds);
    public List<AuthServerDTO> getPushTokenByUserIds(List<AuthServerDTO> userIds);
    public List<AuthServerDTO> getFailyMemberByUserId(AuthServerDTO param);
    public List<AuthServerDTO> getFamilyMemberByGroupIdxList(List<String> userId);
    public List<AuthServerDTO> getMemberByGroupIdxList(AuthServerDTO param);
    public List<AuthServerDTO> getGroupMemberByUserId(String requestUserId);
    public List<AuthServerDTO> getSafeAlarmSet();
    public List<AuthServerDTO> getUserIdFromDeviceGroup(String deviceId);
    public List<AuthServerDTO> getValveStatusList(String userId);
    public List<AuthServerDTO> getGroupIdByUserId(String userId);
    public List<AuthServerDTO> getDeviceIdFromRegist(AuthServerDTO param);
    public List<AuthServerDTO> getUserIdListByPushToken(AuthServerDTO param);
    public List<AuthServerDTO> getUserNicknameAndPushTokenByUserId(List<AuthServerDTO> userIds);
    public AuthServerDTO getAwayHomeModeInfo(AuthServerDTO param);
    public AuthServerDTO getGroupNameByGroupIdx(String groupIdx);
    public AuthServerDTO getUserIdByHp(String hp);
    public AuthServerDTO getSafeAlarmInfo(AuthServerDTO params);
    public AuthServerDTO getSafeAlarmInfoCount(AuthServerDTO params);
    public AuthServerDTO checkValveStatusByUserId(String userId);
    public AuthServerDTO getSafeAlarmTimeDiff(String deviceId);
    public AuthServerDTO getGroupInfoForPush(String deviceId);
    public AuthServerDTO getSinglePushCodeStatus(AuthServerDTO params);
    public AuthServerDTO getPhoneIdInfo(String userId);
    public AuthServerDTO checkSafeAlarmSet(AuthServerDTO params);
    public AuthServerDTO checkLastIndex(AuthServerDTO params);
    public AuthServerDTO getInviteCount(AuthServerDTO param);
    public AuthServerDTO getInviteCountFromInviteStatus(AuthServerDTO param);
    public AuthServerDTO getInviteCountByReqeustResponseUserId(AuthServerDTO param);
    public AuthServerDTO getDeviceNicknameByDeviceId(String deviceId);
    public AuthServerDTO getDeviceNicknameBySubDeviceId(String deviceId);
    public AuthServerDTO checkDuplicateHp(String newHp);
    public AuthServerDTO checkDuplicateHpByUserId(AuthServerDTO member);
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
    public AuthServerDTO getDeviceCountFromRegist(AuthServerDTO info);
    public AuthServerDTO getUserNickname(String userId);
    public AuthServerDTO getPushTokenByUserId(String userId);
    public AuthServerDTO getPushYnStatusByDeviceIdAndUserId(AuthServerDTO info);
    public AuthServerDTO getFirstDeviceUser(String deviceId);
    public AuthServerDTO getFwhInfo(String deviceId);
    public AuthServerDTO getFanLifeStatus(String deviceId);
    public AuthServerDTO checkDuplicateGroupName(AuthServerDTO info);
    public AuthServerDTO getUserLoginoutStatus(String userId);
    public String deleteMemberFromService(String userId);
    public String deleteControllerMapping(AuthServerDTO member);
    public String deleteEachRoomrMapping(AuthServerDTO member);
    public int updatePhoneId(AuthServerDTO info);
    public int updateSafePushAlarmTime(AuthServerDTO info);
    public int updateLoginoutStatus(AuthServerDTO info);
    public int updateSafeAlarm(AuthServerDTO info);
    public int updateNewHouseHolder(AuthServerDTO info);
    public int updateDeviceRegist(AuthServerDTO info);
    public int updateUserDevice(AuthServerDTO info);
    public int updateInviteGroup(AuthServerDTO info);
    public int updateAwayHomeMode(AuthServerDTO info);
    public int insertAwayHomeMode(AuthServerDTO info);
    public int insertInviteGroup(AuthServerDTO info);
    public int insertUserDevicePushByList(List<AuthServerDTO> info);
    public int insertInviteGroupMember(AuthServerDTO info);
    public int insertWorkTime(List<AuthServerDTO> member);
    public int updateGrpDeviceInfoTableForNewHousehold(AuthServerDTO member);
    public int updateGrpInfoTableForNewHousehold(AuthServerDTO member);
    public int deleteUserDevicePush(List<AuthServerDTO> authServerDTOList);
    public int deleteDeviceGrpInfo(List<AuthServerDTO> authServerDTOList);
    public int deleteUserInviteGroup(AuthServerDTO member);
    public int deleteUserInviteGroupByGroupIdx(String groupIdx);
    public int deleteInviteStatusByHouseholdMembers(AuthServerDTO member);
    public int deleteInviteStatusByHouseholder(AuthServerDTO member);
    public int deleteInviteStatusByGroupIdx(String groupIdx);
    public int delHouseholdMember(String userId);
    public int insertHouseholder(AuthServerDTO member);
    public int updateRegistTable(AuthServerDTO member);
    public int InsertSafeAlarmSet(AuthServerDTO member);
    public int updateSafeAlarmSet(DeviceStatusInfo.Device deviceStatusInfo);
    public int updateSafeAlarmSetByBcDt(DeviceStatusInfo.Device deviceStatusInfo);
    public int insertPushHistory(AuthServerDTO member);
    public int updatePushToken(AuthServerDTO member);
    public int insertUserDevicePush(AuthServerDTO member);
    public int insertMember(AuthServerDTO member);
    public int insertAccount(AuthServerDTO member);
    public int updatePassword(AuthServerDTO member);
    public int updateHp(AuthServerDTO member);
    public int updateUserNickname(AuthServerDTO member);
    public int updateGrpNick(AuthServerDTO member);
    public int inviteHouseMember(AuthServerDTO member);
    public int acceptInvite(AuthServerDTO member);
    public int updateLoginStatusByUserIdList(List<AuthServerDTO> userIdList);
    public int updatePushCodeStatus(List<AuthServerDTO> authServerDTOList);
    public int updatePushCodeStatusSingle(AuthServerDTO param);
    public int updateDeviceLocationNicknameDeviceDetail(AuthServerDTO member);
    public int updateDeviceLocationNicknameDeviceRegist(AuthServerDTO member);
    public int insertCommandHistory(AuthServerDTO member);
    public int updateLoginDatetime(AuthServerDTO member);
}
