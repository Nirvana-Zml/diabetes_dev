package com.diabetes.plan.mapper;

import com.diabetes.plan.entity.HealthPlanDietItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthPlanDietItemMapper {

    int insert(HealthPlanDietItem item);
}
