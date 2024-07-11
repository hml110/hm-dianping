package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.USER_FOLLOWS_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;


    /**
     * 关注用户
     *
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 判断到底是关注还是取关
        Long id = UserHolder.getUser().getId();
        if (isFollow) {
            // 2. 关注，新增
            Follow follow = new Follow();
            follow.setUserId(id).setFollowUserId(followUserId);
            boolean save = this.save(follow);
            if (save) {
                // 3. 把关注用户的id,放入redis的set集合 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(USER_FOLLOWS_KEY + id, String.valueOf(followUserId));
            }
        } else {
            //3. 取关，删除
            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(Follow::getUserId, id).eq(Follow::getFollowUserId, followUserId);
            boolean remove = this.remove(queryWrapper);
            if (remove) {
                // 4. 把关注用户的id,移除集合
                stringRedisTemplate.opsForSet().remove(USER_FOLLOWS_KEY + id, String.valueOf(followUserId));
            }
        }
        return Result.ok(isFollow ? "关注成功！" : "取关成功！");
    }


    /**
     * 是否关注用户
     *
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 1. 判断到底是关注还是取关
        Long id = UserHolder.getUser().getId();
        QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Follow::getUserId, id).eq(Follow::getFollowUserId, followUserId);
        int count = this.count(queryWrapper);
        return Result.ok(count > 0);
    }

    /**
     * 共同关注用户
     * 求交集：当前用户和目标用户的交集
     *
     * @param followUserId
     * @return
     */
    @Override
    public Result followCommons(Long followUserId) {
        // 1. 判断到底是关注还是取关
        Long id = UserHolder.getUser().getId();
        // 2. 准备集合key
        String keyUser = USER_FOLLOWS_KEY + id;
        String keyFollowUser = USER_FOLLOWS_KEY + followUserId;
        // 3. 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(keyUser, keyFollowUser);
        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 4. 解析id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
