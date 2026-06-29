package com.diabetes.user.mapper;

import com.diabetes.user.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMapper {

    Admin findByUsername(@Param("username") String username);
}
