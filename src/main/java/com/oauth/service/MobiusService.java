package com.oauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.mobius.AeDTO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

@Service
public class MobiusService {

    private static int requestIndex = 0;

    ObjectMapper objectMapper = new ObjectMapper();

    public MobiusResponse createAe(String srNo, String modelCode) throws Exception {

        AeDTO aeDTOObject = new AeDTO();
        AeDTO.Ae ae = new AeDTO.Ae();

        ae.setRn("0");

        return null;
    }

}
