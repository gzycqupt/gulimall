package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.ProductConstant;
import com.atguigu.common.to.SkuHasStockVo;
import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignService;
import com.atguigu.gulimall.product.feign.SearchFeignService;
import com.atguigu.gulimall.product.feign.WareFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SpuInfoDescService spuInfoDescService;

    @Autowired
    SpuImagesService imagesService;

    @Autowired
    AttrService attrService;

    @Autowired
    ProductAttrValueService attrValueService;

    @Autowired
    SkuInfoService skuInfoService;

    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    SearchFeignService searchFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {
        // 保存spu基本信息 pms_spu_info
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, infoEntity);
        infoEntity.setCreateTime(new Date());
        infoEntity.setUpdateTime(new Date());
        this.saveBaseSpuInfo(infoEntity);

        // 保存spu的描述图片 pms_spu_info_desc
        List<String> decript = vo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId(infoEntity.getId());
        descEntity.setDecript(String.join(",", decript));
        spuInfoDescService.saveSpuInfoDesc(descEntity);

        // 保存spu的图片集 pms_spu_images
        List<String> images = vo.getImages();
        imagesService.saveImages(infoEntity.getId(), images);

        // 保存spu的规格参数 pms_product_attr_value
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValueEntity> collect = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            valueEntity.setAttrId(attr.getAttrId());
            AttrEntity id = attrService.getById(attr.getAttrId());
            valueEntity.setAttrName(id.getAttrName());
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setQuickShow(attr.getShowDesc());
            valueEntity.setSpuId(infoEntity.getId());
            return valueEntity;
        }).collect(Collectors.toList());

        attrValueService.saveProductAttr(collect);

        //  保存spu的积分信息；gulimall_sms->sms_spu_bounds
        //5、保存spu的积分信息；gulimall_sms->sms_spu_bounds
        Bounds bounds = vo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds,spuBoundTo);
        spuBoundTo.setSpuId(infoEntity.getId());
        R r = couponFeignService.saveSpuBounds(spuBoundTo);
        if(r.getCode() != 0){
            log.error("远程保存spu积分信息失败");
        }

        List<Skus> skus = vo.getSkus();
        if (skus != null && skus.size() >0){
            skus.forEach(item->{
                String defaultImg = "";
                for (Images image :item.getImages()) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item, skuInfoEntity);
                skuInfoEntity.setBrandId(infoEntity.getBrandId());
                skuInfoEntity.setCatalogId(infoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSpuId(infoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultImg);

                skuInfoService.saveSkuInfo(skuInfoEntity);

                Long skuId = skuInfoEntity.getSkuId();


                List<SkuImagesEntity> imagesEntities = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity->{
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(imagesEntities);

                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity attrValueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(a, attrValueEntity);
                    attrValueEntity.setSkuId(skuId);

                    return attrValueEntity;
                }).collect(Collectors.toList());
                //5.3）、sku的销售属性信息：pms_sku_sale_attr_value
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                // //5.4）、sku的优惠、满减等信息；gulimall_sms->sms_sku_ladder\sms_sku_full_reduction\sms_member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item,skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                if(skuReductionTo.getFullCount() >0 || skuReductionTo.getFullPrice().compareTo(new BigDecimal("0")) == 1){
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if(r1.getCode() != 0){
                        log.error("远程保存sku优惠信息失败");
                    }
                }
            });
        }
        // 保存当前spu对应的所有sku信息
            ////5.1）、sku的基本信息；pms_sku_info
            //5.2）、sku的图片信息；pms_sku_image
            //5.3）、sku的销售属性信息：pms_sku_sale_attr_value
            // //5.4）、sku的优惠、满减等信息；gulimall_sms->sms_sku_ladder\sms_sku_full_reduction\sms_member_price
    }

    @Override
    public void saveBaseSpuInfo(SpuInfoEntity infoEntity) {
        this.baseMapper.insert(infoEntity);
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {

        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if(!org.springframework.util.StringUtils.isEmpty(key)){
            wrapper.and((w)->{
                w.eq("id",key).or().like("spu_name",key);
            });
        }
        // status=1 and (id=1 or spu_name like xxx)
        String status = (String) params.get("status");
        if(!org.springframework.util.StringUtils.isEmpty(status)){
            wrapper.eq("publish_status",status);
        }

        String brandId = (String) params.get("brandId");
        if(!org.springframework.util.StringUtils.isEmpty(brandId)&&!"0".equalsIgnoreCase(brandId)){
            wrapper.eq("brand_id",brandId);
        }

        String catelogId = (String) params.get("catelogId");
        if(!org.springframework.util.StringUtils.isEmpty(catelogId)&&!"0".equalsIgnoreCase(catelogId)){
            wrapper.eq("catalog_id",catelogId);
        }

        /**
         * status: 2
         * key:
         * brandId: 9
         * catelogId: 225
         */

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    /**
     * 商品上架
     * @param spuId
     */
    @Override
    public void up(Long spuId) {
        //List<SkuEsModel> uoProducts = new ArrayList<>();
        // 组装需要的数据
        //SkuEsModel esModel = new SkuEsModel();
        // 查出当前spuid对应的所有sku信息，品牌的名字
        List<SkuInfoEntity> skus = skuInfoService.getSkusBySpuId(spuId);

        List<Long> skuIdList = skus.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        //查询当前sku所有可以被用来检索的规格属性
        List<ProductAttrValueEntity> productAttrValueEntities = attrValueService.baseAttrlistforspu(spuId);
        List<Long> attrIds = productAttrValueEntities.stream().map(attr->{
            return attr.getAttrId();
        }).collect(Collectors.toList());

        List<Long> searchAttrIds = attrService.selectSearchAttrIds(attrIds);

        Set<Long> idSet = new HashSet<>(searchAttrIds);

        List<SkuEsModel.Attr> attrList = productAttrValueEntities.stream().filter(item -> {
            return idSet.contains(item.getAttrId());
        }).map(item -> {
            SkuEsModel.Attr attr1 = new SkuEsModel.Attr();
            BeanUtils.copyProperties(item, attr1);
            return attr1;
        }).collect(Collectors.toList());
        Map<Long, Boolean> stockMap = null;
        try {
            //发起远程调用，库存系统查询是否有库存
            R skusHasStock = wareFeignService.getSkusHasStock(skuIdList);
            TypeReference<List<SkuHasStockVo>> typeReference = new TypeReference<List<SkuHasStockVo>>(){};
            stockMap = skusHasStock.getData(typeReference).stream().collect(Collectors.toMap(SkuHasStockVo::getSkuId,item->item.getHasStock()));
        } catch (Exception e){
            log.error("库存服务查询异常：原因{}",e);
        }

        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> upProducts = skus.stream().map(sku -> {
            SkuEsModel esModel = new SkuEsModel();
            BeanUtils.copyProperties(sku, esModel);
            esModel.setSkuPrice(sku.getPrice());
            esModel.setSkuImg(sku.getSkuDefaultImg());
            //是否有库存
            if (finalStockMap == null) {
                esModel.setHasStock(true);
            } else {
                esModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }

            //热度评分
            esModel.setHotScore(0L);

            // 查询品牌和分类的名字信息
            BrandEntity brandEntity = brandService.getById(esModel.getBrandId());
            esModel.setBrandName(brandEntity.getName());
            esModel.setBrandImg(brandEntity.getLogo());

            CategoryEntity category = categoryService.getById(esModel.getCatalogId());
            esModel.setCatalogName(category.getName());

            //设置检索属性
            esModel.setAttrs(attrList);
            return esModel;
        }).collect(Collectors.toList());

        R r = searchFeignService.productStatusUp(upProducts);
        if (r.getCode() == 0) {
            //修改当前spu的状态
            baseMapper.updateSpuStatus(spuId, ProductConstant.ProductStatusEnum.SPU_UP.getCode());
        } else {
            //重复调用，接口幂等性？重试机制
            /**
             * Feign调用流程
             * 1、构造请求数据，将对象转为json
             *      RequestTemplate template = buildTemplateFromArgs.create(argv)
             * 2、发送请求进行执行(执行成功会解码响应数据)
             *      executeAndDecodes(template)
             * 3、执行请求会有重试机制
             *      while(true) {
             *          try{
             *              executeAndDecodes(template)
             *          }catch(){
             *              try{
             *                  retryer.continueOrPropagate(e);
             *              }catch(){
             *                  throw ex;
             *              }
             *              continue;
             *      }
             *
             *
             */

        }
    }

    @Override
    public SpuInfoEntity getSpuInfoBySkuId(Long skuId) {
        SkuInfoEntity byId = skuInfoService.getById(skuId);
        Long spuId = byId.getSpuId();
        SpuInfoEntity spuInfoEntity = getById(spuId);
        return spuInfoEntity;
    }

}