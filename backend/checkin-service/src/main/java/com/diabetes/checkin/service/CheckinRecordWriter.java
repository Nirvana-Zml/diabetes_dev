package com.diabetes.checkin.service;

import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.diabetes.common.util.IdGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CheckinRecordWriter {

    private final CheckinRecordMapper checkinRecordMapper;
    private final CheckinService checkinService;

    public CheckinRecordWriter(CheckinRecordMapper checkinRecordMapper, CheckinService checkinService) {
        this.checkinRecordMapper = checkinRecordMapper;
        this.checkinService = checkinService;
    }

    /**
     * 创建打卡主记录（食物/用药/运动模块不写入积分与连续天数）
     */
    public String createRecord(String userId, int checkinType, LocalDate checkinDate) {
        return createRecord(userId, checkinType, checkinDate, LocalDateTime.now());
    }

    public String createRecord(String userId, int checkinType, LocalDate checkinDate, LocalDateTime recordTime) {
        CheckinRecord record = new CheckinRecord();
        record.setCheckinId(IdGenerator.nextId("chk_"));
        record.setUserId(userId);
        record.setCheckinType(checkinType);
        record.setCheckinDate(checkinDate);
        record.setRecordTime(recordTime != null ? recordTime : LocalDateTime.now());
        record.setPointsEarned(0);
        record.setStreakDays(0);
        checkinRecordMapper.insert(record);
        checkinService.invalidateUserCache(userId, checkinDate);
        return record.getCheckinId();
    }
}
