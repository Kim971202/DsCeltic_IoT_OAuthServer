package com.oauth.dto.mobius;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CinDTO {

    @JsonProperty("m2m:cin")
    private Cin defaultValue;

    @Getter
    @Setter
    public static class Cin {
        private String con;
    }

}
