package com.diabetes.audit.mapper;

import com.diabetes.audit.entity.AuditArchivedLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuditArchivedLogMapper {

    int insert(AuditArchivedLog archivedLog);

    int insertBatch(@Param("logs") List<AuditArchivedLog> logs);
}
