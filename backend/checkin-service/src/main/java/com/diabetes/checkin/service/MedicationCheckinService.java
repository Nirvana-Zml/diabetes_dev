package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.MedicationCheckinRequest;
import com.diabetes.checkin.entity.CheckinMedicationDetail;
import com.diabetes.checkin.entity.MedicationPreset;
import com.diabetes.checkin.mapper.CheckinMedicationDetailMapper;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MedicationCheckinService {

    private final CheckinRecordWriter recordWriter;
    private final CheckinMedicationDetailMapper medicationDetailMapper;
    private final PresetMapper presetMapper;
    private final MinioStorageService minioStorageService;

    public MedicationCheckinService(CheckinRecordWriter recordWriter,
                                    CheckinMedicationDetailMapper medicationDetailMapper,
                                    PresetMapper presetMapper,
                                    MinioStorageService minioStorageService) {
        this.recordWriter = recordWriter;
        this.medicationDetailMapper = medicationDetailMapper;
        this.presetMapper = presetMapper;
        this.minioStorageService = minioStorageService;
    }

    @Transactional
    public Map<String, Object> createCheckin(String userId, MedicationCheckinRequest request) {
        LocalDate date = parseDate(request.getCheckinDate());

        CheckinMedicationDetail detail = new CheckinMedicationDetail();
        detail.setSourceType(request.getSourceType());
        detail.setDosage(request.getDosage().trim());
        detail.setTaken(Boolean.TRUE.equals(request.getTaken()) ? 1 : 0);

        if (request.getSourceType() == CheckinConstants.SOURCE_PRESET) {
            if (request.getDrugId() == null || request.getDrugId().isBlank()) {
                throw new BusinessException(400, "选用预设药品时 drugId 必填");
            }
            MedicationPreset preset = presetMapper.findMedicationPresetById(request.getDrugId());
            if (preset == null) {
                throw new BusinessException(404, "预设药品不存在");
            }
            detail.setDrugId(preset.getDrugId());
            detail.setDrugName(preset.getDrugName());
            if (request.getImageObjectKey() != null && !request.getImageObjectKey().isBlank()) {
                detail.setImageObjectKey(request.getImageObjectKey());
            } else {
                detail.setImageObjectKey("medical/" + preset.getDrugId() + ".jpg");
            }
        } else if (request.getSourceType() == CheckinConstants.SOURCE_CUSTOM) {
            if (request.getDrugName() == null || request.getDrugName().isBlank()) {
                throw new BusinessException(400, "自定义用药时 drugName 必填");
            }
            detail.setDrugName(request.getDrugName().trim());
            detail.setImageObjectKey(request.getImageObjectKey());
        } else {
            throw new BusinessException(400, "sourceType 无效");
        }

        if (detail.getImageObjectKey() == null || detail.getImageObjectKey().isBlank()) {
            throw new BusinessException(400, "imageObjectKey 必填");
        }
        validateImageObjectKey(userId, detail.getImageObjectKey(), request.getSourceType());
        detail.setImageObjectKey(CheckinImagePathHelper.normalize(detail.getImageObjectKey()));

        String checkinId = recordWriter.createRecord(userId, CheckinConstants.TYPE_MEDICATION, date);
        detail.setCheckinId(checkinId);
        medicationDetailMapper.insert(detail);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkinId", checkinId);
        result.put("checkinDate", date.toString());
        return result;
    }

    public List<Map<String, Object>> listRecords(String userId, String dateStr) {
        LocalDate date = parseDate(dateStr);
        return medicationDetailMapper.findByUserAndDate(userId, date).stream()
                .map(this::toRecordMap)
                .toList();
    }

    private Map<String, Object> toRecordMap(CheckinMedicationDetail detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("checkinId", detail.getCheckinId());
        map.put("sourceType", detail.getSourceType());
        map.put("drugId", detail.getDrugId());
        map.put("drugName", detail.getDrugName());
        map.put("dosage", detail.getDosage());
        map.put("taken", detail.getTaken() != null && detail.getTaken() == 1);
        map.put("imageObjectKey", detail.getImageObjectKey());
        map.put("imageUrl", minioStorageService.buildCheckinImageUrl(detail.getImageObjectKey()));
        map.put("recordTime", detail.getRecordTime() != null ? detail.getRecordTime().toString() : null);
        return map;
    }

    private void validateImageObjectKey(String userId, String key, int sourceType) {
        String normalized = CheckinImagePathHelper.normalize(key);
        if (sourceType == CheckinConstants.SOURCE_PRESET) {
            if (!CheckinImagePathHelper.isValidPresetKey(normalized, "medical")) {
                throw new BusinessException(400, "imageObjectKey 格式无效，预设应为 medical/{ID}.jpg");
            }
        } else if (!CheckinImagePathHelper.isValidUserUploadKey(normalized, "medical", userId)) {
            throw new BusinessException(400, "imageObjectKey 格式无效，用户上传应为 medical/{userId}/upload_*.jpg");
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
