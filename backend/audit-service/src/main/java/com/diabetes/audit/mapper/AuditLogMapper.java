package com.diabetes.audit.mapper;

import com.diabetes.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AuditLogMapper {

    int insert(AuditLog log);

    AuditLog findById(@Param("logId") String logId);

    List<AuditLog> findByIds(@Param("logIds") List<String> logIds);

    List<AuditLog> findAdminList(@Param("userId") String userId,
                                 @Param("action") String action,
                                 @Param("actions") List<String> actions,
                                 @Param("keyword") String keyword,
                                 @Param("result") Integer result,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    int countAdminList(@Param("userId") String userId,
                       @Param("action") String action,
                       @Param("actions") List<String> actions,
                       @Param("keyword") String keyword,
                       @Param("result") Integer result,
                       @Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);

    int deleteById(@Param("logId") String logId);

    int deleteByIds(@Param("logIds") List<String> logIds);

    List<String> findDistinctActions();

    int countBetween(@Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime,
                     @Param("result") Integer result);

    List<Map<String, Object>> countGroupByAction(@Param("since") LocalDateTime since,
                                                 @Param("limit") int limit);

    List<Map<String, Object>> loginFailureTrend(@Param("since") LocalDateTime since);

    List<Map<String, Object>> topSubjects(@Param("since") LocalDateTime since,
                                          @Param("idPrefix") String idPrefix,
                                          @Param("limit") int limit);
}
