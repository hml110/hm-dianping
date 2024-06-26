package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sentCode(String phone, HttpSession session) {
        //1、校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //2、如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到Redis  并设置2分钟的有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
        //返回ok
        return Result.ok("发送短信验证码成功，验证码："+code);
    }

    /**
     * 短信验证码登录或注册
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            //2、如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        //前端发送的code应该不为空且与session中的一致
        if (code == null || !cacheCode.equals(code)){
            //3.不一致，报错
            return Result.fail("验证码错误");
        }
        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //5.判断用户是否存在
        if (user == null){
            //6.不存在，创建新用户即保存
            user = creatUserWithPhone(phone);
        }
        //7.存在，保存用户信息到session
        //7.1.随机生成token，作为登录令牌 UUID.randomUUID().toString(true); 生成不带'-'的uuid
         String token = UUID.randomUUID().toString(true);
        //7.2.将user转换为hashMap存储  这里的map可以存储多个字段，可以一次性存入redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                .setIgnoreNullValue(true) //忽略空值
                .setFieldValueEditor((fileName,fieldValue)->fieldValue.toString()) //置字段属性值编辑器，用于自定义属性值转换规则，例如null转""等
        );
        //7.3.存储 设置有效期
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key,userMap);
        //7.4.设置有效期 30min
        stringRedisTemplate.expire(key,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //8.返回token
        return Result.ok(token);
    }

    /**
     * 根据手机号创建用户
     * @param phone
     * @return
     */
    private User creatUserWithPhone(String phone) {
        //创建用户
        User user  =new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}
