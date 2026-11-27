package com.weibi.exchange.service.impl;

import cn.hutool.core.convert.Convert;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.exchange.entity.ProjectEntity;
import com.weibi.exchange.mapper.ProjectMapper;
import com.weibi.exchange.model.Project;
import com.weibi.exchange.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目方管理 服务实现类
 *
 * @author jsong
 * @since 2022-07-03
 */
@Service
@Primary
public class ProjectServiceImpl extends BaseServiceImpl<ProjectEntity> implements ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

    @Override
    public List<Project> getProjectInfo(String userId, String coinType, Boolean status, int pageNum, int pageSize) {
        return Convert.toList(Project.class, projectMapper.getProjectInfo(userId));
    }
}
