package com.diabetes.consultation.mapper;

import com.diabetes.consultation.entity.ConsultationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConsultationMessageMapper {

    int insert(ConsultationMessage message);

    List<ConsultationMessage> findBySessionId(@Param("sessionId") String sessionId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    int countBySessionId(@Param("sessionId") String sessionId);

    List<ConsultationMessage> findRecentBySessionId(@Param("sessionId") String sessionId,
                                                    @Param("limit") int limit);

    ConsultationMessage findLatestAiMessage(@Param("sessionId") String sessionId);
}
