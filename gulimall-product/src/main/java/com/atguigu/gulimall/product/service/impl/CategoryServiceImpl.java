package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    private Map<String, Object> cache = new HashMap<>();

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {
        List<CategoryEntity> categoryEntities = baseMapper.selectList(null);

        List<CategoryEntity> level1Menus = categoryEntities.stream().filter(categoryEntity -> categoryEntity.getParentCid() == 0)
                .map((menu) -> {
                    menu.setChildren(getChildrens(menu, categoryEntities));
                    return menu;
                }).sorted((menu1, menu2) -> {
                    return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
                }).collect(Collectors.toList());

        return level1Menus;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO 1.检查当前删除的菜单，是否被别的地方引用
        baseMapper.deleteBatchIds(asList);
    }

    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();
//        CategoryEntity byId = this.getById(catelogId);
        List<Long> parentPath = findParentPath(catelogId, paths);
        Collections.reverse(parentPath);
        return (Long[]) parentPath.toArray(new Long[parentPath.size()]);
    }

    /**
     * 级联更新所有关联的数据
     * 1.同时进行多种缓存操作  @Caching
     * 2.指定删除某个分区下的所有数据 @CacheEvict(value="category",allEntries=true)
     * 3.存储同一类型的数据，都可以指定成同一分区
     */
    @Caching(evict = {
            @CacheEvict(value = "category", key = "'getLevel1Categorys'"),
            @CacheEvict(value = "category", key = "'getCatalogJson'")
    })
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(), category.getName());
    }

    /**
     * @Cacheable //代表当前方法的结果需要缓存，如果缓存中有，方法不用调用；如果缓存中没有，会调用方法，
     *     // 最后将方法的结果放入缓存
     * 每一个需要缓存的数据我们都来指定要放到哪个名字的缓存。【缓存的分区（按照业务类型分）】
     * @Cacheable({"category"})
     *     代表当前方法的结果需要缓存，如果缓存中有，方法不用调用；
     *     如果缓存中没有，会调用方法，最后将方法的结果放入缓存
     * 默认行为：1.如果缓存中有，方法不用调用
     *           2.key默认自动生成：缓存的名字::SimpleKey[](自主生成的key值)
     *           3.缓存的value的值，默认使用jdk序列化机制，将序列化后的数据存到redis
     *           4.默认ttl时间：-1
     * 自定义：1.指定生成的缓存使用的key  key属性指定，接受一个spel
     *         2.指定缓存的数据的存活时间  配置文件中修改ttl
     *         3.将数据保存为json格式
     *spring-cache的不足
     *  1.读模式
     *      缓存穿透：查询一个null数据。解决：缓存空数据：spring.cache.redis.cache-null-values=true
     *      缓存击穿：大量并发进来同时查询一个正好过期的数据。解决：加锁  默认是不加锁的
     *      缓存雪崩：大量的key同时过期。解决：加随机时间  加上过期时间 spring.cache.redis.time-to-live=3600000
     *  2.写模式 （缓存与数据库一致）
     *      读写加锁
     *      引入Canal，感知到MySQL的更新去更新数据库
     *      读多写多，直接去数据库查询就行
     *      总结：
     *          常规数据（读多写少，即时性，一致性要求不高的数据）；完全可以使用spring-cache
     *          特殊数据：特殊设计
     */
    @Cacheable(value = {"category"},key = "#root.method.name")
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));

        return categoryEntities;
    }

    private List<CategoryEntity> getParent_cid(List<CategoryEntity> selectList, Long parent_cid) {
        List<CategoryEntity> collect = selectList.stream().filter(item -> item.getParentCid() == parent_cid).collect(Collectors.toList());
        return collect;
    }

    @Cacheable(value = "category",key = "#root.methodName")
    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson() {
        //将数据库的多次查询变为一次
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        //查出所有1级分类
        //List<CategoryEntity> level1Categorys = getLevel1Categorys();

        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        //封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k ->
                        k.getCatId().toString(), v -> {
                    // 每一个的一级分类，查到这个一级分类的二级分类
                    //List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
                    List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
                    List<Catelog2Vo> catelog2Vos = null;
                    if (categoryEntities != null) {
                        catelog2Vos = categoryEntities.stream().map(l2 -> {
                            Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                            //找当前二级分类的三级分类封装成vo
                            //List<CategoryEntity> level3Catelog = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", l2.getCatId()));
                            List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                            if (level3Catelog != null) {
                                //封装成指定格式
                                List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                                    Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                                    return catelog3Vo;
                                }).collect(Collectors.toList());
                                catelog2Vo.setCatalog3List(collect);
                            }
                            return catelog2Vo;
                        }).collect(Collectors.toList());
                    }
                    return catelog2Vos;
                }
        ));
        return parent_cid;
    }

    //TODO 产生堆外内存溢出：OutOfDirectMemoryError
    // springboot2.0以后默认使用lettuce作为操作redis的客户端。它使用netty进行网络通信
    // lettuce的bug导致netty堆外内存溢出 -Xmx300m；netty如果没有指定堆外内存，默认使用-Xmx300m
    // 可以通过-Dio.netty.maxDirectMemory进行设置
    // 解决方案：不能使用-Dio.netty.maxDirectMemory值去调大堆外内存；
    // 升级lettuce客户端  切换使用jedis
    // lettuce、jedis操作redis的底层客户端，spring再次封装redisTemplate

    public Map<String, List<Catelog2Vo>> getCatalogJson01() {

        /**
         * 空结果缓存：解决缓存穿透
         * 设置过期时间（加随机值）：解决缓存雪崩
         * 加锁：解决缓存击穿
         */

        //给缓存中放json字符串，拿出的json字符串，还要逆转为能用的对象类型
        // 加入缓存逻辑，缓存中存的数据是json字符串
        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)) {
            //缓存中没有，查询数据库
            Map<String, List<Catelog2Vo>> catalogJsonFromDb = getCatalogJsonFromDbWithRedisLock();

            return catalogJsonFromDb;
        }
        System.out.println("查询了缓存。。。。。");
        Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
        });
        return result;
    }

    //缓存里面的数据如何和数据库保持一致
    //缓存数据一致性
    // 双写模式：
    // 失效模式：
    private Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithRedissonLock() {
        RLock lock = redissonClient.getLock("catalogJson-lock");
        lock.lock();
        try {
            String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
            if (!StringUtils.isEmpty(catalogJSON)) {
                //缓存不为null直接返回
                Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
                });
                return result;
            }
            System.out.println("查询了数据库。。。。。。");
            //将数据库的多次查询变为一次
            List<CategoryEntity> selectList = baseMapper.selectList(null);

            //查出所有1级分类
            //List<CategoryEntity> level1Categorys = getLevel1Categorys();

            List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
            //封装数据
            Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k ->
                            k.getCatId().toString(), v -> {
                        // 每一个的一级分类，查到这个一级分类的二级分类
                        //List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
                        List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
                        List<Catelog2Vo> catelog2Vos = null;
                        if (categoryEntities != null) {
                            catelog2Vos = categoryEntities.stream().map(l2 -> {
                                Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                                //找当前二级分类的三级分类封装成vo
                                //List<CategoryEntity> level3Catelog = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", l2.getCatId()));
                                List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                                if (level3Catelog != null) {
                                    //封装成指定格式
                                    List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                                        Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                                        return catelog3Vo;
                                    }).collect(Collectors.toList());
                                    catelog2Vo.setCatalog3List(collect);
                                }
                                return catelog2Vo;
                            }).collect(Collectors.toList());
                        }
                        return catelog2Vos;
                    }
            ));
            String s = JSON.toJSONString(parent_cid);
            stringRedisTemplate.opsForValue().set("catalogJSON", s);
            return parent_cid;
        } finally {
            lock.unlock();
        }
    }

    private Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithRedisLock() {

        // 占分布式锁，去redis占坑
        String uuid = UUID.randomUUID().toString();
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lock", uuid, 300, TimeUnit.SECONDS);
        if (lock) {
            System.out.println("获取分布式锁成功。。。");
            //加锁成功，执行业务
            // 设置过期时间
            //stringRedisTemplate.expire("lock",30, TimeUnit.SECONDS);

            String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
            if (!StringUtils.isEmpty(catalogJSON)) {
                //缓存不为null直接返回
                Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
                });
                return result;
            }
            System.out.println("查询了数据库。。。。。。");
            //将数据库的多次查询变为一次
            List<CategoryEntity> selectList = baseMapper.selectList(null);

            //查出所有1级分类
            //List<CategoryEntity> level1Categorys = getLevel1Categorys();

            List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
            //封装数据
            Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k ->
                            k.getCatId().toString(), v -> {
                        // 每一个的一级分类，查到这个一级分类的二级分类
                        //List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
                        List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
                        List<Catelog2Vo> catelog2Vos = null;
                        if (categoryEntities != null) {
                            catelog2Vos = categoryEntities.stream().map(l2 -> {
                                Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                                //找当前二级分类的三级分类封装成vo
                                //List<CategoryEntity> level3Catelog = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", l2.getCatId()));
                                List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                                if (level3Catelog != null) {
                                    //封装成指定格式
                                    List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                                        Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                                        return catelog3Vo;
                                    }).collect(Collectors.toList());
                                    catelog2Vo.setCatalog3List(collect);
                                }
                                return catelog2Vo;
                            }).collect(Collectors.toList());
                        }
                        return catelog2Vos;
                    }
            ));
            String s = JSON.toJSONString(parent_cid);
            stringRedisTemplate.opsForValue().set("catalogJSON", s);
            //获取值对比+对比成功删除=原子操作  LUA脚本解锁
