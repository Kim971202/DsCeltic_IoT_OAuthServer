package com.oauth.mapper;

import com.oauth.dto.authServerDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper {

    public authServerDTO getSerialNumberBydeviceId(String deviceId);

}
