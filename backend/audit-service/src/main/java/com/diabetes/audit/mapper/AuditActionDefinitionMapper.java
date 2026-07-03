package com.diabetes.audit.mapper;

import com.diabetes.audit.entity.AuditActionDefinition;

import java.util.List;

public interface AuditActionDefinitionMapper {

    List<AuditActionDefinition> findAllEnabled();
}
