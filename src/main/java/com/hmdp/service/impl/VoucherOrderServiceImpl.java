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
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 秒杀优惠券的方法
     *
     * @param voucherId
     * @return
     */
    @Override

    public Result seckillVoucher(Long voucherId) {
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
        /**
         * 先获取锁，再进入带有事务的函数，解决了可能会导致当前方法事务还没有提交，但是锁已经释放也会导致问题
         */
        synchronized (userId.toString().intern()){
            //但是以上做法依然有问题，因为你调用的方法，其实是this.的方式调用的，事务想要生效，还得利用代理来生效，所以这个地方，我们需要获得原始的事务对象， 来操作事务
            // return this.createVoucherOrder(voucherId);

            //获取当前对象的代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional  //这里涉及到了两张表的操作
    public  Result createVoucherOrder(Long voucherId) {
        //5.一人一单
        Long userId = UserHolder.getUser().getId();
        /**
         * 这里如果是两个用户，是不需要加锁的
         * 如果是一个用户，那么只需要通过关键字进行加锁
         * 锁对象：userId.toString()
         * intern() :返回字符串对象的规范表示形式。
         * 这样的话，只要值是一样的，锁是一样的
         */
            //5.1. 查询订单
            //查询数量
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //5.2. 判断是否存在
            if (count > 0) {
                //用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }
            //6.扣减库存
            boolean sucess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")  // set stock = stock -1
                    //cas法解决库存问题：修改时对比库存
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock =?
                    .update();
            if (!sucess) {
                //扣减失败
                return Result.fail("库存不足！");
            }
            //7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //7.1 订单id
            long orderId = redisIdWorker.nexId("order");
            voucherOrder.setId(orderId);
            //7.2 用户id
            voucherOrder.setUserId(userId);
            //7.3 优惠劵id
            voucherOrder.setVoucherId(voucherId);
            //保存记录
            save(voucherOrder);

            //8.返回订单id
            return Result.ok(orderId);
        }

}
