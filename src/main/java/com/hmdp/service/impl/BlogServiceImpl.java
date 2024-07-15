package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;


    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 保存笔记
     *
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1、获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2、保存探店博文
        boolean isSuccess = this.save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败！");
        }
        // 3、找到作者的所有粉丝
        QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Follow::getFollowUserId, user.getId());
        List<Follow> follows = followService.list(queryWrapper);
        for (Follow follow : follows) {
            // 4.1 获取粉丝id
            Long userId = follow.getUserId();
            // 4.2 将博文发送给粉丝'收件箱（sortedSet）'
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(blog.getId()), System.currentTimeMillis());
        }
        // 3、返回id
        return Result.ok(blog.getId());
    }

    /**
     * 根据id获取Blog
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        // 1. 查blog
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);

        // 3. 查询blog是否被点赞

        isBlogLiked(blog);

        return Result.ok(blog);
    }

    /**
     * 判断blog是否被点赞
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            Long userId = user.getId();
            // 2. 判断当前登录用户，是否已经点赞
            Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
            blog.setIsLike(BooleanUtil.isTrue(score != null));
        }
    }

    /**
     * 获取热门blog
     *
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询Blog的用户
     *
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 点赞
     * 同一个用户只能点赞一次，再次点击则取消点赞
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断当前登录用户，是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        if (score == null) {
            // 3. 如果未点赞，可以点赞
            // 3.1 数据库点赞+1
            boolean isSuccess = this.update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2 保存用户到redis的set集合
            if (isSuccess) {
                // zadd key value score
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4、如果已经点赞，取消点赞
            // 4.1 数据库点赞数减一
            boolean isSuccess = this.update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 把用户移除redis的set集合
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 获取点赞列表
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询top5的点赞用户 zrange key 0 4   根据score查询前5名
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析其中的用户id查询用户
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3. 根据用户id查询用户  ORDER BY FIELD 指定查询顺序
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4. 返回
        return Result.ok(userDTOS);
    }


    /**
     * 获取关注用户的消息
     *
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询收件箱  ZREVRANGEBYSCORE key MAX MIN LIMIT offset count
        String key = FEED_KEY + userId;
        /**
         * interface TypedTuple【元组】<V> extends Comparable<TypedTuple<V>> {
         *
         *                @Nullable
         *        V getValue();        ----   blogId
         *
         *        @Nullable
         *        Double getScore();   ----  分数
         *
         *        }
         */
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //4. 解析数据 blogId,score(时间戳),offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 最小的时间
        int os = 1; // 下次使用的偏移量
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) { // 5 4 4 2 2
            //4.1 获取blog的id
            String idStr = typedTuple.getValue();
            ids.add(Long.valueOf(idStr));
            // 4.2获取分数   -- 最后一个元素的时间
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                // 计数
                os++;
            } else {
                // 出现新的最小时间
                minTime = time;
                os = 1;
            }
        }
        //4. 根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = this.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();

        blogs.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        // 5. 封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
