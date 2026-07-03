<template>
  <SiteLayout>

    <div class="he-page" :class="{ 'he-page--mobile': isMobile }">
      <div v-if="!isMobile" class="he-page-header animate-fade-in">
        <h1 class="he-page-title">糖尿病风险预测</h1>
        <p class="he-page-desc">基于多维度健康数据，科学评估您的糖尿病患病风险，提供个性化健康建议</p>
      </div>

      <div class="he-tabs animate-fade-in" :class="{ 'he-tabs--mobile': isMobile }">
        <button
          type="button"
          class="he-tab"
          :class="{ active: activeTab === 'assess' }"
          @click="activeTab = 'assess'"
        >
          风险问卷
          <span v-if="hasUnreadHistory && !showResult" class="he-tab-dot" aria-label="有新评估报告" />
          <span v-if="activeTab === 'assess'" class="he-tab-indicator" />
        </button>
        <button
          type="button"
          class="he-tab"
          :class="{ active: activeTab === 'history' }"
          @click="switchToHistoryTab"
        >
          历史记录
          <span v-if="hasUnreadHistory" class="he-tab-dot" aria-label="有未读评估" />
          <span v-if="activeTab === 'history'" class="he-tab-indicator" />
        </button>
      </div>

      <template v-if="activeTab === 'assess'">
        <!-- 新报告提示 -->
        <div
          v-if="hasUnreadHistory && !showResult && latestUnreadAssessment"
          class="section-card he-new-report-banner animate-fade-in"
        >
          <div class="he-new-report-banner__main">
            <span class="he-new-badge he-new-badge--lg">新</span>
            <div>
              <p class="he-new-report-banner__title">您有一份新的风险评估报告</p>
              <p class="he-new-report-banner__meta">
                {{ formatTime(latestUnreadAssessment.assessed_at) }}
                · {{ levelText(latestUnreadAssessment.risk_level) }}
                · {{ latestUnreadAssessment.risk_score }} 分
              </p>
            </div>
          </div>
          <el-button type="primary" @click="viewHistory(latestUnreadAssessment)">查看新报告</el-button>
        </div>

        <!-- 用户信息概览 -->
        <div v-if="userProfile && !showResult" class="section-card he-user-card animate-fade-in">
          <div class="he-user-row">
            <div class="he-user-items">
              <div class="he-user-item">
                <div class="he-user-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </div>
                <div>
                  <p class="he-user-label">昵称</p>
                  <p class="he-user-value">{{ userProfile.nickname || userProfile.username || '-' }}</p>
                </div>
              </div>
              <div class="he-divider" />
              <div class="he-user-item">
                <div class="he-user-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                  </svg>
                </div>
                <div>
                  <p class="he-user-label">性别</p>
                  <p class="he-user-value">{{ genderLabel(userProfile.gender) }}</p>
                </div>
              </div>
              <div class="he-divider" />
              <div class="he-user-item">
                <div class="he-user-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
                <div>
                  <p class="he-user-label">年龄</p>
                  <p class="he-user-value">{{ userAge ?? '未填写' }} 岁</p>
                </div>
              </div>
            </div>
            <div class="he-user-tip">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>年龄与性别由个人资料自动读取，无需重复填写</span>
            </div>
          </div>
        </div>

        <template v-if="!showResult">
          <!-- 手机端：简化进度条，便于老年用户理解当前位置 -->
          <div v-if="isMobile" class="section-card he-mobile-progress animate-fade-in">
            <div class="he-mobile-progress__top">
              <span class="he-mobile-progress__label">第 {{ currentStep + 1 }} 步，共 {{ QUESTIONNAIRE_STEPS.length }} 步</span>
              <span class="he-mobile-progress__percent">{{ stepProgressPercent }}%</span>
            </div>
            <div class="he-mobile-progress__track" role="progressbar" :aria-valuenow="currentStep + 1" :aria-valuemin="1" :aria-valuemax="QUESTIONNAIRE_STEPS.length">
              <div class="he-mobile-progress__fill" :style="{ width: `${stepProgressPercent}%` }" />
            </div>
            <h2 class="he-mobile-progress__title">{{ QUESTIONNAIRE_STEPS[currentStep].title }}</h2>
            <p class="he-mobile-progress__desc">{{ QUESTIONNAIRE_STEPS[currentStep].desc }}</p>
            <div class="he-mobile-progress__dots" aria-hidden="true">
              <span
                v-for="(_, i) in QUESTIONNAIRE_STEPS"
                :key="i"
                class="he-mobile-progress__dot"
                :class="{ active: i === currentStep, completed: i < currentStep }"
              />
            </div>
          </div>

          <!-- 桌面端：完整步骤条 -->
          <div v-else class="section-card he-steps-card animate-fade-in">
            <div class="he-steps">
              <div
                v-for="(s, i) in QUESTIONNAIRE_STEPS"
                :key="i"
                class="he-step"
                :class="{ active: i === currentStep, completed: i < currentStep, pending: i > currentStep }"
                @click="goToStep(i)"
              >
                <div
                  v-if="i < QUESTIONNAIRE_STEPS.length - 1"
                  class="he-step-connector"
                  :class="{ completed: i < currentStep }"
                />
                <div class="he-step-circle">
                  <svg v-if="i < currentStep" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  <span v-else>{{ i + 1 }}</span>
                </div>
                <p class="he-step-title">{{ s.title }}</p>
                <p class="he-step-desc">{{ s.desc }}</p>
              </div>
            </div>
          </div>

          <!-- 表单内容 -->
          <div class="section-card he-form-card animate-fade-in" :class="{ 'he-form-card--mobile': isMobile }">
            <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="step-form" :class="{ 'step-form--mobile': isMobile }">
              <!-- Step 0: 基本信息提示 -->
              <div v-show="currentStep === 0" class="step-panel">
                <div class="he-info-banner">
                  <div class="he-info-banner-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  </div>
                  <span>以下信息已从您的账户自动获取</span>
                </div>
                <div class="he-info-table">
                  <div class="he-info-header">
                    <div>项目</div>
                    <div>值</div>
                    <div>项目</div>
                    <div>值</div>
                  </div>
                  <div class="he-info-row">
                    <div class="he-info-label">用户名</div>
                    <div class="he-info-value">{{ userProfile?.username || '-' }}</div>
                    <div class="he-info-label">性别</div>
                    <div class="he-info-value">{{ genderLabel(userProfile?.gender) }}</div>
                  </div>
                  <div class="he-info-row">
                    <div class="he-info-label">出生日期</div>
                    <div class="he-info-value">{{ userProfile?.birth_date || '未设置' }}</div>
                    <div class="he-info-label">年龄</div>
                    <div class="he-info-value">{{ userAge ?? '-' }} 岁</div>
                  </div>
                </div>
                <p class="he-footnote">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  如需修改性别或出生日期，请前往
                  <router-link to="/user-center" class="he-link">个人中心</router-link>
                  更新资料。
                </p>
              </div>

              <!-- Step 1: 体征指标 -->
              <div v-show="currentStep === 1" class="step-panel">
                <p v-if="isMobile" class="he-step-hint">请填写最近一次测量数据，不确定的可参考体检报告</p>
                <el-row :gutter="isMobile ? 0 : 16">
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="身高 (cm)" prop="height">
                      <el-input-number v-model="form.height" :min="100" :max="250" :step="0.5" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="体重 (kg)" prop="weight">
                      <el-input-number v-model="form.weight" :min="30" :max="300" :step="0.1" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="空腹血糖 (mmol/L)" prop="fasting_glucose">
                      <el-input-number v-model="form.fasting_glucose" :min="2" :max="30" :step="0.1" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="餐后2h血糖 (mmol/L)">
                      <el-input-number v-model="form.postprandial_glucose" :min="2" :max="30" :step="0.1" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="随机血糖 (mmol/L)">
                      <el-input-number v-model="form.random_glucose" :min="2" :max="30" :step="0.1" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="糖化血红蛋白 HbA1c (%)">
                      <el-input-number v-model="form.hba1c" :min="4" :max="15" :step="0.1" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="收缩压 (mmHg)" prop="systolic_bp">
                      <el-input-number v-model="form.systolic_bp" :min="60" :max="250" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="舒张压 (mmHg)" prop="diastolic_bp">
                      <el-input-number v-model="form.diastolic_bp" :min="30" :max="150" :size="isMobile ? 'large' : 'default'" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="数据来源">
                      <el-select v-model="form.test_source" :size="isMobile ? 'large' : 'default'" style="width:100%">
                        <el-option v-for="o in TEST_SOURCE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>

              <!-- Step 2: 生活方式 -->
              <div v-show="currentStep === 2" class="step-panel">
                <p v-if="isMobile" class="he-step-hint">请选择最符合您日常情况的选项</p>
                <el-row :gutter="isMobile ? 0 : 16">
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="吸烟状况">
                      <el-select v-model="form.smoking" :size="isMobile ? 'large' : 'default'" style="width:100%">
                        <el-option v-for="o in SMOKING_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="饮酒状况">
                      <el-select v-model="form.alcohol" :size="isMobile ? 'large' : 'default'" style="width:100%">
                        <el-option v-for="o in ALCOHOL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="运动频率">
                      <el-select v-model="form.exercise_freq" :size="isMobile ? 'large' : 'default'" style="width:100%">
                        <el-option v-for="o in EXERCISE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="饮食习惯">
                      <el-select v-model="form.diet_type" :size="isMobile ? 'large' : 'default'" style="width:100%">
                        <el-option v-for="o in DIET_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="是否有糖尿病家族史" prop="family_history">
                      <el-radio-group v-model="form.family_history" class="he-radio-cards">
                        <el-radio :value="true">有</el-radio>
                        <el-radio :value="false">无</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>

              <!-- Step 3: 糖尿病状态 -->
              <div v-show="currentStep === 3" class="step-panel">
                <el-row :gutter="isMobile ? 0 : 16">
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="糖尿病分型">
                      <el-select v-model="form.diabetes_type" :size="isMobile ? 'large' : 'default'" style="width:100%">
                        <el-option v-for="o in DIABETES_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="确诊日期">
                      <el-date-picker v-model="form.diagnosed_date" type="date" value-format="YYYY-MM-DD" :size="isMobile ? 'large' : 'default'" style="width:100%" placeholder="如已确诊请填写" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="isMobile ? 24 : 12">
                    <el-form-item label="是否使用胰岛素">
                      <el-radio-group v-model="form.is_insulin_taken" class="he-radio-cards">
                        <el-radio :value="true">是</el-radio>
                        <el-radio :value="false">否</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                  <el-col v-if="showPregnantOption" :span="isMobile ? 24 : 12">
                    <el-form-item label="是否处于妊娠期">
                      <el-radio-group v-model="form.is_pregnant" class="he-radio-cards">
                        <el-radio :value="true">是</el-radio>
                        <el-radio :value="false">否</el-radio>
                      </el-radio-group>
                      
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>

              <!-- Step 4: 家族病史 -->
              <div v-show="currentStep === 4" class="step-panel">
                <div class="list-toolbar">
                  <span class="list-title">家族病史明细</span>
                  <el-button type="primary" link @click="addFamilyHistory">+ 添加一条</el-button>
                </div>
                <el-empty v-if="!form.family_histories.length" description="暂无家族病史，可点击添加" />
                <div v-for="(item, idx) in form.family_histories" :key="idx" class="list-item-card">
                  <el-row :gutter="12">
                    <el-col :span="8">
                      <el-form-item label="亲属关系">
                        <el-select v-model="item.relation" style="width:100%">
                          <el-option v-for="r in RELATION_OPTIONS" :key="r" :label="r" :value="r" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="疾病名称">
                        <el-input v-model="item.disease_name" placeholder="如：2型糖尿病" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="是否糖尿病相关">
                        <el-switch v-model="item.is_diabetes" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="亲属年龄">
                        <el-input-number v-model="item.member_age" :min="1" :max="120" style="width:100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="确诊年龄">
                        <el-input-number v-model="item.diagnosed_age" :min="1" :max="120" style="width:100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="是否健在">
                        <el-switch v-model="item.is_alive" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="24">
                      <el-form-item label="补充说明">
                        <el-input v-model="item.note" type="textarea" :rows="1" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-button type="danger" link @click="form.family_histories.splice(idx, 1)">删除</el-button>
                </div>
              </div>

              <!-- Step 5: 既往病史 -->
              <div v-show="currentStep === 5" class="step-panel">
                <div class="list-toolbar">
                  <span class="list-title">既往病史明细</span>
                  <el-button type="primary" link @click="addMedicalHistory">+ 添加一条</el-button>
                </div>
                <el-empty v-if="!form.medical_histories.length" description="暂无既往病史，可点击添加" />
                <div v-for="(item, idx) in form.medical_histories" :key="idx" class="list-item-card">
                  <el-row :gutter="12">
                    <el-col :span="10">
                      <el-form-item label="疾病名称">
                        <el-input v-model="item.disease_name" placeholder="如：高血压" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="7">
                      <el-form-item label="确诊日期">
                        <el-date-picker v-model="item.diagnosed_date" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="7">
                      <el-form-item label="病程状态">
                        <el-select v-model="item.status" style="width:100%">
                          <el-option v-for="o in MEDICAL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="24">
                      <el-form-item label="补充说明">
                        <el-input v-model="item.note" type="textarea" :rows="1" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-button type="danger" link @click="form.medical_histories.splice(idx, 1)">删除</el-button>
                </div>
              </div>

              <!-- Step 6: 用药 -->
              <div v-show="currentStep === 6" class="step-panel">
                <div class="list-toolbar">
                  <span class="list-title">当前用药明细</span>
                  <el-button type="primary" link @click="addMedication">+ 添加一条</el-button>
                </div>
                <el-empty v-if="!form.medications.length" description="暂无用药记录，可点击添加" />
                <div v-for="(item, idx) in form.medications" :key="idx" class="list-item-card">
                  <el-row :gutter="12">
                    <el-col :span="8">
                      <el-form-item label="药品名称">
                        <el-input v-model="item.drug_name" placeholder="如：二甲双胍" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="剂量">
                        <el-input v-model="item.dosage" placeholder="如：0.5g" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="服用说明">
                        <el-input v-model="item.frequency_desc" placeholder="如：每日两次" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="是否胰岛素">
                        <el-switch v-model="item.is_insulin" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="状态">
                        <el-select v-model="item.status" style="width:100%">
                          <el-option v-for="o in MEDICATION_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-button type="danger" link @click="form.medications.splice(idx, 1)">删除</el-button>
                </div>
              </div>
            </el-form>

            <div class="he-step-actions" :class="{ 'he-step-actions--sticky': isMobile }">
              <el-button
                v-if="currentStep > 0"
                :size="isMobile ? 'large' : 'large'"
                class="he-btn-secondary"
                @click="currentStep--"
              >
                上一步
              </el-button>
              <el-button
                v-if="currentStep < QUESTIONNAIRE_STEPS.length - 1"
                type="primary"
                size="large"
                class="he-btn-primary"
                @click="nextStep"
              >
                下一步
                <svg v-if="!isMobile" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                </svg>
              </el-button>
              <el-button
                v-else
                type="primary"
                size="large"
                class="he-btn-primary"
                :loading="submitting"
                @click="submitAssess"
              >
                提交评估
              </el-button>
            </div>
            <div v-if="isMobile && !showResult" class="he-step-actions-spacer" aria-hidden="true" />
          </div>
        </template>

        <!-- 评估报告 -->
        <div v-if="showResult && result" class="section-card he-report-card animate-fade-in">
          <div class="he-report-header">
            <div>
              <div class="he-report-title-row">
                <h3 class="he-report-title">AI 风险评估报告</h3>
                <span v-if="isCurrentResultUnread" class="he-new-badge">新</span>
              </div>
              <p class="he-report-sub">基于您提交的健康数据综合评估</p>
            </div>
            <el-button link type="primary" @click="resetQuestionnaire">重新填写问卷</el-button>
          </div>

          <div class="he-risk-banner" :class="`he-risk-banner--${result.risk_level}`">
            <div class="he-risk-banner-main">
              <span class="he-risk-level">{{ levelText(result.risk_level) }}</span>
              <span class="he-risk-score">{{ result.risk_score }} 分</span>
            </div>
            <p v-if="result.report_summary" class="he-risk-summary">{{ result.report_summary }}</p>
          </div>

          <el-row :gutter="20" class="he-charts-row">
            <el-col :xs="24" :md="12">
              <div ref="gaugeRef" class="chart-box he-chart-box" />
            </el-col>
            <el-col :xs="24" :md="12">
              <div ref="radarRef" class="chart-box he-chart-box" />
            </el-col>
          </el-row>

          <div class="he-metrics-grid">
            <div class="he-metric-item">
              <span class="he-metric-label">BMI</span>
              <span class="he-metric-value">{{ result.bmi }} <small>({{ bmiLevelText(result.bmi_level) }})</small></span>
            </div>
            <div class="he-metric-item">
              <span class="he-metric-label">血糖等级</span>
              <span class="he-metric-value">{{ glucoseText(result.glucose_level) }}</span>
            </div>
            <div class="he-metric-item">
              <span class="he-metric-label">置信度</span>
              <span class="he-metric-value">{{ confidenceText(result.confidence) }}</span>
            </div>
            <div class="he-metric-item">
              <span class="he-metric-label">评估时间</span>
              <span class="he-metric-value">{{ result.assessed_at || '刚刚' }}</span>
            </div>
          </div>

          <h4 class="he-sub-title">风险因素</h4>
          <div class="he-factor-list">
            <div v-for="(f, i) in result.factors" :key="i" class="he-factor-item">
              <div class="he-factor-head">
                <span class="he-factor-name">{{ f.name }}</span>
                <span class="he-factor-level" :class="factorLevelClass(f.level)">{{ f.level }}</span>
              </div>
              <p class="he-factor-desc">{{ f.description }}</p>
            </div>
          </div>

          <h4 class="he-sub-title">改善建议</h4>
          <ul class="he-suggestion-list">
            <li v-for="(s, i) in result.suggestions" :key="i">{{ s }}</li>
          </ul>

          <div class="he-report-actions">
            <el-button type="primary" size="large" class="he-btn-primary" @click="$router.push('/living-plans')">
              生成健康方案
            </el-button>
          </div>
          <DisclaimerBar class="he-disclaimer" />
        </div>
      </template>

      <!-- 历史记录 -->
      <template v-else>
        <div class="section-card he-history-card animate-fade-in">
          <div v-if="!historyList.length" class="he-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            <p>暂无评估记录</p>
          </div>
          <div v-else class="he-history-list">
            <div
              v-for="row in historyList"
              :key="row.assessment_id || row.assessed_at"
              class="he-history-item"
              :class="{ 'he-history-item--unread': isUnreadAssessment(row) }"
              @click="viewHistory(row)"
            >
              <div class="he-history-time">
                {{ formatTime(row.assessed_at) }}
                <span v-if="isUnreadAssessment(row)" class="he-new-badge he-new-badge--inline">新</span>
              </div>
              <div class="he-history-meta">
                <span class="he-history-level" :class="`he-history-level--${row.risk_level}`">
                  {{ levelText(row.risk_level) }}
                </span>
                <span class="he-history-score">{{ row.risk_score }} 分</span>
              </div>
              <svg class="he-history-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
              </svg>
            </div>
          </div>
        </div>
      </template>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import DisclaimerBar from '@/components/DisclaimerBar.vue'
