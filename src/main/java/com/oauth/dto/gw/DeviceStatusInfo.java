package com.oauth.dto.gw;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeviceStatusInfo {

   private ArrayList<Device> devices;

   private static DeviceStatusInfo dr910W;
   public DeviceStatusInfo() {devices = new ArrayList<>();}

   public static DeviceStatusInfo getInstance() {
      if (dr910W == null) {
         dr910W = new DeviceStatusInfo();
      }
      return dr910W;
   }
   public void addDr910W(Device device) {devices.add(device);}
   public void setDevices(List<Device> devices) {this.devices.addAll(devices);}

   private String modelCategoryCode;
   private String deviceStatus;
   private Device device;
   private String uuId;
   private String functionId;



   @Data
   @Getter
   @Setter
   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public static class Device {

      private String mfcd;
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
      private String hwSt;
      private String slCd;
      private String mfDt;
      private String ecOp;
      private String fcLc;
      private String blCf;
      private String h12;
      private String h24;
      private String wk7;
      private String fwh;

      private String past;
      private String inDr;
      private String inCl;
      private String ecSt;

      private String new24h;
      private String new12h;
      private String new7wk;

      private String old24h;
      private String old12h;
      private String old7wk;

      private String modelCategoryCode;
      private String deviceNickName;
      private String regSort;
      private String deviceId;
      private String controlAuthKey;
      private String deviceStatus;
      private String addrNickname;
      private String serialNumber;
      
      private String vtSp;
      private String onHour;
      private String onMinute;
      private String offHour;
      private String offMinute;
      private String pw;                  // 전원ON/OFF예약
      private String hr;                  // 전원ON/OFF예약 시간 타이머
      private String mn;                  // 전원ON/OFF예약 분 타이머
      private String ven7Wk;
      private String rsSl;
      private String rsPw;
      private String inAq;
   }
}
