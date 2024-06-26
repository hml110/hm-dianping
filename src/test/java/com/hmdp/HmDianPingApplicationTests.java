package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;


    @Resource
    private RedisIdWorker redisIdWorker;


    @Resource
    private CacheClient cacheClient;


    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedissonClient redissonClient2;

    @Resource
    private RedissonClient redissonClient3;

    //定义大小为500的线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);


    @Test
    void testSaveShop() {

        Shop shop = shopService.getById("1L");
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);

//       shopService.saveShop2Redis(1L,10L);
//       shopService.saveShop2Redis(2L,10L);
//       shopService.saveShop2Redis(3L,10L);
//       shopService.saveShop2Redis(4L,10L);
//       shopService.saveShop2Redis(5L,10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        //线程计数器
        CountDownLatch latch = new CountDownLatch(300);
        //定义多线程任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nexId("order");
                System.out.println("id = " + order);
            }
            //减少锁存器的计数，如果计数达到零，则释放所有等待线程。如果当前计数大于零，则递减。如果新计数为零，则出于线程调度目的，将重新启用所有等待线程。如果当前计数等于零，那么什么都不会发生。
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        //将任务提交到线程池  300个线程
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //等待所有的计数器结束
        latch.await();
        long end = System.currentTimeMillis();

        System.out.println("耗时： " + (end - begin));

    }


    /**
     * 联锁 multiLock
     */
    RLock lock = null;


    /**
     * 三个独立节点对应的三个独立锁
     */
    RLock lock1 = null;
    RLock lock2 = null;
    RLock lock3 = null;


    /**
     * 设置锁
     */
    @Test
    void setUp(){
        lock1 = redissonClient.getLock("order");
        lock2 = redissonClient2.getLock("order");
        lock3 = redissonClient3.getLock("order");


        // 创建联锁 multiLock
        lock = redissonClient.getMultiLock(lock1, lock2, lock3);


    }

    /**
     * 尝试获取锁
     */
    @Test
    void method1() throws InterruptedException {
        setUp();
        // 尝试获取锁
        boolean isLock = lock.tryLock(1L,TimeUnit.SECONDS);
        if (!isLock) {
            log.error("获取锁失败......method1()");
            return;
        }
        try {
            log.info("获取锁成功......method1()");
            method2();
            log.info("开始执行业务......method1()");

        }finally {
            log.warn("准备释放锁......1");
            lock.unlock();
        }
    }
    void method2() {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败......method1()");
            return;
        }
        try {
            log.info("获取锁成功......method1()");

            log.info("开始执行业务......method1()");

        }finally {
            log.warn("准备释放锁......1");
            lock.unlock();
        }
    }
}
