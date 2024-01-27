package com.oauth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.oauth.dto.member.MemberDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface MemberMapper {

    public List<MemberDTO> getUserList();
    public MemberDTO getUserByUserId(String userId);
    public List<MemberDTO> getDeviceInfoByUserID(String userId);
    public int insertMember(MemberDTO member);
    public int insertAccount(MemberDTO member);
    public List<MemberDTO> getUserByHp(String userHp);
    public List<MemberDTO> getUserByDeviceId(String deviceId);
    public MemberDTO getUserByUserIdAndHp(MemberDTO member);
    public MemberDTO getUserByUserIdAndHpAndDeviceId(MemberDTO member);
    public int updatePassword(MemberDTO member);
    public MemberDTO passwordCheck(String pw);
    public int updateUserNicknameAndHp(MemberDTO member);
    public MemberDTO accessTokenCheck(MemberDTO member);
    public List<MemberDTO> getHouseMembersByUserId(List<MemberDTO> members);
    public List<MemberDTO> getDeviceIdByUserId(String userId);
    public int inviteHouseMember(MemberDTO member);
    public int acceptInvite(MemberDTO member);
    public int insertNewHouseMember(List<MemberDTO> members);
    public List<MemberDTO> getInvitationList (String requestUserId);
    public int delHouseMember(String userId);
    public int changeHouseholdStatus(MemberDTO member);
    public int updatePushCodeStatus(List<HashMap<String, String>> member);
    public int insertInitPushCode(MemberDTO member); // Device 등록 시 사용
    public MemberDTO getPushCodeStatus(MemberDTO member);
    public MemberDTO getNextHouseholderUserId(MemberDTO member);
    public int updateHouseholdTbrOprUser(String userId);
    public int updateHouseholdTbrOprUserDevice(String userId);
}
