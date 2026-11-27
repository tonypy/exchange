package com.weibi.exchange.service;

/**
 * 用户个性化设置 服务类
 *
 * @author jsong
 * @since 2022-07-03
 */
public interface UserProfileService {
    /**
     * 获取用户属性配合
     *
     * @param userId
     * @param profileType
     * @return
     */
    Boolean findUserProfileByOpenBkkWithhold(String userId, String profileType);
}
