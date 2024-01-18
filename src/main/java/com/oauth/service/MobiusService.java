package com.oauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.mobius.AeDTO;
import com.oauth.dto.mobius.CntDTO;
import com.oauth.utils.Common;
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
import org.springframework.stereotype.Service;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Service
public class MobiusService {

    @Autowired
    Common common;
    private String inCSEAddress = "localhost:7579";
    ObjectMapper objectMapper = new ObjectMapper();
    private static PoolingHttpClientConnectionManager connectionManager = null;
    private static CloseableHttpClient httpClient;
    private static int requestIndex = 0;

    public CloseableHttpClient getHttpClient() {
        System.out.println("InteractionRequest -> getHttpClient CALLED");
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
        mobiusResponse.setResponseCode(responseCode);

        HttpEntity responseEntity = response.getEntity();
        String responseString = EntityUtils.toString(responseEntity);
        mobiusResponse.setResponseContent(responseString);

        System.out.println("====HTTP Request URI===============================================================================");
        System.out.println("HTTP Request URI : " + uri.toString());
        System.out.println("====HTTP Request Body=================================================================================");
        System.out.println("HTTP Request Body : " + reqBody);
        System.out.println("====HTTP Response Code=================================================================================");
        System.out.println("HTTP Response Code, dKey : " + responseCode);
        System.out.println("====HTTP Response String=================================================================================");
        System.out.println("HTTP Response String : " + responseString);

        return mobiusResponse;
    }

    public void createAe(String modelCode) throws Exception {

        AeDTO aeObject = new AeDTO();
        AeDTO.Ae ae = new AeDTO.Ae();

        ae.setRn(modelCode + "." + common.getTimeAsiaSeoulNow());
        ae.setApi("api");
        ae.setLbl(Arrays.asList("key1", "key2"));
        ae.setRr(true);
        ae.setPoa(List.of("http://127.0.0.1:7579"));
        aeObject.setDefaultValue(ae);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(aeObject);

        StringEntity entity = new StringEntity(requestBody);

        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(inCSEAddress)
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
            System.out.println("send to oneM2M Error : " + e);
        } finally {
            response.close();
        }
    }

    public void createCnt(String srNo, String aeName) throws Exception {

        CntDTO cntObject = new CntDTO();
        CntDTO.Cnt cnt = new CntDTO.Cnt();

        cnt.setRn(srNo + "." + common.getTimeAsiaSeoulNow());
        cnt.setMbs(10000);

        cntObject.setDefaultValue(cnt);

        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cntObject);

        StringEntity entity = new StringEntity(requestBody);

        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(inCSEAddress)
                .setPath("/Mobius" + "/" + aeName)
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
            System.out.println("send to oneM2M Error : " + e);
        } finally {
            response.close();
        }

    }
}
