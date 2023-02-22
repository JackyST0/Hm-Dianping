package com.xgdp.service;

import com.xgdp.dto.Result;
import com.xgdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *  服务类
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryList();
}
