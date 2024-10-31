package com.oauth.utils;

public class Validator {
    public static boolean isNullOrEmpty(Object data) {
        if(data == null)
            return true;

        if(data == "" || data.toString().trim() == "")
            return true;

        return data.toString().isEmpty();
    }

}
