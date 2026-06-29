package com.diabetes.health.mapper;

import com.diabetes.health.entity.RiskAssessment;
import com.diabetes.health.entity.RiskAssessmentFactor;
import com.diabetes.health.entity.RiskAssessmentSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RiskAssessmentMapper {

    int insert(RiskAssessment assessment);

    RiskAssessment findById(@Param("assessmentId") String assessmentId);

    List<RiskAssessment> findByUserId(@Param("userId") String userId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    int countByUserId(@Param("userId") String userId);

    int insertFactor(RiskAssessmentFactor factor);

    int insertSuggestion(RiskAssessmentSuggestion suggestion);

    List<RiskAssessmentFactor> findFactors(@Param("assessmentId") String assessmentId);

    List<RiskAssessmentSuggestion> findSuggestions(@Param("assessmentId") String assessmentId);

    RiskAssessment findLatestByUserId(@Param("userId") String userId);
}
