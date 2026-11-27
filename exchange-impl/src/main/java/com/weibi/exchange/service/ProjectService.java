package com.weibi.exchange.service;


import com.weibi.exchange.model.Project;

import java.util.List;

/**
 * 项目方管理 服务类
 *
 * @author jsong
 * @since 2022-07-03
 */
public interface ProjectService {
    /**
     * 获取用户项目方
     *
     * @param userId
     * @param coinType
     * @param status
     * @param pageNum
     * @param pageSize
     * @return
     */
    List<Project> getProjectInfo(String userId, String coinType, Boolean status, int pageNum, int pageSize);
}
