package com.oauth.dto.mobius;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SubDTO {

    @JsonProperty("m2m:sub")
    private Sub defaultValue;

    @Getter
    @Setter
    public static class Sub {
        private String rn;
        private Enc enc;
        private List<String> nu;
        private int exc;

        @Getter
        @Setter
        public static class Enc {
            private List<String> net;
        }
    }

}
