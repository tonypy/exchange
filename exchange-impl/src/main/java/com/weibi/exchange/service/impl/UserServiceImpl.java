package com.weibi.exchange.service.impl;

import cn.hutool.core.convert.Convert;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.exchange.entity.UserEntity;
import com.weibi.exchange.mapper.UserMapper;
import com.weibi.exchange.model.User;
import com.weibi.exchange.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务实现类
 *
 * @author jsong
 * @since 2022-07-03
 */
@Service
@Primary
public class UserServiceImpl extends BaseServiceImpl<UserEntity> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<String> allPlatformUsers() {
        return userMapper.allPlatformUsers();
    }

    @Override
    public User findUserById(String id) {
        return Convert.convert(User.class, userMapper.selectById(id));
    }
}
