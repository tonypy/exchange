package com.weibi.exchange.mapper;

import com.weibi.core.commons.db.extension.mapper.BaseDao;
import com.weibi.exchange.entity.UserEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper 接口
 *
 * @author jsong
 * @since 2022-07-03
 */
public interface UserMapper extends BaseDao<UserEntity> {

    /**
     * 获取所有平台用户
     *
     * @return
     */
    @Select("select id from tbl_user where user_role = 'PLATFORM'")
    List<String> allPlatformUsers();
}
