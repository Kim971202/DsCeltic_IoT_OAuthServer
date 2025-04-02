package com.oauth.dto.gw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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

      private String fcDf;
      private String otTp;
      private String mfcd;
      private String rsMd;
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
      private JsonObject h12Json;
      private String h24;
      private JsonObject h24Json;
      private String wk7;
      private JsonArray wk7Json;
      private String fwh;
      private JsonArray fwhJson;
      private String dvSt;

      private String fcLcActv;
      private String ftMdActv;
      private String ecOpActv;

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
      private String targetDeviceId; // 각방 전용 hex serial number 
      private String controlAuthKey;
      private String deviceStatus;
      private String addrNickname;
      private String serialNumber;
      
      private String vfLs;
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
      private String odHm;

      private String modelCode;
      private String prId;
      private String psYn;
      private String dvNm;

      private String groupIdx;
      private String groupName;
      private String latitude;
      private String longitude;
      private String tmpRegistKey;
      private String regDatetime;
      private String column;

      private String t01;
      private String t02;
      private String t03;
      private String t04;
      private String t05;
      private String t06;
      private String t07;
      private String t08;
      private String t09;
      private String t10;
      private String t11;
      private String t12;
      private String t13;
      private String t14;
      private String t15;
      private String t16;
      private String t17;
      private String t18;
      private String t19;
      private String t20;
      private String t21;
      private String t22;
      private String t23;
      private String t24;
   }
}
