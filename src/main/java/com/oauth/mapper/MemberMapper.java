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

}
