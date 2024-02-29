package com.oauth.mapper;

import com.oauth.dto.AuthServerDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface DeviceMapper {

    public AuthServerDTO getSerialNumberBydeviceId(String deviceId);
    public List<AuthServerDTO> deviceAuthCheck(List<AuthServerDTO> device);
    public AuthServerDTO deviceTempAuthCheck(List<AuthServerDTO> device);
    public List<AuthServerDTO> getDeviceAuthCheckValuesByUserId(String device);
    public int changeDeviceNickname(AuthServerDTO device);
    public int insertDeviceModelCode(AuthServerDTO device);
    public int insertDevice(AuthServerDTO device);
    public int insertDeviceRegist(AuthServerDTO device);
    public int insertDeviceDetail(AuthServerDTO device);
    public int updateDeviceRegistLocation(AuthServerDTO device);
    public int updateDeviceDetailLocation(AuthServerDTO device);
    public List<AuthServerDTO> getControlAuthKeyByUserId(String userId);
    public List<AuthServerDTO> getDeviceNicknameAndDeviceLocNickname(List<AuthServerDTO> device);
}
