package com.atguigu.gulimall.product.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@FeignClient("gulimall-seckill")
public interface SeckillFeignService {

    @ResponseBody
    @GetMapping(value = "/sku/seckill/{skuId}")
    public R getSeckillSkuInfo(@PathVariable("skuId") Long skuId);

}
