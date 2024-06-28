package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 加载lua脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    /**
     * 创建阻塞队列
     */
    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<VoucherOrder>(1024*1024);

    /**
     * 创建线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();


    /**
     * 当前类初始化之后进行执行
     */
    @PostConstruct
    private void init(){
        // 提交任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    /**
     * 线程任务
     */
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true){
                // 1.获取队列中的订单信息
                try {
                    VoucherOrder voucherOrder = orderTask.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                   log.error("处理订单异常",e);
                }
            }
        }
    }


    /**
     * 存放IVoucherOrderService代理对象
     * 注意在子线程中无法获取到代理对象
     */
    private IVoucherOrderService proxy;

    /**
     * 处理订单创建
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1、 获取用户
        Long userId = voucherOrder.getUserId();
        //2、创建锁对象
        RLock lock = redissonClient.getLock("lock:order" + userId);
        //3、获取锁
        boolean tryLock = lock.tryLock();
        // 判断是否获取锁成功
        if (!tryLock) {
            // 获取锁失败，返回错误信息
           log.error("不允许重复下单！");
           return;
        }
        try {
             proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }

    }

    /**
     * 秒杀优惠券的方法
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nexId("order");
        //1.执行lua脚本 -- 完成秒杀资格判断
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2. 判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //2.1 不为0 没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2 为0 有购买资格，把下单的信息保存到阻塞队列
        //TODO：保存阻塞队列
        //2.3.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.4 订单id
        voucherOrder.setId(orderId);
        //2.5 用户id
        voucherOrder.setUserId(userId);
        //2.6 优惠劵id
        voucherOrder.setVoucherId(voucherId);
        // 2.7 创建阻塞队列
        // 2.7.1 添加到阻塞队列
        orderTask.add(voucherOrder);
        // 3、获取代理对象
        //获取当前对象的代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3. 返回订单id
        return Result.ok(orderId);
    }


    /**
     * 加锁的创建订单方法
     *
     * @param voucherOrder
     * @return
     */

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        5.一人一单
        Long userId = voucherOrder.getUserId();
        /**
         * 这里如果是两个用户，是不需要加锁的
         * 如果是一个用户，那么只需要通过关键字进行加锁
         * 锁对象：userId.toString()
         * intern() :返回字符串对象的规范表示形式。
         * 这样的话，只要值是一样的，锁是一样的
         */
        //5.1. 查询订单
        //查询数量
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.2. 判断是否存在
        if (count > 0) {
            //用户已经购买过了
            log.error("用户已经购买过一次！！");
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")  // set stock = stock -1
                //cas法解决库存问题：修改时对比库存
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock =?
                .update();
        if (!success) {
            //扣减失败
            log.error("库存不足！");
        }
        // 7 创建订单
        save(voucherOrder);
    }

    /**
     * 秒杀优惠券的方法--- 通过数据库
     *
     * @param voucherId
     * @return
     */

    /**
    public Result seckillVoucherByDB(Long voucherId) {
        //1. 查询优惠劵
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始！");
        }

        //3. 判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀结束！");
        }

        //4.判断库房是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        //加锁创建订单
        // 创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order" + userId);
        // 获取锁
        boolean tryLock = lock.tryLock();
        // 判断是否获取锁成功
        if (!tryLock) {
            // 获取锁失败，返回错误信息
            return Result.fail("不允许重复下单！");
        }
        try {
            //获取当前对象的代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }
*/
    /**
     * 创建秒杀卷订单
     *
     * @param voucherId
     * @return
     */
//    @Transactional  //这里涉及到了两张表的操作
//    public Result createVoucherOrderByDB(Long voucherId) {
//        //5.一人一单
//        Long userId = UserHolder.getUser().getId();
//        /**
//         * 这里如果是两个用户，是不需要加锁的
//         * 如果是一个用户，那么只需要通过关键字进行加锁
//         * 锁对象：userId.toString()
//         * intern() :返回字符串对象的规范表示形式。
//         * 这样的话，只要值是一样的，锁是一样的
//         */
//        //5.1. 查询订单
//        //查询数量
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        //5.2. 判断是否存在
//        if (count > 0) {
//            //用户已经购买过了
//            return Result.fail("用户已经购买过一次！");
//        }
//        //6.扣减库存
//        boolean sucess = seckillVoucherService.update()
//                .setSql("stock = stock - 1")  // set stock = stock -1
//                //cas法解决库存问题：修改时对比库存
//                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock =?
//                .update();
//        if (!sucess) {
//            //扣减失败
//            return Result.fail("库存不足！");
//        }
//        //7.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //7.1 订单id
//        long orderId = redisIdWorker.nexId("order");
//        voucherOrder.setId(orderId);
//        //7.2 用户id
//        voucherOrder.setUserId(userId);
//        //7.3 优惠劵id
//        voucherOrder.setVoucherId(voucherId);
//        //8.返回订单id
//        return Result.ok(orderId);
//    }

}
