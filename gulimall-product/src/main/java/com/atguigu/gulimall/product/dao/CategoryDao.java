package com.atguigu.gulimall.product.dao;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品三级分类
 * 
 * @author gzy
 * @email gzy@gmail.com
 * @date 2020-05-12 22:39:26
 */
@Mapper
public interface CategoryDao extends BaseMapper<CategoryEntity> {
	
}
