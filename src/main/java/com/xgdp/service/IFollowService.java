package com.xgdp.service;

import com.xgdp.dto.Result;
import com.xgdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *  服务类
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
