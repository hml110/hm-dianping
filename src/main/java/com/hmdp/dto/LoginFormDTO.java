package com.hmdp.dto;

import lombok.Data;

/**
 *  登录传送参数的封装对象
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
