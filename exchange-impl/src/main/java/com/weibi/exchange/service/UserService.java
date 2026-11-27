package com.weibi.exchange.service;


import com.weibi.exchange.model.User;

import java.util.List;

/**
 * 用户信息
 *
 * @author jsong
 * @since 2022-07-03
 */
public interface UserService {
    /**
     * 获取所有平台用户
     *
     * @return
     */
    List<String> allPlatformUsers();

    /**
     * 根据主键查询id
     *
     * @param id
     * @return
     */
    User findUserById(String id);
}
