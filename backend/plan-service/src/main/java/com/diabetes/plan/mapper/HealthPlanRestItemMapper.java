package com.diabetes.plan.mapper;

import com.diabetes.plan.entity.HealthPlanRestItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthPlanRestItemMapper {

    int insert(HealthPlanRestItem item);
}
