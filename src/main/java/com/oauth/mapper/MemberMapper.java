package com.oauth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.oauth.dto.AuthServerDTO;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface MemberMapper {

    public List<AuthServerDTO> getUserList();
    public AuthServerDTO getUserByUserId(String userId);
    public AuthServerDTO getAccountByUserId(String userId);
    public List<AuthServerDTO> getDeviceInfoByUserID(String userId);
    public int insertMember(AuthServerDTO member);
    public int insertAccount(AuthServerDTO member);
    public List<AuthServerDTO> getUserByHp(String userHp);
    public List<AuthServerDTO> getUserByDeviceId(String deviceId);
    public AuthServerDTO getUserByUserIdAndHp(AuthServerDTO member);
    public AuthServerDTO getUserByUserIdAndHpAndDeviceId(AuthServerDTO member);
    public int updatePassword(AuthServerDTO member);
    public AuthServerDTO getPasswordByUserId(String userId);
    public int updateUserNicknameAndHp(AuthServerDTO member);
    public AuthServerDTO accessTokenCheck(AuthServerDTO member);
    public List<AuthServerDTO> getHouseMembersByUserId(List<AuthServerDTO> members);
    public List<AuthServerDTO> getDeviceIdByUserId(String userId);
    public int inviteHouseMember(AuthServerDTO member);
    public int acceptInvite(AuthServerDTO member);
    public int insertNewHouseMember(List<AuthServerDTO> members);
    public List<AuthServerDTO> getInvitationList (String requestUserId);
    public int delHouseMember(String userId);
    public int changeHouseholdStatus(AuthServerDTO member);
    public int updatePushCodeStatus(List<HashMap<String, String>> member);
    public int insertInitPushCode(AuthServerDTO member); // Device 등록 시 사용
    public AuthServerDTO getPushCodeStatus(AuthServerDTO member);
    public AuthServerDTO getNextHouseholderUserId(AuthServerDTO member);
    public int updateHouseholdTbrOprUser(String userId);
    public int updateHouseholdTbrOprUserDevice(String userId);
    public String deleteMemberFromService(String userId);
    public String deleteControllerMapping(AuthServerDTO member);
    public List<AuthServerDTO> getPushInfoList(String userId);
    public List<AuthServerDTO> getNoticeList();
    public int updateDeviceLocationNicknameDeviceDetail(AuthServerDTO member);
    public int updateDeviceLocationNicknameDeviceRegist(AuthServerDTO member);
    public AuthServerDTO getUserIdByUserId (String userId);
}
