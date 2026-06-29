package com.diabetes.plan.mapper;

import com.diabetes.plan.entity.HealthPlanExerciseItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthPlanExerciseItemMapper {

    int insert(HealthPlanExerciseItem item);
}
