package com.oauth.controller;

import com.oauth.dto.member.MemberDTO;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.utils.Common;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.*;

@RequestMapping("/users/v1")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class LoginController {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private PasswordEncoder encoder;

    /**
     * 인증 서버 활성화 체크
     */
    @ResponseBody
    @RequestMapping(value="/auth/healthCheck", method={RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public String check(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("Health Check Called");
        return "{\"resultCode\":\"OK\"}";
    }

    /** 로그인 */
    @PostMapping(value = "/login")
    @ResponseBody
    public ResponseEntity<?> doLogin(HttpSession session, HttpServletRequest request, @ModelAttribute MemberDTO params, HttpServletResponse response)
        throws Exception {

        String stringObject = null;

        try {
            String userId = params.getUserId();
            String userPw = params.getUserPassword();


            MemberDTO member = memberMapper.getUserByUserId(userId);
            List<MemberDTO> deviceInfoList = memberMapper.getDeviceInfoByUserID(userId);

            ApiResponse.Data ApiResponseData = new ApiResponse.Data();

            // Device Set 생성
            Set<String> userDeviceIds = new HashSet<>();
            List<ApiResponse.Data.Device> data = new ArrayList<>();

            String userNickname = member.getUserNickname();

            List<String> deviceId = Common.extractUserId(deviceInfoList.toString(), "deviceId");
            List<String> controlAuthKey = Common.extractUserId(deviceInfoList.toString(), "controlAuthKey");
            List<String> deviceNickname = Common.extractUserId(deviceInfoList.toString(), "deviceNickname");
            List<String> regSort = Common.extractUserId(deviceInfoList.toString(), "regSort");

            // Mapper실행 후 사용자가 가지고 있는 Device 개수
            int numDevices = deviceInfoList.size();

           // Device 추가
            for (int i = 0; i < numDevices; i++) {
                ApiResponse.Data.Device device = Common.createDevice(
                        deviceId.get(i),
                        controlAuthKey.get(i),
                        deviceNickname.get(i),
                        regSort.get(i),
                        userDeviceIds);
                data.add(device);
            }



            ApiResponse.Data result = new ApiResponse.Data();
            stringObject = "Y";
            result.setAccessToken("accessToken");
            result.setUserNickname(userNickname);
            result.setResult("Y".equalsIgnoreCase(stringObject) ? ApiResponse.ResponseType.HTTP_200 : ApiResponse.ResponseType.CUSTOM_1003);
            result.setDevice(data);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
        }

      return null;
    }

    /** 회원가입 */
    @PostMapping(value = "/regist")
    @ResponseBody
    public ResponseEntity<?> doRegist(HttpSession session, HttpServletRequest request, @ModelAttribute MemberDTO params, HttpServletResponse response)
        throws Exception {

        String stringObject = "N";
        ApiResponse.Data data = new ApiResponse.Data();

        try{
            String userHp = params.getHp();
            String userNickname = params.getUserNickname();
            String userId = params.getUserId();
            String userPassword = params.getUserPassword();

            System.out.println("hp: " + userHp);
            System.out.println("userNickname: " + userNickname);
            System.out.println("userId: " + userId);
            System.out.println("userPassword: " + userPassword);

            userPassword = encoder.encode(userPassword);
            System.out.println("userPassword: " + userPassword);
            params.setUserPassword(userPassword);
            System.out.println("userPassword: " + userPassword);

            int result = memberMapper.insertMember(params);
            System.out.println("result: " + result);

            if(result > 0) stringObject = "Y";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002);

            return new ResponseEntity<>(data, HttpStatus.OK);

        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

}
