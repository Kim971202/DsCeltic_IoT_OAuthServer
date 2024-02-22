package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

public class DeviceStatusInfo {

    private String modelCategoryCode;
    private List<HashMap<String, Object>> weekList;
    private DeviceStatus deviceStatus;

    @Getter
    @Setter
    public static class DeviceStatus{
        private String rKey;
        private String powr;
        private String opMd;
        private String htTp;
        private String wtTp;
        private String hwTp;
        private String ftMd;
        private String bCdt;
        private String chTp;
        private String cwTp;
        private String mfDt;
        private String type24h;
        private String slCd;
        private String hwSt;
        private String fcLc;
    }

}
