package com.oauth.dto.mobius;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CntDTO {

    @JsonProperty("m2m:cnt")
    private Cnt defaultValue;

    @Getter
    @Setter
    public static class Cnt{
        private String rn;
        private int mbs;
    }

}
