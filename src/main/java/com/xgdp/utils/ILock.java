package com.xgdp.utils;

/**
 * @Author JianXin
 * @Date 2023/2/2 15:07
 * @Github https://github.com/JackyST0
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true 代表获取锁成功；false 代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
