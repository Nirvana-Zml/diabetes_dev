package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.FoodCheckinRequest;
import com.diabetes.checkin.dto.MedicationCheckinRequest;
import com.diabetes.checkin.entity.UserFoodPreset;
import com.diabetes.checkin.entity.UserMedicationPreset;
import com.diabetes.checkin.mapper.UserCustomPresetMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserCustomPresetServiceTest {

    private final UserCustomPresetMapper mapper = mock(UserCustomPresetMapper.class);
    private final UserCustomPresetService service = new UserCustomPresetService(mapper);

    @Test
    void saveFoodTemplate_insertsNewPreset() {
        FoodCheckinRequest request = new FoodCheckinRequest();
        request.setCategoryId("cat_grain");
        request.setFoodName("全麦面包");
        request.setCaloriesPerGram(new BigDecimal("2.65"));
        request.setIsLiquid(false);
        request.setInputUnit(CheckinConstants.INPUT_UNIT_G);
        request.setImageObjectKey("food/u1/upload_abc.jpg");

        when(mapper.findUserFoodByName("u1", "全麦面包")).thenReturn(null);

        service.saveFoodTemplate("u1", request);

        ArgumentCaptor<UserFoodPreset> captor = ArgumentCaptor.forClass(UserFoodPreset.class);
        verify(mapper).insertUserFood(captor.capture());
        assertTrue(captor.getValue().getFoodId().startsWith("uf_"));
        assertEquals("cat_grain", captor.getValue().getCategoryId());
    }

    @Test
    void saveFoodTemplate_updatesExistingPreset() {
        UserFoodPreset existing = new UserFoodPreset();
        existing.setFoodId("uf_existing");
        when(mapper.findUserFoodByName("u1", "全麦面包")).thenReturn(existing);

        FoodCheckinRequest request = new FoodCheckinRequest();
        request.setCategoryId("cat_grain");
        request.setFoodName("全麦面包");
        request.setCaloriesPerGram(new BigDecimal("2.80"));
        request.setInputUnit(CheckinConstants.INPUT_UNIT_ML);
        request.setMlToGRatio(new BigDecimal("1.03"));
        request.setImageObjectKey("food/u1/upload_new.jpg");

        service.saveFoodTemplate("u1", request);

        ArgumentCaptor<UserFoodPreset> captor = ArgumentCaptor.forClass(UserFoodPreset.class);
        verify(mapper).updateUserFood(captor.capture());
        assertEquals("uf_existing", captor.getValue().getFoodId());
        assertEquals(1, captor.getValue().getIsLiquid());
    }

    @Test
    void saveMedicationTemplate_upsertsByDrugName() {
        MedicationCheckinRequest request = new MedicationCheckinRequest();
        request.setDrugName("我的药");
        request.setDosage("0.5g");
        request.setImageObjectKey("medical/u1/upload.jpg");

        when(mapper.findUserMedicationByName("u1", "我的药")).thenReturn(null);
        service.saveMedicationTemplate("u1", request);
        verify(mapper).insertUserMedication(any(UserMedicationPreset.class));

        UserMedicationPreset existing = new UserMedicationPreset();
        existing.setDrugId("ud_1");
        when(mapper.findUserMedicationByName("u1", "我的药")).thenReturn(existing);
        request.setDosage("1g");
        service.saveMedicationTemplate("u1", request);
        ArgumentCaptor<UserMedicationPreset> captor = ArgumentCaptor.forClass(UserMedicationPreset.class);
        verify(mapper).updateUserMedication(captor.capture());
        assertEquals("ud_1", captor.getValue().getDrugId());
        assertEquals("1g", captor.getValue().getDefaultDosage());
    }
}
