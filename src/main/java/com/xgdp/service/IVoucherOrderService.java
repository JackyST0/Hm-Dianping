package com.xgdp.service;

import com.xgdp.dto.Result;
import com.xgdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *  服务类
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
