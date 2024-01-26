package com.oauth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oauth.service.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
@RequestMapping("/notify/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
public class MobiusController {

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;

    /** (샘플) AE, CNT, CIN, SUB 등록 후 CIN으로 GW서버 메세지 전송 */
    @PostMapping(value = "/deviceInfoUpsert")
    @ResponseBody
    public void doMobiusRequest() throws Exception{

        String con = "{\n" +
                "  \"con\": {\n" +
                "    \"rKey\": \"di12\",\n" +
                "    \"powr\": \"on\",\n" +
                "    \"opMd\": \"01\",\n" +
                "    \"htTp\": \"20.0\",\n" +
                "    \"wtTp\": \"59.0\",\n" +
                "    \"hwTp\": \"40.0\",\n" +
                "    \"rsCf\": {\n" +
                "      \"12h\": {\"hr\": \"03\", \"mn\": \"30\"},\n" +
                "      \"7wk\": [{\"wk\": \"0\", \"hs\": \"000000\"}],\n" +
                "      \"fwh\": [\n" +
                "        {\"ws\": [\"0\", \"5\", \"6\"], \"hr\": \"06\", \"mn\": \"30\"},\n" +
                "        {\"ws\": [\"0\", \"1\", \"3\"], \"hr\": \"07\", \"mn\": \"30\"}\n" +
                "      ]\n" +
                "    },\n" +
                "    \"ftMd\": \"on\",\n" +
                "    \"bCdt\": \"on\",\n" +
                "    \"chTp\": \"24.5\",\n" +
                "    \"cwTp\": \"55.0\",\n" +
                "    \"hwSt\": \"on\",\n" +
                "    \"ecOp\": \"on\",\n" +
                "    \"fcLc\": \"of\",\n" +
                "    \"blCf\": \"03\",\n" +
                "    \"mfDt\": \"20231124 20:33:10\"\n" +
                "  }\n" +
                "}";

        String modelCode = "ESCeco20S";
        String srNo = "12345";
        String subName = "AppServerToGWServer";

        String aeName = mobiusService.createAe(modelCode);
        String cntName = mobiusService.createCnt(srNo, aeName);
        mobiusService.createSub(aeName, cntName, con);

    }

    @PostMapping(value = "/sendCin")
    @ResponseBody
    public void sendCin() throws Exception{

        String myBody = "{\n" +
                "   \"m2m:sgn\":{\n" +
                "      \"sur\":\"Mobius/by120.2.481.1.1.45534365636f31335300.1/211111111156/fcLc\",\n" +
                "      \"nev\":{\n" +
                "         \"rep\":{\n" +
                "            \"m2m:cin\":{\n" +
                "               \"rn\":\"4-20240126072313168\",\n" +
                "               \"ty\":4,\n" +
                "               \"pi\":\"3-20240126065641208168\",\n" +
                "               \"ri\":\"4-20240126072313168325\",\n" +
                "               \"ct\":\"20240126T072313\",\n" +
                "               \"lt\":\"20240126T072313\",\n" +
                "               \"st\":12,\n" +
                "               \"et\":\"20260126T072313\",\n" +
                "               \"cs\":406,\n" +
                "               \"con\":{\n" +
                "                  \"rKey\":\"by12\",\n" +
                "                  \"powr\":\"on\",\n" +
                "                  \"opMd\":\"01\",\n" +
                "                  \"htTp\":\"30.0\",\n" +
                "                  \"wtTp\":\"35.0\",\n" +
                "                  \"hwTp\":\"40.0\",\n" +
                "                  \"rsCf\":{\n" +
                "                     \"24h\":{\n" +
                "                        \"hs\":[\n" +
                "                           \"01\",\n" +
                "                           \"02\",\n" +
                "                           \"16\",\n" +
                "                           \"18\"\n" +
                "                        ]\n" +
                "                     },\n" +
                "                     \"12h\":{\n" +
                "                        \"hr\":\"03\",\n" +
                "                        \"mn\":\"30\"\n" +
                "                     },\n" +
                "                     \"7wk\":[\n" +
                "                        {\n" +
                "                           \"wk\":\"0\",\n" +
                "                           \"hs\":[\n" +
                "                              \"01\",\n" +
                "                              \"18\"\n" +
                "                           ]\n" +
                "                        }\n" +
                "                     ],\n" +
                "                     \"fwh\":[\n" +
                "                        {\n" +
                "                           \"ws\":[\n" +
                "                              \"0\",\n" +
                "                              \"5\",\n" +
                "                              \"6\"\n" +
                "                           ],\n" +
                "                           \"hr\":\"06\",\n" +
                "                           \"mn\":\"30\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                           \"ws\":[\n" +
                "                              \"0\",\n" +
                "                              \"1\",\n" +
                "                              \"3\"\n" +
                "                           ],\n" +
                "                           \"hr\":\"07\",\n" +
                "                           \"mn\":\"30\"\n" +
                "                        }\n" +
                "                     ]\n" +
                "                  },\n" +
                "                  \"ftMd\":\"on\",\n" +
                "                  \"bCdt\":\"on\",\n" +
                "                  \"chTp\":\"24.5\",\n" +
                "                  \"cwTp\":\"55.0\",\n" +
                "                  \"hwSt\":\"of\",\n" +
                "                  \"fcLc\":\"of\",\n" +
                "                  \"slCd\":\"02\",\n" +
                "                  \"blCf\":\"03\",\n" +
                "                  \"mfDt\":\"20230108 20:33:10\"\n" +
                "               },\n" +
                "               \"cr\":\"SrtSt\"\n" +
                "            }\n" +
                "         },\n" +
                "         \"net\":3\n" +
                "      },\n" +
                "      \"rvi\":\"2a\"\n" +
                "   }\n" +
                "}";

        List<String> key = new ArrayList<>() {
            {
                add("functionId");
                add("uuId");
            }
        };
        List<String> value = new ArrayList<>() {
            {
                add("deviceRegist");
                add("a432e21a-54df-4e43-8ef9-99cd274dced8");
            }
        };
        String newBody = common.addCon(myBody, key, value);
        System.out.println("newBody: " + newBody);

        String functionId = common.readCon(newBody, "functionId");
        String uuId = common.readCon(newBody, "uuId");

        System.out.println("functionId: " + functionId);
        System.out.println("uuId: " + uuId);

        String aeName = "boiler";
        String cntName = "request";

        Common.setMyMap(aeName, cntName);

        //mobiusService.createCin(aeName, cntName, con);
    }

    @PostMapping(value = "/receiveCin")
    @ResponseBody
    public void receiveCin(@RequestBody String reqBody) throws Exception{
        System.out.println(Common.getMyMap("boiler"));
        //System.out.println(reqBody);
    }

}
