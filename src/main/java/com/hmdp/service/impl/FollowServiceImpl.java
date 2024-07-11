package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
            this.save(follow);
        } else {
            //3. 取关，删除
            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(Follow::getUserId, id).eq(Follow::getFollowUserId, followUserId);
            this.remove(queryWrapper);
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
}
