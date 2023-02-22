package com.xgdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xgdp.dto.LoginFormDTO;
import com.xgdp.dto.Result;
import com.xgdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 *  服务类
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

}
