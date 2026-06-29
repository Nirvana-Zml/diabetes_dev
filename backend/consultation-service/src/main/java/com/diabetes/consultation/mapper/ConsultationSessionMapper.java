package com.diabetes.consultation.mapper;

import com.diabetes.consultation.entity.ConsultationSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConsultationSessionMapper {

    int insert(ConsultationSession session);

    ConsultationSession findById(@Param("sessionId") String sessionId);

    ConsultationSession findActiveByUserId(@Param("userId") String userId);

    List<ConsultationSession> findByUserId(@Param("userId") String userId,
                                           @Param("status") Integer status,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    int countByUserId(@Param("userId") String userId, @Param("status") Integer status);

    int updateAfterMessage(@Param("sessionId") String sessionId,
                           @Param("lastMessage") String lastMessage,
                           @Param("messageCount") int messageCount);

    int closeSession(@Param("sessionId") String sessionId,
                     @Param("rating") Integer rating,
                     @Param("feedback") String feedback);
}
