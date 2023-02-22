package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *  服务类
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryList();
}
