package com.ecm.core.repository;

import com.ecm.core.entity.AuditCategory;
import com.ecm.core.entity.AuditCategorySetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditCategorySettingRepository extends JpaRepository<AuditCategorySetting, AuditCategory> {
}