import { assessRisk, getRiskHistory, getRiskDetail } from '@/api/risk'
import { getUserProfile } from '@/api/user'
import { getHealthRecord } from '@/api/healthRecord'
import { useUserStore } from '@/stores/user'
import { useMessageCenter } from '@/composables/useMessageCenter'
import { useIsMobile } from '@/composables/useBreakpoints'
import {
  QUESTIONNAIRE_STEPS,
  DIABETES_TYPE_OPTIONS,
  SMOKING_OPTIONS,
  ALCOHOL_OPTIONS,
  EXERCISE_OPTIONS,
  DIET_OPTIONS,
  TEST_SOURCE_OPTIONS,
  RELATION_OPTIONS,
  MEDICAL_STATUS_OPTIONS,
  MEDICATION_STATUS_OPTIONS,
  createDefaultForm,
  emptyFamilyHistory,
  emptyMedicalHistory,
  emptyMedication,
  calcAge,
  genderLabel,
  normalizeDietType,
} from './constants'

const RISK_PENDING_MAX_MS = 130000

const isMobile = useIsMobile()
const userStore = useUserStore()
const activeTab = ref('assess')
const formRef = ref()
const submitting = ref(false)
const result = ref(null)
const showResult = ref(false)
const historyList = ref([])
const unreadAssessmentIds = ref([])
const assessPending = ref(false)
const gaugeRef = ref()
const radarRef = ref()
const userProfile = ref(null)
const currentStep = ref(0)

