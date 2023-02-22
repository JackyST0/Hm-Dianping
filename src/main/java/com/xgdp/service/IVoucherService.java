package com.xgdp.service;

import com.xgdp.dto.Result;
import com.xgdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *  服务类
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
