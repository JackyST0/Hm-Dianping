package com.xgdp.service.impl;

import com.xgdp.entity.UserInfo;
import com.xgdp.mapper.UserInfoMapper;
import com.xgdp.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 *  服务实现类
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
