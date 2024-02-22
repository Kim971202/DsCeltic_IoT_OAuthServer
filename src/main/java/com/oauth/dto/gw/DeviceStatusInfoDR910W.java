package com.oauth.dto.gw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeviceStatusInfoDR910W {

   private ArrayList<Device> devices;

   private static DeviceStatusInfoDR910W dr910W;
   public DeviceStatusInfoDR910W() {devices = new ArrayList<>();}

   public static DeviceStatusInfoDR910W getInstance() {
      if (dr910W == null) {
         dr910W = new DeviceStatusInfoDR910W();
      }
      return dr910W;
   }

   private String modelCategoryCode;
   private String deviceStatus;
   private Device device;
   private String uuId;
   private String functionId;

   public void addDr910W(Device device) {devices.add(device);}
   public void setDevices(List<Device> devices) {
      this.devices.addAll(devices);
   }

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
      private Map<String, Object> rsCf;
      private String ftMd;
      private String bCdt;
      private String chTp;
      private String cwTp;
      private String hwSt;
      private String slCd;
      private String mfDt;

      private String modelCategoryCode;
      private String deviceNickName;
      private String regSort;
      private String deviceId;
      private String controlAuthKey;
      private String deviceStatus;
      private String addrNickname;

   }
}
