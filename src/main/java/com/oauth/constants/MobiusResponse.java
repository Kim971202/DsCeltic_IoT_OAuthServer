package com.oauth.constants;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class MobiusResponse {

    private String responseCode;
    private String responseContent;
    private String controlAuthKey;
    private String commandCode;

}
