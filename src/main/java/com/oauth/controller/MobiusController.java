package com.oauth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.service.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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


        String aeName = "boiler";
        String cntName = "request";

        mobiusService.createCin(aeName, cntName, con);

    }

}
