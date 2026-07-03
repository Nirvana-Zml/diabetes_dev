package com.diabetes.health.service;

import com.diabetes.health.dto.RiskAssessRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MedicalCalculator 医学计算器测试")
class MedicalCalculatorTest {

    private MedicalCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MedicalCalculator();
    }

    @Test
    @DisplayName("calculateBmi - 计算正常体重 BMI")
    void calculateBmi_normalWeight() {
        MedicalCalculator.BmiResult result = calculator.calculateBmi(170f, 65f);
        assertEquals(BigDecimal.valueOf(22.5), result.bmi());
        assertEquals("normal", result.bmiLevel());
    }

    @Test
    @DisplayName("calculateBmi - 计算偏瘦 BMI")
    void calculateBmi_underweight() {
        MedicalCalculator.BmiResult result = calculator.calculateBmi(170f, 50f);
        assertEquals(BigDecimal.valueOf(17.3), result.bmi());
        assertEquals("underweight", result.bmiLevel());
    }

    @Test
    @DisplayName("calculateBmi - 计算超重 BMI")
    void calculateBmi_overweight() {
        MedicalCalculator.BmiResult result = calculator.calculateBmi(170f, 85f);
        assertEquals(BigDecimal.valueOf(29.4), result.bmi());
        assertEquals("obese", result.bmiLevel());
    }

    @Test
    @DisplayName("evaluateGlucose - 正常血糖")
    void evaluateGlucose_normal() {
        MedicalCalculator.GlucoseResult result = calculator.evaluateGlucose(5.5f);
        assertEquals(1, result.glucoseLevel());
        assertEquals("normal", result.glucoseLevelName());
    }

    @Test
    @DisplayName("evaluateGlucose - 糖尿病前期")
    void evaluateGlucose_prediabetes() {
        MedicalCalculator.GlucoseResult result = calculator.evaluateGlucose(6.5f);
        assertEquals(2, result.glucoseLevel());
        assertEquals("prediabetes", result.glucoseLevelName());
    }

    @Test
    @DisplayName("evaluateGlucose - 糖尿病")
    void evaluateGlucose_diabetes() {
        MedicalCalculator.GlucoseResult result = calculator.evaluateGlucose(7.5f);
        assertEquals(3, result.glucoseLevel());
        assertEquals("diabetes", result.glucoseLevelName());
    }

    @Test
    @DisplayName("calculateBaseRisk - 低风险")
    void calculateBaseRisk_lowRisk() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(0, result.baseRiskScore());
        assertTrue(result.riskFactors().isEmpty());
    }

    @Test
    @DisplayName("calculateBaseRisk - 中等风险")
    void calculateBaseRisk_mediumRisk() {
        RiskAssessRequest request = createRequest(170f, 75f, 6.2f, true, 0, 2, "balanced", 40);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 75f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(6.2f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 40);

        assertEquals(60, result.baseRiskScore());
        assertEquals(3, result.riskFactors().size());
    }

    @Test
    @DisplayName("calculateBaseRisk - 高风险（包含妊娠期）")
    void calculateBaseRisk_highRiskWithPregnancy() {
        RiskAssessRequest request = createRequest(165f, 65f, 6.8f, true, 1, 0, "high-sugar", 25);
        request.setIsPregnant(true);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(165f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(6.8f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 25);

        assertEquals(85, result.baseRiskScore());
        assertEquals(6, result.riskFactors().size());
    }

    @Test
    @DisplayName("calculateBaseRisk - 分数上限为100")
    void calculateBaseRisk_scoreCappedAt100() {
        RiskAssessRequest request = createRequest(160f, 100f, 8.0f, true, 2, 0, "high-fat", 55);
        request.setIsPregnant(true);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(160f, 100f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(8.0f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 55);

        assertEquals(100, result.baseRiskScore());
    }

    @Test
    @DisplayName("calculateBaseRisk - BMI超标分支")
    void calculateBaseRisk_bmiOverweight() {
        RiskAssessRequest request = createRequest(170f, 85f, 5.5f, false, 0, 3, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 85f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(20, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("BMI超标", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 血糖偏高分支(prediabetes)")
    void calculateBaseRisk_glucosePrediabetes() {
        RiskAssessRequest request = createRequest(170f, 65f, 6.5f, false, 0, 3, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(6.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(25, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("血糖偏高", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 家族遗传史分支")
    void calculateBaseRisk_familyHistory() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, true, 0, 3, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(15, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("家族遗传史", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 吸烟分支")
    void calculateBaseRisk_smoking() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 1, 3, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(10, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("吸烟", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 缺乏运动分支(exerciseFreq=0)")
    void calculateBaseRisk_lackExercise_zero() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 0, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(10, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("缺乏运动", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 缺乏运动分支(exerciseFreq=null)")
    void calculateBaseRisk_lackExercise_null() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, null, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(10, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("缺乏运动", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 不良饮食习惯(high-sugar)")
    void calculateBaseRisk_dietHighSugar() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "high-sugar", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(10, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("不良饮食习惯", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 不良饮食习惯(high-fat)")
    void calculateBaseRisk_dietHighFat() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "high-fat", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(10, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("不良饮食习惯", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 不良饮食习惯(high_sugar 下划线写法)")
    void calculateBaseRisk_dietHighSugarUnderscore() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "high_sugar", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(10, result.baseRiskScore());
        assertEquals("不良饮食习惯", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 不良饮食习惯(high_fat 下划线写法)")
    void calculateBaseRisk_dietHighFatUnderscore() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "high_fat", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(10, result.baseRiskScore());
        assertEquals("不良饮食习惯", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 年龄因素(age>=45)")
    void calculateBaseRisk_ageFactor() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "balanced", 45);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 45);

        assertEquals(10, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("年龄因素", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("calculateBaseRisk - 妊娠期分支")
    void calculateBaseRisk_pregnancy() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "balanced", 30);
        request.setIsPregnant(true);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(15, result.baseRiskScore());
        assertEquals(1, result.riskFactors().size());
        assertEquals("妊娠期", result.riskFactors().get(0).get("name"));
    }

    @Test
    @DisplayName("mapRiskLevel - 低风险分数")
    void mapRiskLevel_low() {
        assertEquals(1, calculator.mapRiskLevel(25));
    }

    @Test
    @DisplayName("mapRiskLevel - 中等风险分数")
    void mapRiskLevel_medium() {
        assertEquals(2, calculator.mapRiskLevel(45));
    }

    @Test
    @DisplayName("mapRiskLevel - 高风险分数")
    void mapRiskLevel_high() {
        assertEquals(3, calculator.mapRiskLevel(75));
    }

    @Test
    @DisplayName("calculateBaseRisk - 吸烟为 null 不计分")
    void calculateBaseRisk_smokingNull() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, null, 3, "balanced", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(0, result.baseRiskScore());
        assertTrue(result.riskFactors().isEmpty());
    }

    @Test
    @DisplayName("calculateBaseRisk - 饮食类型为空不计分")
    void calculateBaseRisk_dietTypeBlank() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, "  ", 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(0, result.baseRiskScore());
        assertTrue(result.riskFactors().isEmpty());
    }

    @Test
    @DisplayName("calculateBaseRisk - 饮食类型为 null 不计分")
    void calculateBaseRisk_dietTypeNull() {
        RiskAssessRequest request = createRequest(170f, 65f, 5.5f, false, 0, 3, null, 30);
        MedicalCalculator.BmiResult bmi = calculator.calculateBmi(170f, 65f);
        MedicalCalculator.GlucoseResult glucose = calculator.evaluateGlucose(5.5f);

        MedicalCalculator.BaseRiskResult result = calculator.calculateBaseRisk(request, bmi, glucose, 30);

        assertEquals(0, result.baseRiskScore());
        assertTrue(result.riskFactors().isEmpty());
    }

    @Test
    @DisplayName("riskLevelName - 各风险等级名称")
    void riskLevelName() {
        assertEquals("low", calculator.riskLevelName(1));
        assertEquals("medium", calculator.riskLevelName(2));
        assertEquals("high", calculator.riskLevelName(3));
    }

    private RiskAssessRequest createRequest(float height, float weight, float glucose,
            boolean familyHistory, Integer smoking, Integer exerciseFreq,
            String dietType, int age) {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(height);
        request.setWeight(weight);
        request.setFastingGlucose(glucose);
        request.setFamilyHistory(familyHistory);
        request.setSmoking(smoking);
        request.setExerciseFreq(exerciseFreq);
        request.setDietType(dietType);
        return request;
    }
}