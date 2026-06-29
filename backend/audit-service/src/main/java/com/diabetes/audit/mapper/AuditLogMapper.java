package com.diabetes.audit.mapper;

import com.diabetes.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLog log);

    List<AuditLog> findByUserId(@Param("userId") String userId,
                                @Param("offset") int offset,
                                @Param("limit") int limit);
}
