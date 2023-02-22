package com.xgdp.service;

import com.xgdp.dto.Result;
import com.xgdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *  服务类
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
