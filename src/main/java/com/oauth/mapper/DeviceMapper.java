package com.oauth.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface DeviceMapper {

    public List<AuthServerDTO> getDeviceInfoSearchList (List<AuthServerDTO> device);
    public List<AuthServerDTO> getMultiSerialNumberBydeviceId(List<AuthServerDTO> deviceId);
    public List<AuthServerDTO> deviceAuthCheck(List<AuthServerDTO> device);
    public List<AuthServerDTO> getDeviceAuthCheckValuesByUserId(String device);
    public List<AuthServerDTO> getControlAuthKeyByUserId(String userId);
    public List<AuthServerDTO> getDeviceNicknameAndDeviceLocNickname(List<AuthServerDTO> device);
    public List<AuthServerDTO> getDeviceErroInfo(String serialNumber);
    public List<DeviceStatusInfo.Device> getDeviceStauts(List<String> serialNumber);
    public DeviceStatusInfo.Device getSingleDeviceStauts(String deviceId);
    public AuthServerDTO getSingleSerialNumberBydeviceId(String deviceId);
    public AuthServerDTO getPushTokenByUserId(String userId);
    public AuthServerDTO deviceTempAuthCheck(List<AuthServerDTO> device);
    public AuthServerDTO getDeviceInfoSearch(String userId);
    public DeviceStatusInfo.Device getDeviceStautsByDeviceId(String deviceId);
    public AuthServerDTO getPushinfoByDeviceId(String deviceId);
    public AuthServerDTO getDeviceRegistStatus(String serialNumber);
    public int changeDeviceNicknameTemp(AuthServerDTO device);
    public int changeDeviceNickname(AuthServerDTO device);
    public int insertDeviceModelCode(AuthServerDTO device);
    public int insertDevice(AuthServerDTO device);
    public int insertDeviceRegist(AuthServerDTO device);
    public int insertDeviceDetail(AuthServerDTO device);
    public int updateDeviceRegistLocation(AuthServerDTO device);
    public int updateDeviceDetailLocation(AuthServerDTO device);
    public int insertDeviceStatus(DeviceStatusInfo.Device device);
    public int updateDeviceStatus(DeviceStatusInfo.Device device);
    public int insertUserDevice(AuthServerDTO device);
    public int insertErrorInfo(AuthServerDTO device);
    public int updateDeviceStatusFromApplication(DeviceStatusInfo.Device device);
    public int insertJson(String jsonBody);



}
