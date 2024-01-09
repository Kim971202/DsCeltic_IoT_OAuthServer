package com.oauth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.oauth.dto.member.MemberDTO;

import java.util.List;

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


}