let gaugeChart = null
let radarChart = null

const hasUnreadHistory = computed(() => unreadAssessmentIds.value.length > 0)

const latestUnreadAssessment = computed(() =>
  historyList.value.find((row) => isUnreadAssessment(row)) ?? null,
)

const isCurrentResultUnread = computed(() => {
  const id = result.value?.assessment_id
  return id && unreadAssessmentIds.value.includes(id)
})

function riskStorageKey(suffix) {
  const uid = userProfile.value?.user_id || userProfile.value?.id || userStore.profile?.user_id || 'guest'
  return `he_risk_${uid}_${suffix}`
}

function readUnreadIds() {
  try {
    const raw = localStorage.getItem(riskStorageKey('unread'))
    unreadAssessmentIds.value = raw ? JSON.parse(raw) : []
  } catch {
    unreadAssessmentIds.value = []
  }
}

function persistUnreadIds() {
  localStorage.setItem(riskStorageKey('unread'), JSON.stringify(unreadAssessmentIds.value))
}

function addUnreadAssessment(id) {
  if (!id || unreadAssessmentIds.value.includes(id)) return
  unreadAssessmentIds.value = [...unreadAssessmentIds.value, id]
  persistUnreadIds()
}

function isUnreadAssessment(row) {
  const id = row?.assessment_id
  return id && unreadAssessmentIds.value.includes(id)
}

