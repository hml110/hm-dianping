package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

/**
 * 操作ThreadLocal的工具类
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    /**
     * ThreadLocal存入user对象
     * @param user
     */
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    /**
     * ThreadLocal获取user对象
     * @return
     */
    public static UserDTO getUser(){
        return tl.get();
    }

    /**
     * ThreadLocal移除user对象
     * @return
     */
    public static void removeUser(){
        tl.remove();
    }
}

