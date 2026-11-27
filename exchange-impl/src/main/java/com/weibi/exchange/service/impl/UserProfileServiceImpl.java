package com.weibi.exchange.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.util.AssertUtil;
import com.weibi.exchange.entity.UserProfileEntity;
import com.weibi.exchange.mapper.UserProfileMapper;
import com.weibi.exchange.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 用户个性化设置 服务实现类
 *
 * @author jsong
 * @since 2022-07-03
 */
@Service
@Primary
public class UserProfileServiceImpl extends BaseServiceImpl<UserProfileEntity> implements UserProfileService {

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Override
    public Boolean findUserProfileByOpenBkkWithhold(String userId, String profileType) {
        return findUserProfileDefault(userId, profileType, Boolean.class, false);
    }

    private <T> T findUserProfileDefault(String userId, String profileType, Class<T> clz, T defaultVal) {
        List<UserProfileEntity> userProfileEntities = userProfileMapper.selectList(new QueryWrapper<UserProfileEntity>()
                .eq("user_id", userId)
                .eq("profile_type", profileType));

        if (CollectionUtils.isEmpty(userProfileEntities)) {
            return defaultVal;
        }

        AssertUtil.isTrue(userProfileEntities.size() == 1, "user " + userId + " profileType " + profileType + " should be unique");

        // 原始方式 JSON.parseObject(userProfileEntities.get(0).getProfileValue()).toJSONString()
        // 此处因错误存储, 存入了转义符 \" , 采用兼容方式转换, 如不需要兼容则可换回去.
        return JSON.parseObject(
                JSONObject.parse(userProfileEntities.get(0).getProfileValue()).toString(), clz);
    }
}
