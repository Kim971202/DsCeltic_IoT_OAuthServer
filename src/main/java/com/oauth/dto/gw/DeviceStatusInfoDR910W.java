package com.oauth.dto.gw;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonNaming()
public class DeviceStatusInfoDR910W {

   private DeviceStatusInfoDR910W() {}

   private static DeviceStatusInfoDR910W dr910W = new DeviceStatusInfoDR910W();

   public static DeviceStatusInfoDR910W getInstance(){
      return dr910W;
   }

   private String modelCategoryCode;
   private String deviceStatus;
   private List<String> rKey;
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
   private String mfDt;
   private String type24h;
   private String slCd;
   private String blCf;
   private String hwSt;
   private String fcLc;

   @Override
   public String toString() {
      return "modelCategoryCode='" + modelCategoryCode + '\'' +
              ", deviceStatus='" + deviceStatus + '\'' +
              ", rKey='" + rKey + '\'' +
              ", powr='" + powr + '\'' +
              ", opMd='" + opMd + '\'' +
              ", htTp='" + htTp + '\'' +
              ", wtTp='" + wtTp + '\'' +
              ", hwTp='" + hwTp + '\'' +
              ", rsCf=" + rsCf +
              ", ftMd='" + ftMd + '\'' +
              ", bCdt='" + bCdt + '\'' +
              ", chTp='" + chTp + '\'' +
              ", cwTp='" + cwTp + '\'' +
              ", mfDt='" + mfDt + '\'' +
              ", type24h='" + type24h + '\'' +
              ", slCd='" + slCd + '\'' +
              ", blCf='" + blCf + '\'' +
              ", hwSt='" + hwSt + '\'' +
              ", fcLc='" + fcLc + '\'' +
              '}';
   }
}
