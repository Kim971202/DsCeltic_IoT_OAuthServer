package com.oauth.constants;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MobiusResponse {

    private int responseCode;
    private String responseContent;
    private String controlAuthKey;
    private String commandCode;

}