//            String lockValue = stringRedisTemplate.opsForValue().get("lock");
//            if (uuid.equals(lockValue)){
//                stringRedisTemplate.delete("lock");
//            }
            String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                    "    return redis.call(\"del\",KEYS[1])\n" +
                    "else\n" +
                    "    return 0\n" +
                    "end";
            Long lock1 = stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList("lock"), uuid);

            return parent_cid;
        } else {
            //加锁失败，重试
            //休眠100ms重试
            System.out.println("获取分布式锁失败。。。等待重试");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getCatalogJsonFromDbWithRedisLock();//自旋的方式
        }
    }

    //从数据库查询并封装分类数据
    private Map<String, List<Catelog2Vo>> getCatalogJsonFromDb01() {
//        //如果缓存中有就用缓存的
//        Map<String, List<Catelog2Vo>> catalogJson = (Map<String, List<Catelog2Vo>>) cache.get("catalogJson");
//
//        if (catalogJson == null) {
//
//        }
        // 只要是同一把锁，就能锁住需要这个锁的所有线程
        // synchronized (this):springboot所有的组件在容器中都是单例的
        // TODO  本地锁，synchronized juc，在分布式情况下，想要锁住所有，必须使用分布式锁
        synchronized (this) {
            // 得到锁以后，我们应该再去缓存中确定一次，如果没有才需要继续查询
            String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
            if (!StringUtils.isEmpty(catalogJSON)) {
                //缓存不为null直接返回
                Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
                });
                return result;
            }
            System.out.println("查询了数据库。。。。。。");
            //将数据库的多次查询变为一次
            List<CategoryEntity> selectList = baseMapper.selectList(null);

            //查出所有1级分类
            //List<CategoryEntity> level1Categorys = getLevel1Categorys();

            List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
            //封装数据
            Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k ->
                            k.getCatId().toString(), v -> {
                        // 每一个的一级分类，查到这个一级分类的二级分类
                        //List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
                        List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
                        List<Catelog2Vo> catelog2Vos = null;
                        if (categoryEntities != null) {
                            catelog2Vos = categoryEntities.stream().map(l2 -> {
                                Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                                //找当前二级分类的三级分类封装成vo
                                //List<CategoryEntity> level3Catelog = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", l2.getCatId()));
                                List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                                if (level3Catelog != null) {
                                    //封装成指定格式
                                    List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                                        Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                                        return catelog3Vo;
                                    }).collect(Collectors.toList());
                                    catelog2Vo.setCatalog3List(collect);
                                }
                                return catelog2Vo;
                            }).collect(Collectors.toList());
                        }
                        return catelog2Vos;
                    }
            ));
            String s = JSON.toJSONString(parent_cid);
            stringRedisTemplate.opsForValue().set("catalogJSON", s);
            return parent_cid;
        }
    }

    //从数据库查询并封装分类数据
    private Map<String, List<Catelog2Vo>> getCatalogJsonFromDb() {
//        //如果缓存中有就用缓存的
//        Map<String, List<Catelog2Vo>> catalogJson = (Map<String, List<Catelog2Vo>>) cache.get("catalogJson");
//
//        if (catalogJson == null) {
//
//        }

        //将数据库的多次查询变为一次
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        //查出所有1级分类
        //List<CategoryEntity> level1Categorys = getLevel1Categorys();

        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        //封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k ->
                        k.getCatId().toString(), v -> {
                    // 每一个的一级分类，查到这个一级分类的二级分类
                    //List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
                    List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
                    List<Catelog2Vo> catelog2Vos = null;
                    if (categoryEntities != null) {
                        catelog2Vos = categoryEntities.stream().map(l2 -> {
                            Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                            //找当前二级分类的三级分类封装成vo
                            //List<CategoryEntity> level3Catelog = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", l2.getCatId()));
                            List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                            if (level3Catelog != null) {
                                //封装成指定格式
                                List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                                    Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                                    return catelog3Vo;
                                }).collect(Collectors.toList());
                                catelog2Vo.setCatalog3List(collect);
                            }
                            return catelog2Vo;
                        }).collect(Collectors.toList());
                    }
                    return catelog2Vos;
                }
        ));
        return parent_cid;
    }

    private List<Long> findParentPath(Long catelogId, List<Long> paths) {
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if (byId.getParentCid() != 0) {
            findParentPath(byId.getParentCid(), paths);
        }
        return paths;
    }

    private List<CategoryEntity> getChildrens(CategoryEntity root, List<CategoryEntity> all) {
        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map(categoryEntity -> {
            categoryEntity.setChildren(getChildrens(categoryEntity, all));
            return categoryEntity;
        }).sorted((menu1, menu2) -> {
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());
        return children;
    }

}