function syncPendingFromStorage() {
  const raw = localStorage.getItem(riskStorageKey('pending_at'))
  if (!raw) {
    assessPending.value = false
    return
  }
  const elapsed = Date.now() - Number(raw)
  if (!Number.isFinite(elapsed) || elapsed > RISK_PENDING_MAX_MS) {
    localStorage.removeItem(riskStorageKey('pending_at'))
    assessPending.value = false
    return
  }
  assessPending.value = true
}

function markAssessPending() {
  localStorage.setItem(riskStorageKey('pending_at'), String(Date.now()))
  assessPending.value = true
}

function clearAssessPending() {
  localStorage.removeItem(riskStorageKey('pending_at'))
  assessPending.value = false
}

function snapshotForm() {
  return {
    ...form,
    family_histories: form.family_histories.map((item) => ({ ...item })),
    medical_histories: form.medical_histories.map((item) => ({ ...item })),
    medications: form.medications.map((item) => ({ ...item })),
  }
}

const form = reactive(createDefaultForm())

const userAge = computed(() => calcAge(userProfile.value?.birth_date))

const stepProgressPercent = computed(() =>
  Math.round(((currentStep.value + 1) / QUESTIONNAIRE_STEPS.length) * 100),
)

const showPregnantOption = computed(() => {
  const g = userProfile.value?.gender
  if (g === 2 || g === 'female') return true
  if (form.diabetes_type === 4) return true
  return false
})

const rules = {
  height: [{ required: true, message: '请输入身高', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入体重', trigger: 'blur' }],
  fasting_glucose: [{ required: true, message: '请输入空腹血糖', trigger: 'blur' }],
  systolic_bp: [{ required: true, message: '请输入收缩压', trigger: 'blur' }],
  diastolic_bp: [{ required: true, message: '请输入舒张压', trigger: 'blur' }],
  family_history: [{ required: true, message: '请选择家族史', trigger: 'change' }],
}

onMounted(async () => {
  await loadProfile()
  readUnreadIds()
  syncPendingFromStorage()
  await Promise.all([loadHealthRecord(), loadHistory()])
})

onBeforeUnmount(() => {
  disposeCharts()
})

watch(
  [showResult, activeTab, () => result.value?.assessment_id],
  ([show, tab]) => {
    if (show && tab === 'assess' && result.value) {
      scheduleRenderCharts()
    } else {
      disposeCharts()
    }
  },
)

async function loadProfile() {
  try {
    userProfile.value = await getUserProfile()
  } catch {
    userProfile.value = {}
  }
}

async function loadHealthRecord() {
  try {
    const record = await getHealthRecord()
    if (!record || !Object.keys(record).length) return
    Object.assign(form, createDefaultForm(), {
      height: record.height ?? form.height,
      weight: record.weight ?? form.weight,
      fasting_glucose: record.fasting_glucose ?? form.fasting_glucose,
      postprandial_glucose: record.postprandial_glucose,
      random_glucose: record.random_glucose,
      hba1c: record.hba1c,
      systolic_bp: record.systolic_bp ?? form.systolic_bp,
      diastolic_bp: record.diastolic_bp ?? form.diastolic_bp,
      diabetes_type: record.diabetes_type ?? 9,
      diagnosed_date: record.diagnosed_date,
      is_pregnant: !!record.is_pregnant,
      family_history: !!record.family_history,
      is_insulin_taken: !!record.is_insulin_taken,
      smoking: record.smoking ?? 0,
      alcohol: record.alcohol ?? 0,
      exercise_freq: record.exercise_freq ?? 1,
      diet_type: normalizeDietType(record.diet_type),
      test_source: record.test_source ?? 1,
      family_histories: (record.family_histories || []).map((f) => ({ ...f })),
      medical_histories: (record.medical_histories || []).map((m) => ({ ...m })),
      medications: (record.medications || []).map((m) => ({ ...m })),
    })
  } catch {
    // 无历史档案时使用默认值
  }
}

async function loadHistory() {
  try {
    const data = await getRiskHistory()
    historyList.value = data.list
  } catch {
    historyList.value = []
  }
  if (assessPending.value && historyList.value.length > 0) {
    const latestId = historyList.value[0]?.assessment_id
    const pendingAt = Number(localStorage.getItem(riskStorageKey('pending_at')) || 0)
    const latestAt = historyList.value[0]?.assessed_at ? new Date(historyList.value[0].assessed_at).getTime() : 0
    if (latestId && pendingAt && latestAt >= pendingAt - 5000) {
      clearAssessPending()
      addUnreadAssessment(latestId)
    }
  }
}

function switchToHistoryTab() {
  activeTab.value = 'history'
}

function addFamilyHistory() {
  form.family_histories.push(emptyFamilyHistory())
}

function addMedicalHistory() {
  form.medical_histories.push(emptyMedicalHistory())
}

function addMedication() {
  form.medications.push(emptyMedication())
}

async function nextStep() {
  if (currentStep.value === 1) {
    const valid = await formRef.value?.validateField?.(['height', 'weight', 'fasting_glucose', 'systolic_bp', 'diastolic_bp']).catch(() => false)
    if (valid === false) return
  }
  if (currentStep.value === 2) {
    const valid = await formRef.value?.validateField?.(['family_history']).catch(() => false)
    if (valid === false) return
  }
  currentStep.value++
}

function goToStep(index) {
  if (index < currentStep.value) currentStep.value = index
}

function factorLevelClass(level) {
  if (!level) return ''
  if (level.includes('高')) return 'he-factor-level--high'
  if (level.includes('中')) return 'he-factor-level--medium'
  return 'he-factor-level--low'
}

function levelText(l) {
  return { low: '低风险', medium: '中风险', high: '高风险' }[l] || l
}
function bmiLevelText(l) {
  return { underweight: '偏瘦', normal: '正常', overweight: '超重', obese: '肥胖' }[l] || l || '-'
}
function glucoseText(l) {
  return { normal: '正常', prediabetes: '糖尿病前期', diabetes: '糖尿病' }[l] || l
}
function confidenceText(c) {
  return { low: '低', medium: '中', high: '高' }[c] || c
}
function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

async function submitAssess() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) {
    ElMessage.warning('请完善必填项（体征指标与家族史）')
    currentStep.value = 1
    return
  }
  if (submitting.value || assessPending.value) {
    ElMessage.info('分析中，请稍后查看')
    return
  }

  const payload = snapshotForm()
  submitting.value = true
  showResult.value = false
  result.value = null
  currentStep.value = 0
  markAssessPending()

  ElMessage.info({
    message: '分析中，请稍后查看',
    duration: 5000,
  })

  assessRisk(payload)
    .then(async (res) => {
      clearAssessPending()
      const id = res.assessment_id || res.assessmentId
      if (id) addUnreadAssessment(id)
      await loadHistory()
      const { refresh } = useMessageCenter()
      refresh()
    })
    .catch((e) => {
      clearAssessPending()
      if (document.visibilityState === 'visible') {
        ElMessage.error(e?.message || '评估生成失败，请稍后重试')
      }
      useMessageCenter().refresh()
    })
    .finally(() => {
      submitting.value = false
    })
}

