package com.diabetes.plan.mapper;

import com.diabetes.plan.entity.HealthPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HealthPlanMapper {

    int insert(HealthPlan plan);

    int deactivateActivePlans(@Param("userId") String userId);

    HealthPlan findById(@Param("planId") String planId);

    HealthPlan findLatestByUserId(@Param("userId") String userId);

    List<HealthPlan> findHistory(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);

    int countByUserId(@Param("userId") String userId);

    int updateFavorite(@Param("planId") String planId, @Param("isFavorite") int isFavorite);
}
