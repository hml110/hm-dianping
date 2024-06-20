package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;


    @Resource
    private RedisIdWorker redisIdWorker;

    //定义大小为500的线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);


   @Test
    void testSaveShop(){
       shopService.saveShop2Redis(1L,10L);
       shopService.saveShop2Redis(2L,10L);
       shopService.saveShop2Redis(3L,10L);
       shopService.saveShop2Redis(4L,10L);
       shopService.saveShop2Redis(5L,10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
       //线程计数器
        CountDownLatch latch = new CountDownLatch(300);
        //定义多线程任务
       Runnable task = () ->{
           for (int i = 0; i < 100; i++) {
               long order = redisIdWorker.nexId("order");
               System.out.println("id = " + order);
           }
           //减少锁存器的计数，如果计数达到零，则释放所有等待线程。如果当前计数大于零，则递减。如果新计数为零，则出于线程调度目的，将重新启用所有等待线程。如果当前计数等于零，那么什么都不会发生。
           latch.countDown();
        };
        long begin = System.currentTimeMillis();
        //将任务提交到线程池
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //等待所有的计数器结束
        latch.await();
        long end = System.currentTimeMillis();

        System.out.println("耗时： " + (end - begin) );

    }
}
