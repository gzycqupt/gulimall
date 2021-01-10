package com.atguigu.gulimall.search.vo;

import lombok.Data;

import java.util.List;

/**
 *  封装页面所有可能传递过来的查询条件
 *  catalog3Id=225&keyword=小米&sort=saleCount_asc&hasStock=0/1
 */
@Data
public class SearchParam {

    // 页面传递过来的全文匹配关键字
    private String keyword;
    //三级分类id
    private Long catalog3Id;
    /**
     * sort=saleCount_asc/desc
     * sort=skuPrice_asc/desc
     * sort=hotScore_asc/desc
     */
    private String sort;
    /**
     * 好多的过滤条件
     * hasStock(是否有货)、skuPrice区间、brandId、catalog3Id、attrs
     * hasStock=0/1
     * skuPrice=1_500/_500/500_
     * 是否只显示有货
     */
    private Integer hasStock;
    //价格区间查询
    private String skuPrice;
    //品牌查询，多选
    private List<Long> brandId;
    //按照属性筛选
    private List<String> attrs;

    private Integer pageNum =1;

    private String _queryString;

}
