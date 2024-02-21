package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GWResponseObject {

    private String message;

    public GWResponseObject() {
    }

    public GWResponseObject(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "GWResponseObject{" +
                "message='" + message + '\'' +
                '}';
    }
}