function resetQuestionnaire() {
  showResult.value = false
  result.value = null
  currentStep.value = 0
}

function disposeCharts() {
  gaugeChart?.dispose()
  radarChart?.dispose()
  gaugeChart = null
  radarChart = null
}

function ensureChart(el, current) {
  if (current && current.getDom?.() === el) return current
  current?.dispose()
  return echarts.init(el)
}

/** 0~1 小数权重归一化到 0~100，与后端 MedicalCalculator 刻度对齐 */
function radarFactorValues(factors) {
  const raw = factors.map((f) => Number(f.weight) || 0)
  const usePercentScale = raw.some((w) => w > 0) && raw.every((w) => w <= 1)
  const values = usePercentScale ? raw.map((w) => w * 100) : raw
  const max = Math.max(...values, 30)
  return {
    indicator: factors.map((f, i) => ({ name: f.name, max })),
    values,
  }
}

async function scheduleRenderCharts() {
  await nextTick()
  await new Promise((resolve) => requestAnimationFrame(resolve))
  renderCharts()
}

function renderCharts() {
  if (!result.value || !gaugeRef.value) return

  gaugeChart = ensureChart(gaugeRef.value, gaugeChart)
  gaugeChart.setOption({
    series: [{
      type: 'gauge',
      min: 0,
      max: 100,
      detail: { formatter: '{value}分', fontSize: isMobile.value ? 22 : 18 },
      data: [{ value: result.value.risk_score, name: '风险分值' }],
      axisLine: { lineStyle: { color: [[0.4, '#67c23a'], [0.7, '#e6a23c'], [1, '#f56c6c']] } },
    }],
  })

  if (radarRef.value && result.value.factors?.length) {
    const { indicator, values } = radarFactorValues(result.value.factors)
    radarChart = ensureChart(radarRef.value, radarChart)
    radarChart.setOption({
      radar: { indicator },
      series: [{ type: 'radar', data: [{ value: values, name: '风险因素' }] }],
    })
  } else {
    radarChart?.dispose()
    radarChart = null
  }
}

async function viewHistory(row) {
  if (row.assessment_id) {
    unreadAssessmentIds.value = unreadAssessmentIds.value.filter((id) => id !== row.assessment_id)
    persistUnreadIds()
  }
  const detail = row.assessment_id ? await getRiskDetail(row.assessment_id) : row
  result.value = detail
  showResult.value = true
  activeTab.value = 'assess'
}
</script>

<style scoped>
.he-page {
  --he-primary: var(--health-600, #0d9488);
  --he-primary-light: var(--health-500, #14b8a6);
  --he-primary-dark: var(--health-700, #0f766e);
  --he-primary-bg: #f0fdfa;
  --he-text: #1e293b;
  --he-text-secondary: #64748b;
  --he-text-muted: #94a3b8;
  --he-border: #e2e8f0;
  --he-border-light: #f1f5f9;
  --he-bg: #f8fafc;
  --he-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

@keyframes heFadeInUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: heFadeInUp 0.5s ease-out forwards;
}

.he-page-header { margin-bottom: 32px; }
.he-page-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--he-text);
  letter-spacing: -0.02em;
  margin: 0 0 8px;
}
.he-page-desc {
  font-size: 15px;
  color: var(--he-text-secondary);
  line-height: 1.6;
  margin: 0;
}

/* Tabs */
.he-tabs {
  display: flex;
  border-bottom: 1px solid var(--he-border);
  margin-bottom: 32px;
}
.he-tabs--mobile {
  margin-bottom: 16px;
  border-bottom: 2px solid var(--he-border);
}
.he-tab {
  position: relative;
  padding: 16px 32px;
  font-size: 16px;
  font-weight: 500;
  color: var(--he-text-muted);
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
}
.he-tab:hover { color: var(--he-text-secondary); }
.he-tab.active { color: var(--he-primary); font-weight: 600; }
.he-tab-dot {
  position: absolute;
  top: 12px;
  right: 18px;
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  box-shadow: 0 0 0 2px #fff;
}
.he-tab-indicator {
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 100%;
  height: 3px;
  background: var(--he-primary);
  border-radius: 3px 3px 0 0;
}

.he-new-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  background: #ef4444;
  border-radius: 999px;
  flex-shrink: 0;
}
.he-new-badge--lg {
  min-width: 28px;
  height: 28px;
  font-size: 13px;
}
.he-new-badge--inline {
  margin-left: 8px;
  vertical-align: middle;
}
.he-new-report-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  margin-bottom: 24px;
  border: 1px solid #fecaca;
  background: linear-gradient(135deg, #fff7ed, #fff1f2);
}
.he-new-report-banner__main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.he-new-report-banner__title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--he-text);
}
.he-new-report-banner__meta {
  margin: 0;
  font-size: 13px;
  color: var(--he-text-secondary);
}
.he-report-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* User overview card */
.he-user-card { padding: 32px; }
.he-user-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.he-user-items {
  display: flex;
  align-items: center;
  gap: 48px;
  flex-wrap: wrap;
}
.he-user-item {
  display: flex;
  align-items: center;
  gap: 16px;
}
.he-user-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--he-primary-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.he-user-icon svg { width: 24px; height: 24px; color: var(--he-primary); }
.he-user-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--he-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0 0 4px;
}
.he-user-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--he-text);
  margin: 0;
}
.he-divider { width: 1px; height: 48px; background: var(--he-border); }
.he-user-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--he-primary-bg);
  border-radius: 12px;
  font-size: 14px;
  color: var(--he-primary);
  font-weight: 500;
}
.he-user-tip svg { width: 16px; height: 16px; flex-shrink: 0; }

