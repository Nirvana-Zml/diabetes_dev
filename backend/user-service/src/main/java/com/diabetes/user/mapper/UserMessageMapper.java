package com.diabetes.user.mapper;

import com.diabetes.user.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMessageMapper {

    int insert(UserMessage message);

    int updateByBiz(UserMessage message);

    UserMessage findById(@Param("messageId") String messageId);

    UserMessage findByUserTypeBiz(@Param("userId") String userId,
                                  @Param("messageType") String messageType,
                                  @Param("bizId") String bizId);

    List<UserMessage> listByUser(@Param("userId") String userId,
                                 @Param("unreadOnly") boolean unreadOnly,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    int countByUser(@Param("userId") String userId, @Param("unreadOnly") boolean unreadOnly);

    int countUnread(@Param("userId") String userId);

    int markRead(@Param("userId") String userId, @Param("messageId") String messageId);

    int markAllRead(@Param("userId") String userId);

    int markReadByBiz(@Param("userId") String userId,
                      @Param("messageType") String messageType,
                      @Param("bizId") String bizId);
}
