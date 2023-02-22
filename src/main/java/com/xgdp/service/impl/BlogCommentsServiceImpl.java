package com.xgdp.service.impl;

import com.xgdp.entity.BlogComments;
import com.xgdp.mapper.BlogCommentsMapper;
import com.xgdp.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 *  服务实现类
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
