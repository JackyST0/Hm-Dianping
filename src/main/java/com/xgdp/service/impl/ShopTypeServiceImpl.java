package com.xgdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xgdp.dto.Result;
import com.xgdp.entity.ShopType;
import com.xgdp.mapper.ShopTypeMapper;
import com.xgdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

/**
 *  服务实现类
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = "cache:list";
        // 1.从redis查询商铺类型缓存
        String listJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(listJson)){
            // 3.存在，直接返回
            List<ShopType> shopTypes = JSONUtil.toList(listJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 4.不存在，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        // 5.不存在，返回错误
        if (shopTypes.size() == 0){
            return Result.fail("商铺类型不存在!");
        }
        // 6.存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypes));
        // 7.返回
        return Result.ok(shopTypes);
    }
}