/* Steps */
.he-steps-card { padding: 40px 32px; }
.he-steps {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.he-step {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  z-index: 1;
}
.he-step.pending { cursor: default; }
.he-step-connector {
  position: absolute;
  top: 20px;
  left: 50%;
  right: -50%;
  height: 2px;
  background: var(--he-border);
  z-index: 0;
}
.he-step-connector.completed { background: var(--he-primary); }
.he-step-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
  position: relative;
  z-index: 1;
  transition: all 0.2s;
}
.he-step-circle svg { width: 20px; height: 20px; }
.he-step.active .he-step-circle {
  background: var(--he-primary);
  color: #fff;
  box-shadow: 0 4px 14px rgba(13, 148, 136, 0.3);
  outline: 4px solid var(--he-primary-bg);
}
.he-step.completed .he-step-circle {
  background: var(--he-primary);
  color: #fff;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.2);
}
.he-step.pending .he-step-circle {
  background: #fff;
  border: 2px solid var(--he-border);
  color: var(--he-text-muted);
}
.he-step.completed:hover .he-step-circle {
  border-color: var(--he-primary);
  color: var(--he-primary);
}
.he-step-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 4px;
  text-align: center;
  color: var(--he-text-secondary);
}
.he-step.active .he-step-title,
.he-step.completed .he-step-title { color: var(--he-primary); }
.he-step-desc {
  font-size: 12px;
  color: var(--he-text-muted);
  margin: 0;
  text-align: center;
}

/* Form card */
.he-form-card { padding: 32px; }
.step-form { min-height: 280px; }
.step-panel { padding: 4px 0; }

.he-info-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: var(--he-primary-bg);
  border-radius: 12px;
  margin-bottom: 24px;
  font-size: 14px;
  font-weight: 500;
  color: var(--he-primary);
}
.he-info-banner-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(13, 148, 136, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.he-info-banner-icon svg { width: 16px; height: 16px; }

.he-info-table {
  border: 1px solid var(--he-border);
  border-radius: 12px;
  overflow: hidden;
}
.he-info-header,
.he-info-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  align-items: center;
}
.he-info-header {
  background: var(--he-bg);
  border-bottom: 1px solid var(--he-border);
}
.he-info-header > div {
  padding: 16px 24px;
  font-size: 13px;
  font-weight: 600;
  color: var(--he-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.he-info-row { border-bottom: 1px solid var(--he-border-light); }
.he-info-row:last-child { border-bottom: none; }
.he-info-row:nth-child(even) { background: var(--he-bg); }
.he-info-row:hover { background: var(--he-primary-bg); }
.he-info-label {
  padding: 20px 24px;
  font-size: 14px;
  font-weight: 600;
  color: var(--he-text);
}
.he-info-value {
  padding: 20px 24px;
  font-size: 14px;
  color: var(--he-text-secondary);
}

.he-footnote {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 20px 0 0;
  font-size: 14px;
  color: var(--he-text-muted);
}
.he-footnote svg { width: 16px; height: 16px; flex-shrink: 0; }
.he-link {
  color: var(--he-primary);
  font-weight: 500;
  text-decoration: none;
  margin: 0 2px;
}
.he-link:hover { text-decoration: underline; }

.field-hint { font-size: 12px; color: var(--he-text-muted); margin: 4px 0 0; }
.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--he-border-light);
}
.list-title { font-weight: 600; font-size: 16px; color: var(--he-text); }
.list-item-card {
  border: 1px solid var(--he-border);
  border-radius: 12px;
  padding: 16px 20px 8px;
  margin-bottom: 12px;
  background: var(--he-bg);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.list-item-card:hover {
  border-color: rgba(13, 148, 136, 0.3);
  box-shadow: 0 2px 12px rgba(13, 148, 136, 0.08);
}

.he-step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
}
.he-btn-primary {
  display: inline-flex !important;
  align-items: center;
  gap: 8px;
  padding: 12px 40px !important;
  border-radius: 12px !important;
  font-weight: 600 !important;
  box-shadow: 0 4px 14px rgba(13, 148, 136, 0.25) !important;
}
.he-btn-primary svg { width: 20px; height: 20px; }
.he-btn-secondary {
  padding: 12px 28px !important;
  border-radius: 12px !important;
}

/* Report */
.he-report-card { padding: 32px; }
.he-report-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.he-report-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--he-text);
  margin: 0 0 4px;
}
.he-report-sub { font-size: 14px; color: var(--he-text-secondary); margin: 0; }

