package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
public class IndexController {

    @Autowired
    CategoryService categoryService;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @GetMapping({"/", "/index.html"})
    public String indexPage(Model model) {
        //查出一级分类
        List<CategoryEntity> categoryEntities = categoryService.getLevel1Categorys();

        model.addAttribute("categorys", categoryEntities);
        return "index";
    }

    @ResponseBody
    @GetMapping("/index/catalog.json")
    public Map<String, List<Catelog2Vo>> getCatalogJson() {

        Map<String, List<Catelog2Vo>> catalogJson = categoryService.getCatalogJson();
        return catalogJson;
    }

    @ResponseBody
    @GetMapping("/hello")
    public String hello() {
        RLock lock = redissonClient.getLock("my-lock");
        lock.lock();//阻塞式等待
        // 锁的自动续期，如果业务超长，运行时间自动给锁续上新的30s，不用担心业务时间长，锁自动过期
        //被删掉，加锁的业务只要运行完成，就不会给当前锁续期，即使不手动解锁，锁默认在30s以后会自动删除
        //lock.lock(10, TimeUnit.SECONDS);//10秒自动解锁，自动解锁时间一定要大于业务的执行时间
        // 在锁时间到了以后，不会自动续期
        // 如果我们传递了锁的超时时间，就发送给redis执行脚本，进行占锁，默认超时就是我们指定的时间
        // 如果我们未指定锁的超时时间，就使用30*1000，默认时间
        // 只要占锁成功，就会启动一个定时任务【重新给锁设置过期时间，新的过期时间就是看门狗的默认时间】
        // internalLockLeaseTime/3,10s
        try {
            System.out.println("加锁成功，执行业务" + Thread.currentThread().getId());
            Thread.sleep(10000);
        } catch (Exception e) {

        } finally {
            System.out.println("释放锁" + Thread.currentThread().getId());
            lock.unlock();
        }
        return "hello";
    }

    //保证一定能读到最新数据，修改期间，写锁时一个排他锁；读锁时一个共享锁
    //写锁没释放读就必须等待
    // 读+读：相当于无锁，并发读，只会在redis中记录好，所有当前的读锁，他们都会同时加锁成功
    // 写+读：等待写锁释放
    // 写+写：阻塞方式
    // 读+写：有读锁，写也需要等待
    // 只要有写的存在，都必须等待
    @GetMapping("/write")
    @ResponseBody
    public String writeValue() {
        RReadWriteLock lock = redissonClient.getReadWriteLock("re-lock");
        RLock rLock = lock.writeLock();
        String s = "";
        try {
            rLock.lock();
            s = UUID.randomUUID().toString();
            Thread.sleep(30000);
            stringRedisTemplate.opsForValue().set("writeValue", s);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
        }
        return s;
    }

    @GetMapping("/read")
    @ResponseBody
    public String readValue() {
        RReadWriteLock lock = redissonClient.getReadWriteLock("re-lock");
        RLock rLock = lock.readLock();
        rLock.lock();
        String s = "";
        try {
            s = stringRedisTemplate.opsForValue().get("writeValue");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
        }
        return s;
    }

    @GetMapping("/lockDoor")
    @ResponseBody
    public String lockDoor() throws InterruptedException {
        RCountDownLatch door = redissonClient.getCountDownLatch("door");
        door.trySetCount(5);
        door.await();
        return "放假了。。。";
    }

    @GetMapping("/gogogo/{id}")
    @ResponseBody
    public String gogogo(@PathVariable("id") Long id){
        RCountDownLatch door = redissonClient.getCountDownLatch("door");
        door.countDown();
        return id+"班的人都走了";
    }

    @GetMapping("/park")
    @ResponseBody
    public String park() {
        RSemaphore park = redissonClient.getSemaphore("park");
        try {
            park.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "ok";
    }

    @GetMapping("/go")
    @ResponseBody
    public String go() {
        RSemaphore park = redissonClient.getSemaphore("park");
        park.release();
        return "ok";
    }

}
