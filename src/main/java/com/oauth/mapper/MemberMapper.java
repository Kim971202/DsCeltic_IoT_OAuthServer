package com.oauth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.oauth.dto.authServerDTO;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface MemberMapper {

    public List<authServerDTO> getUserList();
    public authServerDTO getUserByUserId(String userId);
    public List<authServerDTO> getDeviceInfoByUserID(String userId);
    public int insertMember(authServerDTO member);
    public int insertAccount(authServerDTO member);
    public List<authServerDTO> getUserByHp(String userHp);
    public List<authServerDTO> getUserByDeviceId(String deviceId);
    public authServerDTO getUserByUserIdAndHp(authServerDTO member);
    public authServerDTO getUserByUserIdAndHpAndDeviceId(authServerDTO member);
    public int updatePassword(authServerDTO member);
    public authServerDTO passwordCheck(String pw);
    public int updateUserNicknameAndHp(authServerDTO member);
    public authServerDTO accessTokenCheck(authServerDTO member);
    public List<authServerDTO> getHouseMembersByUserId(List<authServerDTO> members);
    public List<authServerDTO> getDeviceIdByUserId(String userId);
    public int inviteHouseMember(authServerDTO member);
    public int acceptInvite(authServerDTO member);
    public int insertNewHouseMember(List<authServerDTO> members);
    public List<authServerDTO> getInvitationList (String requestUserId);
    public int delHouseMember(String userId);
    public int changeHouseholdStatus(authServerDTO member);
    public int updatePushCodeStatus(List<HashMap<String, String>> member);
    public int insertInitPushCode(authServerDTO member); // Device 등록 시 사용
    public authServerDTO getPushCodeStatus(authServerDTO member);
    public authServerDTO getNextHouseholderUserId(authServerDTO member);
    public int updateHouseholdTbrOprUser(String userId);
    public int updateHouseholdTbrOprUserDevice(String userId);
    public int deleteMemberFromService(String userId);
}