.he-risk-banner {
  padding: 24px 28px;
  border-radius: 16px;
  margin-bottom: 24px;
}
.he-risk-banner--low { background: linear-gradient(135deg, #ecfdf5, #d1fae5); border: 1px solid #a7f3d0; }
.he-risk-banner--medium { background: linear-gradient(135deg, #fffbeb, #fef3c7); border: 1px solid #fde68a; }
.he-risk-banner--high { background: linear-gradient(135deg, #fef2f2, #fecaca); border: 1px solid #fca5a5; }
.he-risk-banner-main { display: flex; align-items: baseline; gap: 16px; margin-bottom: 8px; }
.he-risk-level { font-size: 24px; font-weight: 700; color: var(--he-text); }
.he-risk-score { font-size: 18px; font-weight: 600; color: var(--he-text-secondary); }
.he-risk-summary { font-size: 14px; color: var(--he-text-secondary); line-height: 1.6; margin: 0; }

.he-charts-row { margin-bottom: 24px; }
.he-chart-box {
  background: var(--he-bg);
  border-radius: 12px;
  border: 1px solid var(--he-border-light);
}

.he-metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 28px;
}
.he-metric-item {
  padding: 16px 20px;
  background: var(--he-bg);
  border-radius: 12px;
  border: 1px solid var(--he-border-light);
}
.he-metric-label {
  display: block;
  font-size: 12px;
  color: var(--he-text-muted);
  margin-bottom: 6px;
}
.he-metric-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--he-text);
}
.he-metric-value small { font-weight: 400; color: var(--he-text-secondary); }

.he-sub-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--he-text);
  margin: 0 0 16px;
}
.he-factor-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 28px; }
.he-factor-item {
  padding: 16px 20px;
  background: var(--he-bg);
  border-radius: 12px;
  border: 1px solid var(--he-border-light);
}
.he-factor-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.he-factor-name { font-weight: 600; color: var(--he-text); }
.he-factor-level {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 999px;
}
.he-factor-level--high { background: #fef2f2; color: #dc2626; }
.he-factor-level--medium { background: #fffbeb; color: #d97706; }
.he-factor-level--low { background: #ecfdf5; color: #059669; }
.he-factor-desc { font-size: 14px; color: var(--he-text-secondary); margin: 0; line-height: 1.5; }

.he-suggestion-list {
  list-style: none;
  padding: 0;
  margin: 0 0 28px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.he-suggestion-list li {
  position: relative;
  padding: 12px 16px 12px 36px;
  background: var(--he-primary-bg);
  border-radius: 10px;
  font-size: 14px;
  color: var(--he-text);
  line-height: 1.5;
}
.he-suggestion-list li::before {
  content: '';
  position: absolute;
  left: 14px;
  top: 18px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--he-primary);
}

.he-report-actions { margin-bottom: 16px; }
.he-disclaimer { margin-top: 8px; }

/* History */
.he-history-card { padding: 8px; }
.he-empty {
  text-align: center;
  padding: 64px 20px;
  color: var(--he-text-muted);
}
.he-empty svg { width: 48px; height: 48px; margin-bottom: 12px; opacity: 0.5; }
.he-empty p { margin: 0; font-size: 15px; }
.he-history-list { display: flex; flex-direction: column; }
.he-history-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
}
.he-history-item:hover { background: var(--he-primary-bg); }
.he-history-item--unread {
  background: #fff7ed;
  border: 1px solid #fed7aa;
}
.he-history-item--unread .he-history-time {
  font-weight: 600;
  color: #c2410c;
}
.he-history-time { flex: 1; font-size: 14px; color: var(--he-text); }
.he-history-meta { display: flex; align-items: center; gap: 12px; }
.he-history-level {
  font-size: 13px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 999px;
}
.he-history-level--low { background: #ecfdf5; color: #059669; }
.he-history-level--medium { background: #fffbeb; color: #d97706; }
.he-history-level--high { background: #fef2f2; color: #dc2626; }
.he-history-score { font-size: 14px; font-weight: 600; color: var(--he-text-secondary); }
.he-history-arrow { width: 20px; height: 20px; color: var(--he-text-muted); }

@media (max-width: 1024px) {
  .he-user-items { gap: 24px; }
  .he-divider { display: none; }
  .he-steps-card { overflow-x: auto; }
  .he-steps { min-width: 700px; }
  .he-metrics-grid { grid-template-columns: repeat(2, 1fr); }
  .he-info-header,
  .he-info-row { grid-template-columns: 1fr 1fr; }
  .he-info-header > div:nth-child(3),
  .he-info-header > div:nth-child(4) { display: none; }
}

@media (max-width: 640px) {
  .he-page-title { font-size: 22px; }
  .he-tab { padding: 12px 20px; font-size: 14px; }
  .he-user-card,
  .he-form-card,
  .he-steps-card,
  .he-report-card { padding: 20px; }
  .he-metrics-grid { grid-template-columns: 1fr; }
}

/* ── 手机端 / 老年友好样式 ── */
.he-page--mobile {
  --he-text: #1c1917;
  --he-text-secondary: #44403c;
  --he-text-muted: #57534e;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
.he-page--mobile .animate-fade-in {
  animation: none;
  opacity: 1;
  transform: none;
}

/* 标签页：等宽、大触控区 */
.he-tabs--mobile .he-tab {
  flex: 1;
  text-align: center;
  padding: 18px 12px;
  font-size: 17px;
  font-weight: 600;
  min-height: 52px;
}
.he-tabs--mobile .he-tab-dot {
  top: 10px;
  right: calc(50% - 36px);
  width: 10px;
  height: 10px;
}
.he-tabs--mobile .he-tab-indicator {
  height: 4px;
}

/* 手机端进度条 */
.he-mobile-progress {
  padding: 20px 18px;
  border-left: 4px solid var(--he-primary);
}
.he-mobile-progress__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.he-mobile-progress__label {
  font-size: 15px;
  font-weight: 600;
  color: var(--he-text-secondary);
}
.he-mobile-progress__percent {
  font-size: 17px;
  font-weight: 700;
  color: var(--he-primary);
  font-variant-numeric: tabular-nums;
}
.he-mobile-progress__track {
  height: 10px;
  background: #e7e5e4;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 16px;
}
.he-mobile-progress__fill {
  height: 100%;
  background: linear-gradient(90deg, var(--he-primary-light), var(--he-primary));
  border-radius: 999px;
  transition: width 0.35s ease;
}
.he-mobile-progress__title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: var(--he-text);
  line-height: 1.35;
}
.he-mobile-progress__desc {
  margin: 0 0 14px;
  font-size: 16px;
  color: var(--he-text-secondary);
  line-height: 1.6;
}
.he-mobile-progress__dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.he-mobile-progress__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d6d3d1;
  transition: background 0.2s, transform 0.2s;
}
.he-mobile-progress__dot.active {
  background: var(--he-primary);
  transform: scale(1.3);
}
.he-mobile-progress__dot.completed {
  background: #99f6e4;
}

.he-step-hint {
  margin: 0 0 18px;
  padding: 14px 16px;
  font-size: 15px;
  line-height: 1.65;
  color: var(--he-text-secondary);
  background: #fafaf9;
  border-radius: 12px;
  border-left: 3px solid var(--he-primary-light);
}

/* 用户概览卡片 */
.he-page--mobile .he-user-card {
  padding: 18px;
}
.he-page--mobile .he-user-row {
  flex-direction: column;
  align-items: stretch;
  gap: 16px;
}
.he-page--mobile .he-user-items {
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
}
.he-page--mobile .he-user-item {
  padding: 14px 16px;
  background: var(--he-primary-bg);
  border-radius: 14px;
  gap: 14px;
}
.he-page--mobile .he-user-icon {
  width: 44px;
  height: 44px;
}
.he-page--mobile .he-user-label {
  font-size: 13px;
  text-transform: none;
  letter-spacing: 0;
  color: var(--he-text-muted);
}
.he-page--mobile .he-user-value {
  font-size: 20px;
}
.he-page--mobile .he-user-tip {
  font-size: 15px;
  line-height: 1.6;
  padding: 12px 14px;
  border-radius: 12px;
}
.he-page--mobile .he-user-tip svg {
  width: 20px;
  height: 20px;
}

/* 表单：大字号、大触控区 */
.he-form-card--mobile {
  padding: 18px 16px 0;
  margin-bottom: 0;
}
.he-page--mobile .step-form--mobile {
  min-height: auto;
}
.he-page--mobile .step-form--mobile :deep(.el-form-item) {
  margin-bottom: 22px;
}
.he-page--mobile .step-form--mobile :deep(.el-col) {
  max-width: 100%;
  flex: 0 0 100%;
}
.he-page--mobile .step-form--mobile :deep(.el-form-item__label) {
  font-size: 17px;
  font-weight: 600;
  color: var(--he-text);
  line-height: 1.5;
  padding-bottom: 8px;
}
.he-page--mobile .step-form--mobile :deep(.el-input-number),
.he-page--mobile .step-form--mobile :deep(.el-select),
.he-page--mobile .step-form--mobile :deep(.el-date-editor) {
  width: 100%;
}
.he-page--mobile .step-form--mobile :deep(.el-input-number .el-input__inner),
.he-page--mobile .step-form--mobile :deep(.el-select .el-select__wrapper),
.he-page--mobile .step-form--mobile :deep(.el-input__inner),
.he-page--mobile .step-form--mobile :deep(.el-textarea__inner) {
  font-size: 17px;
  min-height: 48px;
}
.he-page--mobile .step-form--mobile :deep(.el-input-number__decrease),
.he-page--mobile .step-form--mobile :deep(.el-input-number__increase) {
  width: 44px;
  font-size: 18px;
}

/* 单选卡片式按钮，便于点击 */
.he-page--mobile .he-radio-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.he-page--mobile .he-radio-cards :deep(.el-radio) {
  display: flex;
  align-items: center;
  height: auto;
  min-height: 52px;
  margin-right: 0;
  padding: 14px 18px;
  border: 2px solid var(--he-border);
  border-radius: 14px;
  background: #fff;
  transition: border-color 0.2s, background 0.2s;
}
.he-page--mobile .he-radio-cards :deep(.el-radio.is-checked) {
  border-color: var(--he-primary);
  background: var(--he-primary-bg);
}
.he-page--mobile .he-radio-cards :deep(.el-radio__label) {
  font-size: 17px;
  font-weight: 500;
  color: var(--he-text);
  padding-left: 10px;
}
.he-page--mobile .he-radio-cards :deep(.el-radio__inner) {
  width: 22px;
  height: 22px;
  border-width: 2px;
}
.he-page--mobile .he-radio-cards :deep(.el-radio__inner::after) {
  width: 10px;
  height: 10px;
}

/* 信息表格 */
.he-page--mobile .he-info-banner {
  font-size: 16px;
  padding: 16px;
  border-radius: 14px;
}
.he-page--mobile .he-info-label,
.he-page--mobile .he-info-value {
  padding: 16px 18px;
  font-size: 16px;
}
.he-page--mobile .he-info-label {
  font-weight: 600;
  color: var(--he-text);
}
.he-page--mobile .he-info-value {
  color: var(--he-text-secondary);
}
.he-page--mobile .he-footnote {
  font-size: 15px;
  line-height: 1.65;
  flex-wrap: wrap;
}

/* 列表卡片 */
.he-page--mobile .list-title {
  font-size: 18px;
}
.he-page--mobile .list-toolbar {
  margin-bottom: 18px;
}
.he-page--mobile .list-toolbar :deep(.el-button) {
  font-size: 16px;
  min-height: 44px;
}
.he-page--mobile .list-item-card {
  padding: 16px;
  border-radius: 14px;
  margin-bottom: 14px;
}
.he-page--mobile .list-item-card :deep(.el-button) {
  font-size: 16px;
  min-height: 44px;
  margin-top: 4px;
}

/* 底部固定操作栏 */
.he-step-actions--sticky {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(var(--bottom-nav-height, 64px) + env(safe-area-inset-bottom, 0));
  z-index: 20;
  flex-direction: column-reverse;
  gap: 10px;
  margin-top: 0;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid var(--he-border);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(8px);
}
.he-step-actions--sticky .he-btn-primary,
.he-step-actions--sticky .he-btn-secondary {
  width: 100%;
  min-height: 52px !important;
  font-size: 17px !important;
  border-radius: 14px !important;
  justify-content: center;
}
.he-step-actions-spacer {
  height: calc(120px + var(--bottom-nav-height, 64px));
}

/* 新报告横幅 */
.he-page--mobile .he-new-report-banner {
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
  padding: 18px;
}
.he-page--mobile .he-new-report-banner__title {
  font-size: 17px;
}
.he-page--mobile .he-new-report-banner__meta {
  font-size: 15px;
}
.he-page--mobile .he-new-report-banner :deep(.el-button) {
  width: 100%;
  min-height: 48px;
  font-size: 16px;
}

/* 评估报告 */
.he-page--mobile .he-report-card {
  padding: 18px 16px;
}
.he-page--mobile .he-report-title {
  font-size: 22px;
}
.he-page--mobile .he-report-sub {
  font-size: 15px;
}
.he-page--mobile .he-risk-level {
  font-size: 28px;
}
.he-page--mobile .he-risk-score {
  font-size: 20px;
}
.he-page--mobile .he-risk-summary {
  font-size: 16px;
  line-height: 1.75;
}
.he-page--mobile .he-metric-label {
  font-size: 14px;
}
.he-page--mobile .he-metric-value {
  font-size: 18px;
}
.he-page--mobile .he-sub-title {
  font-size: 18px;
  margin-bottom: 14px;
}
.he-page--mobile .he-factor-name {
  font-size: 17px;
}
.he-page--mobile .he-factor-desc {
  font-size: 16px;
  line-height: 1.65;
}
.he-page--mobile .he-factor-level {
  font-size: 14px;
  padding: 4px 12px;
}
.he-page--mobile .he-suggestion-list li {
  font-size: 16px;
  line-height: 1.7;
  padding: 14px 16px 14px 38px;
}
.he-page--mobile .he-report-actions :deep(.el-button) {
  width: 100%;
  min-height: 52px;
  font-size: 17px;
}

/* 历史记录 */
.he-page--mobile .he-history-item {
  padding: 18px 16px;
  min-height: 72px;
  border-radius: 14px;
  margin-bottom: 8px;
  border: 1px solid var(--he-border-light);
}
.he-page--mobile .he-history-time {
  font-size: 16px;
}
.he-page--mobile .he-history-level {
  font-size: 14px;
  padding: 6px 14px;
}
.he-page--mobile .he-history-score {
  font-size: 16px;
}
.he-page--mobile .he-empty {
  padding: 48px 20px;
}
.he-page--mobile .he-empty p {
  font-size: 17px;
}
</style>
