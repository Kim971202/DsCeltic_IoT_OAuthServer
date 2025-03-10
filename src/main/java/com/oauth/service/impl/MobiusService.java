package com.oauth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.dto.mobius.AeDTO;
import com.oauth.dto.mobius.CinDTO;
import com.oauth.dto.mobius.CntDTO;
import com.oauth.dto.mobius.SubDTO;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.utils.Common;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class MobiusService {

    @Autowired
    DeviceMapper deviceMapper;
    @Value("${app.server.address.short.gw}")
    private String shortGwServerAddr;
    @Value("${app.server.address.long.gw}")
    private String longGwServerAddr;
    @Value("${app.server.address.push}")
    private String pushServerAddr;
    ObjectMapper objectMapper = new ObjectMapper();
    private static PoolingHttpClientConnectionManager connectionManager = null;
    private static CloseableHttpClient httpClient;
    private static int requestIndex = 0;

    public CloseableHttpClient getHttpClient() {
        log.info("InteractionRequest -> getHttpClient CALLED");
        if(connectionManager == null) {
            connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(500);
            connectionManager.setDefaultMaxPerRoute(50);
            RequestConfig config = RequestConfig.custom()
                    .setConnectionRequestTimeout(500)
                    .setConnectTimeout(10)
                    .setSocketTimeout(2000)
                    .setExpectContinueEnabled(true).build();

            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(config)
                    .setConnectionManager(connectionManager)
                    .build();
        }
        return httpClient;
    }

    private MobiusResponse pickupResponse(URI uri, String reqBody, HttpResponse response) throws ParseException, IOException{

        MobiusResponse mobiusResponse = new MobiusResponse();

        int responseCode = response.getStatusLine().getStatusCode();
        mobiusResponse.setResponseCode(String.valueOf(responseCode));

        HttpEntity responseEntity = response.getEntity();
        String responseString = EntityUtils.toString(responseEntity);
        mobiusResponse.setResponseContent(responseString);

        // log.info("FirstHeader Content-Location: " + response.getFirstHeader("Content-Location"));
        // log.info("LastHeader Content-Location: " + response.getLastHeader("Content-Location"));

        log.info("=====START==================================================================================================");
        log.info("HTTP Request URI : " + uri.toString());
        log.info("HTTP Request Body : " + reqBody);
        log.info("HTTP Response Code, dKey : " + responseCode);
        log.info("HTTP Response String : " + responseString);
        log.info("=====END==================================================================================================");

        return mobiusResponse;
    }

    public MobiusResponse createAe(String serialNumber) throws Exception {

        AeDTO aeObject = new AeDTO();
        AeDTO.Ae ae = new AeDTO.Ae();

        ae.setRn(serialNumber);
        ae.setApi("api");
        ae.setLbl(Arrays.asList("key1", "key2"));
        ae.setRr(true);
        ae.setPoa(List.of("http://127.0.0.1:7579"));
        aeObject.setDefaultValue(ae);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(aeObject);

        StringEntity entity = new StringEntity(requestBody);

        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(shortGwServerAddr)
                .setPath("/Mobius")
                .build();

        HttpPost post = new HttpPost(uri);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/vnd.onem2m-res+json;ty=2");
        post.setHeader("X-M2M-Origin", "S");
        post.setHeader("locale", "ko");
        post.setHeader("X-M2M-RI", Integer.toString(requestIndex));
        post.setEntity(entity);
        requestIndex++;

        MobiusResponse mobiusResponse = null;
        CloseableHttpResponse response = null;

        try {
            CloseableHttpClient httpClient = getHttpClient();
            response = httpClient.execute(post);
            mobiusResponse = pickupResponse(uri, requestBody, response);
            
        } catch (Exception e) {
            log.error("send to oneM2M Error : " + e);
        } finally {
            if(response != null)
                response.close();
        }
        return mobiusResponse;
    }

    public MobiusResponse createCnt(String aeName, String cntName) throws Exception {

        CntDTO cntObject = new CntDTO();
        CntDTO.Cnt cnt = new CntDTO.Cnt();

        cnt.setRn(cntName);
        cnt.setMbs(10000);

        cntObject.setDefaultValue(cnt);

        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cntObject);

        StringEntity entity = new StringEntity(requestBody);

        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(shortGwServerAddr)
                .setPath("/Mobius" + "/" + aeName)
                .build();

        HttpPost post = new HttpPost(uri);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/vnd.onem2m-res+json;ty=3");
        post.setHeader("X-M2M-Origin", "S");
        post.setHeader("locale", "ko");
        post.setHeader("X-M2M-RI", Integer.toString(requestIndex));
        post.setEntity(entity);
        requestIndex++;

        MobiusResponse mobiusResponse = null;
        CloseableHttpResponse response = null;

        try {
            CloseableHttpClient httpClient = getHttpClient();
            response = httpClient.execute(post);
            mobiusResponse = pickupResponse(uri, requestBody, response);

        } catch (Exception e) {
            log.error("send to oneM2M Error : " + e);
        } finally {
            if(response != null)
                response.close();
        }
        return mobiusResponse;
    }

    public MobiusResponse createCin(String aeName, String cntName, String con) throws Exception{
        CinDTO cinObject = new CinDTO();
        CinDTO.Cin cin = new CinDTO.Cin();

        cin.setCon(con);
        cinObject.setDefaultValue(cin);

        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cinObject);
        // log.info("requestBody: " + requestBody);
        StringEntity entity = new StringEntity(requestBody);

        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(shortGwServerAddr)
                .setPath("/Mobius" + "/" + aeName + "/" + cntName)
                .build();

        HttpPost post = new HttpPost(uri);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/vnd.onem2m-res+json;ty=4");
        post.setHeader("X-M2M-Origin", "S");
        post.setHeader("locale", "ko");
        post.setHeader("X-M2M-RI", Integer.toString(requestIndex));
        post.setEntity(entity);
        requestIndex++;

        MobiusResponse mobiusResponse = null;
        CloseableHttpResponse response = null;

        try {
            CloseableHttpClient httpClient = getHttpClient();
            response = httpClient.execute(post);
            mobiusResponse = pickupResponse(uri, requestBody, response);
            // log.info("mobiusResponse: " + mobiusResponse);
        } catch (Exception e) {
            log.error("send to oneM2M Error : " + e);
            return mobiusResponse;
        } finally {
            if(response != null)
                response.close();
        }
        return mobiusResponse;
    }

    public MobiusResponse createSub(String aeName, String cntName, String addrType) throws Exception{

        SubDTO subOject = new SubDTO();
        SubDTO.Sub sub = new SubDTO.Sub();
        SubDTO.Sub.Enc enc = new SubDTO.Sub.Enc();

        String rnName = null;
        String serverAddr = "";

        if(addrType.equals("gw")){
            rnName = "AppServerToGwServer";
            serverAddr = longGwServerAddr;
        } else if(addrType.equals("push")){
            rnName = "AppServerToPushServer";
            serverAddr = pushServerAddr;
        }

        log.info("serverAddr: " + serverAddr);

        enc.setNet(List.of(3));

        sub.setEnc(enc);
        sub.setRn(rnName);
        sub.setNu(List.of(serverAddr));
        sub.setExc(10);
        subOject.setDefaultValue(sub);

        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(subOject);

        StringEntity entity = new StringEntity(requestBody);

        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(shortGwServerAddr)
                .setPath("/Mobius" + "/" + aeName + "/" + cntName)
                .build();

        HttpPost post = new HttpPost(uri);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/vnd.onem2m-res+json;ty=4");
        post.setHeader("X-M2M-Origin", "S");
        post.setHeader("locale", "ko");
        post.setHeader("X-M2M-RI", Integer.toString(requestIndex));
        post.setEntity(entity);
        requestIndex++;

        MobiusResponse mobiusResponse = null;
        CloseableHttpResponse response = null;

        try {
            CloseableHttpClient httpClient = getHttpClient();
            response = httpClient.execute(post);
            mobiusResponse = pickupResponse(uri, requestBody, response);

        } catch (Exception e) {
            log.error("send to oneM2M Error : " + e);
        } finally {
            if(response != null)
                response.close();
        }
        return mobiusResponse;
    }

    public void actvHandler(DeviceStatusInfo.Device dr910W){

        if(deviceMapper.getActiveStautsByDeviceId(dr910W.getDeviceId()) == null) deviceMapper.insertActiveStatus(dr910W);
        else deviceMapper.updateActiveStatus(dr910W);

    }
    
    public void rtstHandler(DeviceStatusInfo.Device dr910W){
        if(deviceMapper.getDeviceStautsByDeviceId(dr910W.getDeviceId()) == null){
            // 신규 기기 INSERT
            deviceMapper.insertDeviceStatus(dr910W);
        } else {
            // 기존 기기 UPDATE
            deviceMapper.updateDeviceStatus(dr910W);
        }
    }

}
