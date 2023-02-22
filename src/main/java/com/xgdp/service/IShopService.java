package com.xgdp.service;

import com.xgdp.dto.Result;
import com.xgdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *  服务类
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
