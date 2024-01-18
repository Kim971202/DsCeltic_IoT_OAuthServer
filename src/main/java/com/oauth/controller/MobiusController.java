package com.oauth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
//@RequestMapping("/notify/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
public class MobiusController {

    @Autowired
    Common common;

    /**  */
    @PostMapping(value = "/mobiusRequest")
    @ResponseBody
    public void doMobiusRequest(HttpServletRequest request, HttpServletResponse response, @RequestBody String reqBdoy) throws Exception{

        System.out.println(reqBdoy);
        String conResult = common.dr910WDeviceRegist(reqBdoy);



    }

}
