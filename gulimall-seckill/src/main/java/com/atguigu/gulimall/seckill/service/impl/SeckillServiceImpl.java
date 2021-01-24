package com.atguigu.gulimall.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.interceptor.LoginInterceptor;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSessionsWithSkus;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private CouponFeignService couponFeignService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String SESSIONS_CACHE_PREFIX = "seckill:sessions:";

    private static final String SKUKILL_CACHE_PREFIX = "seckill:skus";

    private static final String SKU_STOCK_SEMAPHORE = "seckill:stock:";

    @Override
    public void uploadSeckillSkuLatest3Days() {
        //扫描需要参与
        R r = couponFeignService.getLatest3DaySession();
        if (r.getCode() == 0) {

            List<SeckillSessionsWithSkus> sesssionData = r.getData(new TypeReference<List<SeckillSessionsWithSkus>>() {
            });
            //缓存到redis
            //缓存活动信息
            saveSessionInfos(sesssionData);
            //缓存活动的关联商品信息
            saveSessionSkuInfos(sesssionData);
        }
    }

    private void saveSessionInfos(List<SeckillSessionsWithSkus> sessions) {
        sessions.stream().forEach(session -> {
            Long startTime = session.getStartTime().getTime();
            Long endTime = session.getEndTime().getTime();
            String key = SESSIONS_CACHE_PREFIX + startTime + "_" + endTime;

            Boolean hasKey = stringRedisTemplate.hasKey(key);
            if (!hasKey) {
                List<String> collect = session.getRelationSkus().stream().map(item -> item.getPromotionSessionId() + "_" + item.getSkuId().toString()).collect(Collectors.toList());
                //缓存活动信息
                stringRedisTemplate.opsForList().leftPushAll(key, collect);
            }
        });
    }

    private void saveSessionSkuInfos(List<SeckillSessionsWithSkus> sessions) {
        sessions.stream().forEach(session -> {
            //准备hash操作
            BoundHashOperations<String, Object, Object> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);

            session.getRelationSkus().stream().forEach(seckillSkuVo -> {

                if (!ops.hasKey(seckillSkuVo.getPromotionSessionId().toString() + "_" + seckillSkuVo.getSkuId().toString())) {
                    //缓存商品
                    SeckillSkuRedisTo redisTo = new SeckillSkuRedisTo();
                    //sku的基本数据
                    R skuInfo = productFeignService.info(seckillSkuVo.getSkuId());
                    if (skuInfo.getCode() == 0) {
                        SkuInfoVo info = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                        });
                        redisTo.setSkuInfo(info);
                    }
                    //sku的秒杀信息
                    BeanUtils.copyProperties(seckillSkuVo, redisTo);

                    //设置上当前商品的秒杀时间信息
                    redisTo.setStartTime(session.getStartTime().getTime());
                    redisTo.setEndTime(session.getEndTime().getTime());

                    //随机码
                    String token = UUID.randomUUID().toString().replace("-", "");
                    redisTo.setRandomCode(token);

                    String s = JSON.toJSONString(redisTo);
                    ops.put(seckillSkuVo.getPromotionSessionId() + "_" + seckillSkuVo.getSkuId().toString(), s);

                    //引入分布式的信号量
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                    //商品可以秒杀的数量作为信号量
                    semaphore.trySetPermits(seckillSkuVo.getSeckillCount());
                }
            });

        });
    }

    @Override
    public List<SeckillSkuRedisTo> getCurrentSeckillSkus() {
        //确定当前时间属于哪个秒杀场次
        long currentTime = System.currentTimeMillis();
        Set<String> keys = stringRedisTemplate.keys(SESSIONS_CACHE_PREFIX + "*");

        for (String key : keys) {
            String replace = key.replace(SESSIONS_CACHE_PREFIX, "");
            String[] split = replace.split("_");
            long startTime = Long.parseLong(split[0]);
            long endTime = Long.parseLong(split[1]);
            //当前秒杀活动处于有效期内
            if (currentTime >= startTime && currentTime <= endTime) {
                //获取这个秒杀场次需要的所有商品信息
                List<String> range = stringRedisTemplate.opsForList().range(key, -100, 100);
                BoundHashOperations<String, String, String> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                List<String> list = ops.multiGet(range);
                if (list != null) {
                    List<SeckillSkuRedisTo> collect = list.stream().map(item -> {
                        SeckillSkuRedisTo redisTo = new SeckillSkuRedisTo();
                        SeckillSkuRedisTo redis = JSON.parseObject((String) item, SeckillSkuRedisTo.class);
                        //redis.setRandomCode(null);//当前秒杀开始就需要随机码
                        return redis;
                    }).collect(Collectors.toList());
                    return collect;
                }
                break;
            }
        }
        return null;
    }

    @Override
    public SeckillSkuRedisTo getSeckillSkuInfo(Long skuId) {
        //找到所有需要参与秒杀的商品的key
        BoundHashOperations<String, String, String> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        Set<String> keys = ops.keys();
        if (keys != null && keys.size() > 0) {
            for (String key : keys) {
                if (Pattern.matches("\\d_" + skuId, key)) {
                    String v = ops.get(key);
                    SeckillSkuRedisTo redisTo = JSON.parseObject(v, SeckillSkuRedisTo.class);
                    //当前商品参与秒杀活动
                    if (redisTo != null) {
                        long current = System.currentTimeMillis();
                        //当前活动在有效期，暴露商品随机码返回
                        if (redisTo.getStartTime() < current && redisTo.getEndTime() > current) {
                            return redisTo;
                        }
                        redisTo.setRandomCode(null);
                        return redisTo;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String kill(String killId, String key, Integer num) {
        MemberResponseVo respVo = LoginInterceptor.loginUser.get();
        //获取当前秒杀商品的详细信息
        BoundHashOperations<String, String, String> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        String json = ops.get(killId);

        String orderSn = null;
        if (!StringUtils.isEmpty(json)) {
            SeckillSkuRedisTo redis = JSON.parseObject(json, SeckillSkuRedisTo.class);
            //1. 验证时间合法性
            long current = System.currentTimeMillis();
            long ttl = redis.getEndTime() - current;
            if (current >= redis.getStartTime() && current <= redis.getEndTime()) {
                //2. 验证商品和商品随机码是否对应
                String randomCode = redis.getRandomCode();
                String skuId = redis.getPromotionSessionId() + "_" + redis.getSkuId();
                if (killId.equals(skuId) && randomCode.equals(key)) {
                    //验证购物数量是否合理
                    if (num <= redis.getSeckillLimit().intValue()) {
                        //验证这个人是否已经购买过。幂等性；如果只要秒杀成功，就去占位。通过在redis中使用 用户id-skuId 来占位看是否买过
                        String redisKey = respVo.getId()+"_"+skuId;
                        Boolean occupy = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, num.toString(), ttl, TimeUnit.MILLISECONDS);
                        if (occupy){
                            //占位成功，说明该用户未秒杀过该商品，则继续
                            //尝试获取库存信号量
                            RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + redis.getRandomCode());
                            try {
                                boolean acquire = semaphore.tryAcquire(num, 100, TimeUnit.MILLISECONDS);
                                if (acquire) {
                                    // 创建订单号
                                    orderSn = IdWorker.getTimeId();
                                    // 创建秒杀订单to
                                    SeckillOrderTo orderTo = new SeckillOrderTo();
                                    orderTo.setMemberId(respVo.getId());
                                    orderTo.setNum(num);
                                    orderTo.setOrderSn(orderSn);
                                    orderTo.setPromotionSessionId(redis.getPromotionSessionId());
                                    orderTo.setSeckillPrice(redis.getSeckillPrice());
                                    orderTo.setSkuId(redis.getSkuId());
                                    //5.3 发送创建订单的消息
                                    rabbitTemplate.convertAndSend("order-event-exchange", "order.seckill.order", orderTo);
                                }
                            } catch (InterruptedException e) {
                                return null;
                            }
                        }else {
                            return null;
                        }
                    }
                }
            }
            return orderSn;
        }
        return null;
    }

}
