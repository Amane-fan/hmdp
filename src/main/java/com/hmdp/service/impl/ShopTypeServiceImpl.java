package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOPTYPE_KEY;

        List<String> shopTypeList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            List<ShopType> list = shopTypeList.stream().map(shopType -> {
                return JSONUtil.toBean(shopType, ShopType.class);
            }).collect(Collectors.toList());
            return Result.ok(list);
        }

        List<ShopType> list = query().orderByAsc("sort").list();
        if (list == null || list.isEmpty()) {
            return Result.fail("没有任何分类!");
        }

        stringRedisTemplate.opsForList().rightPushAll(key,
                list.stream().map(shopType -> JSONUtil.toJsonStr(shopType)).toList());

        return Result.ok(list);
    }
}
