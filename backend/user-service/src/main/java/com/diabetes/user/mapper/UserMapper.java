package com.diabetes.user.mapper;

import com.diabetes.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findById(@Param("userId") String userId);

    User findByUsername(@Param("username") String username);

    User findByPhone(@Param("phone") String phone);

    User findByEmail(@Param("email") String email);

    int countByUsername(@Param("username") String username);

    int countByPhone(@Param("phone") String phone);

    int countByEmail(@Param("email") String email);

    int insert(User user);

    int updateProfile(User user);

    int updateAvatarId(@Param("userId") String userId, @Param("avatarId") String avatarId);

    int updatePassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash);

    int updatePrivacy(@Param("userId") String userId, @Param("privacySettings") String privacySettings);
}
