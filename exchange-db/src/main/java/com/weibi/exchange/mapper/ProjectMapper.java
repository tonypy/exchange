package com.weibi.exchange.mapper;

import com.weibi.core.commons.db.extension.mapper.BaseDao;
import com.weibi.exchange.entity.ProjectEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 项目方管理 Mapper 接口
 *
 * @author jsong
 * @since 2022-07-03
 */
public interface ProjectMapper extends BaseDao<ProjectEntity> {
    @Select(" SELECT id, user_id, coin_type, level, trade_pair, status, withdrawal, del, create_time, update_time" +
            " FROM tbl_project" +
            " WHERE user_id = #{userId} and status = 1 and del = 1" +
            " ORDER BY id DESC limit 200 offset 0")
    List<ProjectEntity> getProjectInfo(String userId);
}
