package com.oauth.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface DeviceMapper {

    public List<AuthServerDTO> getDeviceInfoSearchList (List<AuthServerDTO> param);
    public List<AuthServerDTO> getDeviceInfoSearchIdx (List<String> param);
    public List<AuthServerDTO> getDeviceIdByGroupIdx (String groupIdx);
    public List<AuthServerDTO> getMultiSerialNumberBydeviceId(List<AuthServerDTO> deviceId);
    public List<AuthServerDTO> deviceAuthCheck(List<AuthServerDTO> device);
    public List<AuthServerDTO> getDeviceAuthCheckValuesByUserId(String device);
    public List<AuthServerDTO> getControlAuthKeyByUserId(AuthServerDTO param);
    public List<AuthServerDTO> getDeviceNicknameAndDeviceLocNickname(List<AuthServerDTO> device);
    public List<AuthServerDTO> getDeviceErroInfo(String serialNumber);
    public List<AuthServerDTO> getPushinfoByDeviceId(String deviceId);
    public List<String> getInviteGroupIdxList();
    public List<String> getRegistGroupIdxList();
    public List<DeviceStatusInfo.Device> getDeviceStauts(List<String> serialNumber);
    public List<DeviceStatusInfo.Device> getActiveStauts(List<String> serialNumber);
    public List<AuthServerDTO> getCheckedDeviceExist(String deviceId);
    public AuthServerDTO checkDeviceExist(String deviceId);
    public AuthServerDTO getGroupNameAndDeviceNickByDeviceId(String deviceId);
    public AuthServerDTO getSingleSerialNumberBydeviceId(String deviceId);
    public AuthServerDTO getPushTokenByUserId(String userId);
    public AuthServerDTO deviceTempAuthCheck(List<AuthServerDTO> device);
    public AuthServerDTO getDeviceInfoSearch(AuthServerDTO params);
    public AuthServerDTO getDeviceRegistStatus(String serialNumber);
    public AuthServerDTO checkDeviceStatus(AuthServerDTO params);
    public DeviceStatusInfo.Device getSingleDeviceStauts(String deviceId);
    public DeviceStatusInfo.Device getDeviceStautsByDeviceId(String deviceId);
    public DeviceStatusInfo.Device getActiveStautsByDeviceId(String deviceId);
    public int updateGroupName(AuthServerDTO params);
    public int deleteNoDeviceGroupByList(List<String> groupIdxList);
    public int insertDeviceGrpInfo(AuthServerDTO params);
    public int insertDeviceListGrpInfo(List<AuthServerDTO> device);
    public int deleteDeviceListGrpInfo(List<AuthServerDTO> device);
    public int updateDeviceErrorStatus(String deviceId);
    public int changeDeviceNicknameTemp(AuthServerDTO device);
    public int changeDeviceNickname(AuthServerDTO device);
    public int insertDeviceModelCode(AuthServerDTO device);
    public int insertDevice(AuthServerDTO device);
    public int insertDeviceRegist(AuthServerDTO device);
    public int insertDeviceDetail(AuthServerDTO device);
    public int updateDeviceRegistLocation(AuthServerDTO device);
    public int updateDeviceRegistGroupName(AuthServerDTO device);
    public int updateDeviceDetailLocation(AuthServerDTO device);
    public int insertDeviceStatus(DeviceStatusInfo.Device device);
    public int insertActiveStatus(DeviceStatusInfo.Device device);
    public int updateDeviceStatus(DeviceStatusInfo.Device device);
    public int updateActiveStatus(DeviceStatusInfo.Device device);
    public int insertUserDevice(AuthServerDTO device);
    public int insertErrorInfo(AuthServerDTO device);
    public int updateDeviceStatusFromApplication(DeviceStatusInfo.Device device);
    public int insertJson(String jsonBody);
    public int updateSleepMode(AuthServerDTO params);
    public AuthServerDTO checkDeviceAuthkeyExist(AuthServerDTO params);
    public int updateUserDevice(AuthServerDTO params);
    public int updateDeviceDetail(AuthServerDTO params);
    public int updateDeviceRegist(AuthServerDTO params);
    public int insertFristDeviceUser(AuthServerDTO params);
    // TBR_FIRST_DEVICE_USER
}
