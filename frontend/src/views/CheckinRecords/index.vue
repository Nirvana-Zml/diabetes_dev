<template>
  <SiteLayout title="生活打卡" full-bleed>

    <div class="checkin-page" :class="{ 'checkin-page--mobile-ref': isMobile }">
      <!-- 待完成打卡提醒横幅 -->
      <div v-if="bannerReminders.length" class="reminder-banner-wrap">
        <el-alert
          v-for="item in bannerReminders"
          :key="item.log_id || item.logId"
          type="warning"
          :closable="true"
          show-icon
          class="reminder-banner"
          @close="dismissBanner(item)"
        >
          <template #title>{{ item.title || item.checkin_type_label + '提醒' }}</template>
          <div class="reminder-banner__body">
            <span>{{ item.body }}</span>
            <el-button type="primary" size="small" @click="goReminderTab(item)">立即打卡</el-button>
          </div>
        </el-alert>
      </div>

      <!-- 统计卡片：手机三列 / 桌面四列 -->
      <section v-if="isMobile" class="stats-grid stats-grid--mobile">
        <div class="stat-card stat-card--mobile-ref">
          <div class="stat-card__mobile-head">
            <div class="stat-icon stat-icon--primary">
              <el-icon :size="14"><DocumentChecked /></el-icon>
            </div>
            <span class="stat-badge stat-badge--primary">+{{ stats.week_checkins }} 本周</span>
          </div>
          <p class="stat-value">{{ stats.total_checkins }}</p>
          <p class="stat-label">总卡数量</p>
        </div>
        <div class="stat-card stat-card--mobile-ref">
          <div class="stat-card__mobile-head">
            <div class="stat-icon stat-icon--accent">
              <el-icon :size="14"><Star /></el-icon>
            </div>
            <span class="stat-badge stat-badge--accent">+{{ todayStatus.today_points || 0 }} 今日</span>
          </div>
          <p class="stat-value">{{ stats.total_points.toLocaleString() }}</p>
          <p class="stat-label">总积分</p>
        </div>
        <div class="stat-card stat-card--mobile-ref">
          <div class="stat-card__mobile-head">
            <div class="stat-icon stat-icon--orange">
              <el-icon :size="14"><Sunny /></el-icon>
            </div>
            <span v-if="stats.streak_days >= 7" class="stat-badge stat-badge--orange">火热</span>
          </div>
          <p class="stat-value">{{ stats.streak_days }}</p>
          <p class="stat-label">连续打卡</p>
        </div>
      </section>
      <section v-else class="stats-grid">
        <div class="stat-card">
          <div class="stat-card__top">
            <div>
              <div class="stat-icon stat-icon--primary">
                <el-icon :size="18"><DocumentChecked /></el-icon>
              </div>
              <p class="stat-value">{{ stats.total_checkins }}</p>
              <p class="stat-label">总卡数量</p>
            </div>
            <span class="stat-badge stat-badge--primary">+{{ stats.week_checkins }} 本周</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__top">
            <div>
              <div class="stat-icon stat-icon--accent">
                <el-icon :size="18"><Star /></el-icon>
              </div>
              <p class="stat-value">{{ stats.total_points.toLocaleString() }}</p>
              <p class="stat-label">总积分</p>
            </div>
            <span class="stat-badge stat-badge--accent">+{{ todayStatus.today_points || 0 }} 今日</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__top">
            <div>
              <div class="stat-icon stat-icon--orange">
                <el-icon :size="18"><Sunny /></el-icon>
              </div>
              <p class="stat-value">{{ stats.streak_days }}</p>
              <p class="stat-label">连续打卡</p>
            </div>
            <span v-if="stats.streak_days >= 7" class="stat-badge stat-badge--orange">火热</span>
          </div>
        </div>
        <div class="stat-card stat-card--progress">
          <div class="progress-ring-wrap">
            <svg class="progress-ring" viewBox="0 0 64 64">
              <circle cx="32" cy="32" r="28" fill="none" stroke="#ccfbf1" stroke-width="6" />
              <circle
                cx="32" cy="32" r="28" fill="none" stroke="#0d9488" stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="progressCircumference"
                :stroke-dashoffset="progressRingOffset"
                class="progress-ring-circle"
              />
            </svg>
            <span class="progress-ring-text">{{ todayProgress }}%</span>
          </div>
          <div>
            <p class="progress-title">今日完成</p>
            <p class="progress-sub">{{ todayTasksSummary.done }}/{{ todayTasksSummary.total }} 项任务</p>
          </div>
        </div>
      </section>

      <!-- 手机端今日进度 -->
      <div v-if="isMobile" class="mobile-today-progress">
        <div class="mobile-today-progress__info">
          <div class="mobile-today-progress__ring">
            <svg viewBox="0 0 40 40" aria-hidden="true">
              <circle cx="20" cy="20" r="16" fill="none" stroke="#ccfbf1" stroke-width="4" />
              <circle
                cx="20" cy="20" r="16" fill="none" stroke="#0d9488" stroke-width="4"
                stroke-linecap="round"
                :stroke-dasharray="mobileRingCircumference"
                :stroke-dashoffset="mobileRingOffset"
                transform="rotate(-90 20 20)"
              />
            </svg>
            <span>{{ todayProgress }}%</span>
          </div>
          <div class="mobile-today-progress__text">
            <strong>今日打卡进度</strong>
            <span>{{ todayTasksSummary.done }}/{{ todayTasksSummary.total }} 项任务已完成</span>
          </div>
        </div>
        <div class="mobile-today-progress__bar">
          <div class="mobile-today-progress__fill" :style="{ width: `${todayProgress}%` }" />
        </div>
      </div>

      <!-- 手机端快捷入口 -->
      <nav class="mobile-checkin-nav" aria-label="打卡相关功能">
        <button type="button" class="mobile-nav-chip mobile-nav-chip--analysis" @click="$router.push('/checkin-analysis')">
          <el-icon><DataAnalysis /></el-icon>
          <span>打卡分析</span>
        </button>
        <button type="button" class="mobile-nav-chip mobile-nav-chip--reminder" @click="$router.push('/checkin-reminder-settings')">
          <el-icon><Bell /></el-icon>
          <span>提醒设置</span>
        </button>
      </nav>

      <div class="page-body">
        <div class="workspace-layout">
          <aside class="workspace-aside">
            <section class="analysis-banner" @click="$router.push('/checkin-analysis')">
              <div class="banner-left">
                <div class="banner-icon"><el-icon :size="24"><DataAnalysis /></el-icon></div>
                <div class="banner-text">
                  <strong>打卡统计分析</strong>
                  <span>查看周/月趋势与 AI 行为总结</span>
                </div>
              </div>
              <div class="banner-right">
                <span>查看详情</span>
                <div class="banner-arrow"><el-icon><ArrowRight /></el-icon></div>
              </div>
            </section>

            <details class="achievement-collapse" open>
              <summary class="achievement-collapse__summary" @click="onAchievementSummaryClick">
                <h3>成就墙</h3>
                <div class="ach-count">
                  <span class="ach-count-done">{{ unlockedCount }}</span>
                  <span class="ach-count-sep">/</span>
                  <span class="ach-count-total">{{ achievements.length }}</span>
                </div>
              </summary>
              <div class="achievement-grid">
                <div
                  v-for="(a, idx) in achievements"
                  :key="a.id"
                  class="achievement-card"
                  :class="{ unlocked: a.unlocked, [`tone-${idx % 3}`]: a.unlocked }"
                >
                  <div class="achievement-icon">
                    <span>{{ a.unlocked ? '🏅' : '🔒' }}</span>
                  </div>
                  <h4 class="ach-name">{{ a.name }}</h4>
                  <p class="ach-desc">{{ a.desc }}</p>
                  <div class="ach-status">
                    <span>{{ a.unlocked ? '已解锁' : '未解锁' }}</span>
                  </div>
                </div>
              </div>
            </details>
          </aside>

          <!-- 右侧：日期栏 + 打卡操作区 -->
          <div class="checkin-workspace panel-card">
            <section class="date-section date-section--embedded">
              <button type="button" class="date-nav-btn" aria-label="上一天" @click="changeDate(-1)">
                <el-icon><ArrowLeft /></el-icon>
              </button>
              <div class="date-center">
                <span v-if="isToday" class="date-badge">今天</span>
                <span class="date-main">{{ dateDisplay.full }}</span>
                <span class="date-weekday">{{ dateDisplay.weekday }}</span>
              </div>
              <button type="button" class="date-nav-btn" :disabled="isToday" aria-label="下一天" @click="changeDate(1)">
                <el-icon><ArrowRight /></el-icon>
              </button>
            </section>

            <div class="main-layout">
              <aside class="layout-sidebar">
                <div class="checkin-inner-panel sidebar-panel">
                  <div class="sidebar-block">
                    <h3 class="panel-label">打卡分类</h3>
                    <nav class="category-nav">
                      <button
                        v-for="tab in mainTabs"
                        :key="tab.key"
                        type="button"
                        class="category-nav-btn"
                        :class="{ active: activeTab === tab.key }"
                        @click="switchTab(tab.key)"
                      >
                        <el-icon :size="20"><component :is="tab.icon" /></el-icon>
                        <span>{{ tab.label }}</span>
                      </button>
                    </nav>
                  </div>

                  <div v-show="activeTab === 'food'" class="sidebar-block">
                    <h3 class="panel-label">餐次选择</h3>
                    <div class="meal-chips">
                      <button
                        v-for="m in mealPeriods"
                        :key="m.value"
                        type="button"
                        class="meal-chip"
                        :class="{ active: mealPeriod === m.value }"
                        @click="mealPeriod = m.value"
                      >{{ m.label }}</button>
                    </div>
                  </div>

                  <div v-show="activeTab === 'food'" class="sidebar-block">
                    <h3 class="panel-label">快捷操作</h3>
                    <div class="quick-actions">
                      <button
                        type="button"
                        class="quick-action-btn"
                        :class="{ active: foodMode === 'custom' }"
                        @click="foodMode = 'custom'"
                      >
                        <el-icon><Plus /></el-icon>
                        <span>自定义食物</span>
                      </button>
                      <button
                        type="button"
                        class="quick-action-btn quick-action-btn--muted"
                        :class="{ active: foodMode === 'preset' }"
                        @click="foodMode = 'preset'"
                      >
                        <el-icon><Search /></el-icon>
                        <span>选择食物</span>
                      </button>
                    </div>
                  </div>
                </div>
              </aside>

              <div class="layout-main">
        <!-- ===== 食物打卡 ===== -->
        <section v-show="activeTab === 'food'" class="module-panel">
          <template v-if="foodMode === 'preset'">
            <h3
              v-if="isMobile && foodCategories.length"
              class="panel-label food-section-label"
            >食物分类</h3>
            <div v-if="foodCategories.length" class="category-tabs">
              <button
                v-for="c in foodCategories"
                :key="c.category_id"
                type="button"
                class="cat-tab"
                :class="{ active: selectedCategoryId === c.category_id }"
                @click="selectCategory(c.category_id)"
              >{{ c.category_name }}</button>
            </div>
            <div v-loading="presetsLoading" class="food-grid">
              <div v-for="f in foodPresets" :key="f.food_id" class="food-item" @click="openFoodPreset(f)">
                <div class="food-img-wrap">
                  <img v-if="f.image_url" :src="f.image_url" :alt="f.food_name" @error="onImgError" />
                  <span v-else class="food-placeholder">🍽</span>
                  <span v-if="isMobile" class="food-add-badge" aria-hidden="true">
                    <el-icon :size="14"><Plus /></el-icon>
                  </span>
                </div>
                <span class="food-name">{{ f.food_name }}</span>
                <span class="food-kcal">{{ f.calories_per_gram }} kcal/g</span>
              </div>
            </div>
            <el-empty v-if="!presetsLoading && !foodPresets.length" description="暂无预设食物" />
          </template>

          <template v-else>
            <div class="custom-form">
              <div class="upload-box" @click="triggerFoodUpload">
                <img v-if="customFood.image_url" :src="customFood.image_url" class="upload-preview" alt="" />
                <template v-else>
                  <el-icon :size="28"><Camera /></el-icon>
                  <span>上传图片</span>
                </template>
                <div v-if="customFood.uploading" class="upload-mask">上传中…</div>
              </div>
              <input ref="foodFileInput" type="file" accept="image/*" hidden @change="onFoodFileChange" />
              <el-form label-position="top" class="compact-form">
                <el-form-item label="食物分类" required>
                  <el-select v-model="customFood.category_id" placeholder="选择分类" style="width:100%">
                    <el-option v-for="c in foodCategories" :key="c.category_id" :label="c.category_name" :value="c.category_id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="食物名称" required>
                  <el-input v-model="customFood.name" placeholder="如：全麦面包" />
                </el-form-item>
                <el-form-item label="是否液体">
                  <el-switch v-model="customFood.is_liquid" />
                </el-form-item>
                <el-form-item label="每克卡路里 (kcal/g)" required>
                  <el-input-number v-model="customFood.calories_per_gram" :min="0.01" :step="0.01" :precision="2" style="width:100%" />
                </el-form-item>
                <el-form-item v-if="customFood.is_liquid" label="ml→g 换算系数">
                  <el-input-number v-model="customFood.ml_to_g_ratio" :min="0.01" :step="0.01" :precision="2" style="width:100%" />
                </el-form-item>
                <el-form-item label="食用量" required>
                  <div class="amount-row">
                    <el-input-number v-model="customFood.input_amount" :min="0.01" :precision="1" style="flex:1" />
                    <el-select v-if="customFood.is_liquid" v-model="customFood.input_unit" style="width:88px">
                      <el-option label="g" :value="1" />
                      <el-option label="ml" :value="2" />
                    </el-select>
                    <span v-else class="unit-tag">g</span>
                  </div>
                </el-form-item>
                <div class="preview-kcal">总热量：{{ calcFoodCalories(customFood) }} kcal</div>
                <el-button type="primary" round class="submit-btn" :loading="submitting" @click="submitCustomFood">确认打卡</el-button>
              </el-form>
            </div>
          </template>

          <div class="records-section">
            <div class="records-head">
              <h4 class="records-title">当日食物记录</h4>
              <div class="daily-kcal-summary">
                <span class="daily-kcal-label">已摄入</span>
                <strong class="daily-kcal-value">{{ foodDailyTotalCalories }}</strong>
                <span class="daily-kcal-unit">千卡</span>
              </div>
            </div>
            <div class="calorie-progress">
              <div class="calorie-progress-head">
                <span>目标 1,800 千卡</span>
                <span class="calorie-progress-pct">{{ foodCaloriePercent }}%</span>
              </div>
              <div class="calorie-progress-bar">
                <div class="calorie-progress-fill" :style="{ width: foodCaloriePercent + '%' }" />
              </div>
            </div>
            <div v-if="foodRecords.length" class="record-list">
              <div v-for="r in foodRecords" :key="r.checkin_id" class="record-card food-record-card">
                <div class="record-thumb">
                  <img v-if="r.image_url" :src="r.image_url" alt="" @error="onImgError" />
                  <span v-else>🍽</span>
                </div>
                <div class="record-body">
                  <div class="record-title">{{ r.food_name }}</div>
                  <div class="record-meta">{{ r.meal_period_label }} · {{ formatFoodAmount(r) }}</div>
                </div>
                <div class="record-right">
                  <strong>{{ r.total_calories }} 千卡</strong>
                  <span class="record-status">已打卡</span>
                </div>
              </div>
            </div>
            <div v-else class="records-empty">
              <div class="records-empty-icon"><el-icon :size="28"><Dish /></el-icon></div>
              <p>暂无打卡记录</p>
              <span>点击上方食物添加记录</span>
            </div>
          </div>
        </section>

        <!-- ===== 用药打卡 ===== -->
        <section v-show="activeTab === 'medication'" class="module-panel">
          <div class="mode-switch med">
            <button type="button" :class="{ active: medMode === 'preset' }" @click="medMode = 'preset'">选择药品</button>
            <button type="button" :class="{ active: medMode === 'custom' }" @click="medMode = 'custom'">自定义药品</button>
          </div>

          <template v-if="medMode === 'preset'">
            <div v-loading="presetsLoading" class="drug-list">
              <div v-for="d in medicationPresets" :key="d.drug_id" class="drug-item" @click="openMedPreset(d)">
                <div class="drug-thumb">
                  <img v-if="d.image_url" :src="d.image_url" :alt="d.drug_name" @error="onImgError" />
                  <span v-else>💊</span>
                </div>
                <span>{{ d.drug_name }}</span>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="custom-form">
              <div class="upload-box med" @click="triggerMedUpload">
                <img v-if="customMed.image_url" :src="customMed.image_url" class="upload-preview" alt="" />
                <template v-else><el-icon :size="28"><Camera /></el-icon><span>上传药品图片</span></template>
                <div v-if="customMed.uploading" class="upload-mask">上传中…</div>
              </div>
              <input ref="medFileInput" type="file" accept="image/*" hidden @change="onMedFileChange" />
              <el-form label-position="top" class="compact-form">
                <el-form-item label="药品名称" required><el-input v-model="customMed.name" /></el-form-item>
                <el-form-item label="剂量" required><el-input v-model="customMed.dosage" placeholder="如：0.5g" /></el-form-item>
                <el-form-item label="是否已服"><el-switch v-model="customMed.taken" /></el-form-item>
                <el-button type="primary" round class="submit-btn" :loading="submitting" @click="submitCustomMed">确认打卡</el-button>
              </el-form>
            </div>
          </template>

          <div class="records-section">
            <h4 class="records-title">当日用药记录</h4>
            <div v-if="medicationRecords.length" class="record-list">
              <div v-for="r in medicationRecords" :key="r.checkin_id" class="record-card med-card">
                <div class="record-thumb">
                  <img v-if="r.image_url" :src="r.image_url" alt="" @error="onImgError" />
                  <span v-else>💊</span>
                </div>
                <div class="record-body">
                  <div class="record-title">{{ r.drug_name }}</div>
                  <div class="record-meta">{{ r.dosage }}</div>
                </div>
                <div class="record-right">
                  <el-tag :type="r.taken ? 'success' : 'info'" size="small">{{ r.taken ? '已服' : '未服' }}</el-tag>
                  <span>{{ formatTime(r.record_time) }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无打卡记录" :image-size="64" />
          </div>
        </section>

        <!-- ===== 运动打卡 ===== -->
        <section v-show="activeTab === 'exercise'" class="module-panel">
          <div class="mode-switch ex">
            <button type="button" :class="{ active: exMode === 'preset' }" @click="exMode = 'preset'">选择运动</button>
            <button type="button" :class="{ active: exMode === 'custom' }" @click="exMode = 'custom'">自定义运动</button>
          </div>

          <template v-if="exMode === 'preset'">
            <div v-loading="presetsLoading" class="exercise-list">
              <div v-for="e in exercisePresets" :key="e.exercise_id" class="exercise-item" @click="openExPreset(e)">
                <span class="ex-name">{{ e.exercise_name }}</span>
                <span class="ex-kcal">{{ e.calories_per_minute }} kcal/min</span>
              </div>
            </div>
          </template>

          <template v-else>
            <el-form label-position="top" class="compact-form">
              <el-form-item label="运动项目名称" required><el-input v-model="customEx.name" /></el-form-item>
              <el-form-item label="每分钟消耗 (kcal/min)" required>
                <el-input-number v-model="customEx.calories_per_minute" :min="0.01" :step="0.1" :precision="2" style="width:100%" />
              </el-form-item>
              <el-form-item label="运动分钟数" required>
                <el-input-number v-model="customEx.duration" :min="1" :max="300" style="width:100%" />
              </el-form-item>
              <div class="preview-kcal">总消耗：{{ calcExCalories(customEx.calories_per_minute, customEx.duration) }} kcal</div>
              <el-button type="primary" round class="submit-btn" :loading="submitting" @click="submitCustomEx">确认打卡</el-button>
            </el-form>
          </template>

          <div class="records-section">
            <h4 class="records-title">当日运动记录</h4>
            <div v-if="exerciseRecords.length" class="record-list">
              <div v-for="r in exerciseRecords" :key="r.checkin_id" class="record-card ex-card">
                <div class="record-body full">
                  <div class="record-title">{{ r.exercise_name }}</div>
                  <div class="record-meta">{{ r.duration_minutes }} 分钟</div>
                </div>
                <div class="record-right">
                  <strong>{{ r.calories_burned }} kcal</strong>
                  <span>{{ formatTime(r.record_time) }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无打卡记录" :image-size="64" />
          </div>
        </section>

        <!-- ===== 血糖打卡 ===== -->
        <section v-show="activeTab === 'glucose'" class="module-panel glucose-panel">
          <div class="glucose-input-card">
            <h4 class="panel-title">记录血糖</h4>
            <el-form label-position="top" class="compact-form">
              <el-form-item label="血糖值 (mmol/L)" required>
                <el-input-number v-model="glucoseForm.value" :min="2" :max="30" :step="0.1" style="width:100%" />
              </el-form-item>
              <el-form-item label="测量时段">
                <el-select v-model="glucoseForm.context" style="width:100%">
                  <el-option label="空腹" :value="1" />
                  <el-option label="餐后2h" :value="2" />
                  <el-option label="睡前" :value="3" />
                  <el-option label="随机" :value="4" />
                </el-select>
              </el-form-item>
            </el-form>
            <el-button type="primary" round :loading="submitting" class="glucose-submit" @click="submitGlucose">
              保存记录
            </el-button>
          </div>

          <div class="glucose-trend-section">
            <div class="trend-head">
              <h4 class="panel-title">血糖趋势</h4>
              <el-radio-group v-model="glucoseTrendDays" size="small">
                <el-radio-button :value="7">7天</el-radio-button>
                <el-radio-button :value="14">14天</el-radio-button>
                <el-radio-button :value="30">30天</el-radio-button>
              </el-radio-group>
            </div>
            <div v-if="glucoseSummary.count" class="glucose-stats">
              <div class="g-stat">
                <span class="g-stat-num">{{ glucoseSummary.count }}</span>
                <span class="g-stat-lbl">记录次数</span>
              </div>
              <div class="g-stat">
                <span class="g-stat-num">{{ glucoseSummary.avg ?? '-' }}</span>
                <span class="g-stat-lbl">平均 mmol/L</span>
              </div>
              <div class="g-stat">
                <span class="g-stat-num">{{ glucoseSummary.max ?? '-' }}</span>
                <span class="g-stat-lbl">最高</span>
              </div>
              <div class="g-stat">
                <span class="g-stat-num">{{ glucoseSummary.min ?? '-' }}</span>
                <span class="g-stat-lbl">最低</span>
              </div>
            </div>
            <div ref="glucoseChartRef" class="glucose-chart" />
            <p class="chart-hint">灰色区域为参考正常范围 3.9–6.1 mmol/L（空腹）</p>
          </div>

          <div class="records-section">
            <h4 class="records-title">{{ dateDisplay.main }} 血糖记录</h4>
            <div v-if="glucoseRecords.length" class="record-list">
              <div v-for="r in glucoseRecords" :key="r.checkin_id" class="record-card glucose-card">
                <div class="record-body">
                  <div class="glucose-value-row">
                    <strong class="glucose-value">{{ r.glucose_value }}</strong>
                    <span class="glucose-unit">mmol/L</span>
                    <span class="glucose-status" :class="'status-' + r.status">{{ glucoseStatusLabel(r.status) }}</span>
                  </div>
                  <div class="record-meta">{{ r.measure_context_label }}</div>
                </div>
                <div class="record-right">
                  <span>{{ formatTime(r.record_time) }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="当日暂无血糖记录" :image-size="64" />
          </div>
        </section>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 预设食物弹窗 -->
    <el-dialog v-model="foodDialogVisible" :title="foodDialogFood?.food_name" width="92%" style="max-width:420px" destroy-on-close>
      <div v-if="foodDialogFood" class="dialog-preset">
        <div class="dialog-img">
          <img v-if="foodDialogFood.image_url" :src="foodDialogFood.image_url" alt="" @error="onImgError" />
          <span v-else>🍽</span>
        </div>
        <p class="dialog-meta">{{ foodDialogFood.calories_per_gram }} kcal/g</p>
        <el-form label-position="top">
          <el-form-item label="食用量" required>
            <div class="amount-row">
              <el-input-number v-model="foodDialogAmount" :min="0.01" :precision="1" style="flex:1" />
              <el-select v-if="foodDialogFood.is_liquid" v-model="foodDialogUnit" style="width:88px">
                <el-option label="g" :value="1" />
                <el-option label="ml" :value="2" />
              </el-select>
              <span v-else class="unit-tag">g</span>
            </div>
          </el-form-item>
          <div class="preview-kcal">总热量：{{ calcPresetFoodCalories() }} kcal</div>
        </el-form>
      </div>
      <template #footer>
        <el-button round @click="foodDialogVisible = false">取消</el-button>
        <el-button type="primary" round :loading="submitting" @click="submitFoodPreset">确认打卡</el-button>
      </template>
    </el-dialog>

    <!-- 预设用药弹窗 -->
    <el-dialog v-model="medDialogVisible" :title="medDialogDrug?.drug_name" width="92%" style="max-width:420px" destroy-on-close>
      <div v-if="medDialogDrug" class="dialog-preset">
        <div class="dialog-img">
          <img v-if="medDialogDrug.image_url" :src="medDialogDrug.image_url" alt="" @error="onImgError" />
          <span v-else>💊</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="剂量" required><el-input v-model="medDialogDosage" placeholder="如：0.5g" /></el-form-item>
          <el-form-item label="是否已服"><el-switch v-model="medDialogTaken" /></el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button round @click="medDialogVisible = false">取消</el-button>
        <el-button type="primary" round :loading="submitting" @click="submitMedPreset">确认打卡</el-button>
      </template>
    </el-dialog>

    <!-- 预设运动弹窗 -->
    <el-dialog v-model="exDialogVisible" :title="exDialogItem?.exercise_name" width="92%" style="max-width:420px" destroy-on-close>
      <div v-if="exDialogItem" class="dialog-preset">
        <p class="dialog-meta">{{ exDialogItem.calories_per_minute }} kcal/min</p>
        <el-form label-position="top">
          <el-form-item label="运动分钟数" required>
            <el-input-number v-model="exDialogDuration" :min="1" :max="300" style="width:100%" />
          </el-form-item>
          <div class="preview-kcal">总消耗：{{ calcExCalories(exDialogItem.calories_per_minute, exDialogDuration) }} kcal</div>
        </el-form>
      </div>
      <template #footer>
        <el-button round @click="exDialogVisible = false">取消</el-button>
        <el-button type="primary" round :loading="submitting" @click="submitExPreset">确认打卡</el-button>
      </template>
    </el-dialog>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  ArrowLeft, ArrowRight, Dish, FirstAidKit, Odometer, TrendCharts,
  DataAnalysis, Camera, DocumentChecked, Star, Sunny, Plus, Search, Bell,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import { useIsMobile } from '@/composables/useBreakpoints'
import {
  getTodayStatus, getCheckinStats, getAchievements,
  getFoodCategories, getFoodPresets, createFoodCheckin, getFoodRecords, uploadCheckinImage,
  getMedicationPresets, createMedicationCheckin, getMedicationRecords,
  getExercisePresets, createExerciseCheckin, getExerciseRecords,
  createGlucoseCheckin, getGlucoseRecords, getGlucoseHistory,
} from '@/api/checkin'
import { pendingReminders, useCheckinReminder } from '@/composables/useCheckinReminder'
import { ackReminder, clickReminder } from '@/api/checkinReminder'

const route = useRoute()
const isMobile = useIsMobile()
const { refresh: refreshReminders } = useCheckinReminder()

function onAchievementSummaryClick(e) {
  if (!isMobile.value) {
    e.preventDefault()
    return
  }
}

const bannerReminders = computed(() => pendingReminders.value || [])

async function goReminderTab(item) {
  const tab = item.tab || 'food'
  const logId = item.log_id || item.logId
  if (logId) {
    try { await clickReminder(logId) } catch { /* ignore */ }
  }
  switchTab(tab)
}

function dismissBanner(item) {
  const logId = item.log_id || item.logId
  if (logId) ackReminder(logId).catch(() => {})
}

const GLUCOSE_STATUS_LABELS = {
  low: '偏低',
  normal: '正常',
  elevated: '偏高',
  high: '过高',
  unknown: '-',
}

const mealPeriods = [
  { value: 1, label: '早餐' }, { value: 2, label: '午餐' }, { value: 3, label: '晚餐' },
  { value: 4, label: '上午加餐' }, { value: 5, label: '下午加餐' }, { value: 6, label: '晚上加餐' },
]

const mainTabs = [
  { key: 'food', label: '食物', icon: Dish, color: '#0d9488' },
  { key: 'medication', label: '用药', icon: FirstAidKit, color: '#6366f1' },
  { key: 'exercise', label: '运动', icon: Odometer, color: '#f59e0b' },
  { key: 'glucose', label: '血糖', icon: TrendCharts, color: '#ef4444' },
]

const checkinDate = ref(dayjs().format('YYYY-MM-DD'))
const activeTab = ref('food')
const mealPeriod = ref(1)
const foodMode = ref('preset')
const medMode = ref('preset')
const exMode = ref('preset')

const stats = ref({ total_points: 0, streak_days: 0, total_checkins: 0, week_checkins: 0 })
const todayStatus = ref({ today_checkins: [], today_points: 0 })
const achievements = ref([])
const presetsLoading = ref(false)
const submitting = ref(false)

const foodCategories = ref([])
const selectedCategoryId = ref('')
const foodPresets = ref([])
const medicationPresets = ref([])
const exercisePresets = ref([])

const foodRecords = ref([])
const medicationRecords = ref([])
const exerciseRecords = ref([])
const glucoseRecords = ref([])

const foodDialogVisible = ref(false)
const foodDialogFood = ref(null)
const foodDialogAmount = ref(100)
const foodDialogUnit = ref(1)

const medDialogVisible = ref(false)
const medDialogDrug = ref(null)
const medDialogDosage = ref('')
const medDialogTaken = ref(true)

const exDialogVisible = ref(false)
const exDialogItem = ref(null)
const exDialogDuration = ref(30)

const glucoseForm = ref({ value: 5.6, context: 4 })
const glucoseTrendDays = ref(14)
const glucoseHistory = ref({ records: [], summary: {} })
const glucoseChartRef = ref(null)
let glucoseChart = null

const customFood = ref({
  name: '', category_id: '', calories_per_gram: 1, is_liquid: false, ml_to_g_ratio: 1,
  input_unit: 1, input_amount: 100, image_object_key: '', image_url: '', uploading: false,
})
const customMed = ref({
  name: '', dosage: '', taken: true, image_object_key: '', image_url: '', uploading: false,
})
const customEx = ref({ name: '', calories_per_minute: 5, duration: 30 })

const foodFileInput = ref(null)
const medFileInput = ref(null)

const progressCircumference = 2 * Math.PI * 28

const todayProgress = computed(() => {
  const list = todayStatus.value.today_checkins || []
  const done = list.filter((x) => x.completed).length
  return Math.round((done / 4) * 100)
})

const todayTasksSummary = computed(() => {
  const list = todayStatus.value.today_checkins || []
  const done = list.filter((x) => x.completed).length
  return { done, total: Math.max(list.length, 4) }
})

const progressRingOffset = computed(() => {
  return progressCircumference * (1 - todayProgress.value / 100)
})

const mobileRingCircumference = 2 * Math.PI * 16
const mobileRingOffset = computed(() => mobileRingCircumference * (1 - todayProgress.value / 100))

const foodCaloriePercent = computed(() =>
  Math.min(100, Math.round((foodDailyTotalCalories.value / 1800) * 100)),
)

const isToday = computed(() => checkinDate.value === dayjs().format('YYYY-MM-DD'))
const unlockedCount = computed(() => achievements.value.filter((a) => a.unlocked).length)
const glucoseSummary = computed(() => glucoseHistory.value.summary || {})

const foodDailyTotalCalories = computed(() =>
  foodRecords.value.reduce((sum, r) => sum + (Number(r.total_calories) || 0), 0),
)

const dateDisplay = computed(() => {
  const d = dayjs(checkinDate.value)
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return {
    main: d.isSame(dayjs(), 'day') ? '今天' : d.format('M月D日'),
    full: d.format('YYYY年M月D日'),
    weekday: weekdays[d.day()],
  }
})

onMounted(async () => {
  const tabQuery = route.query.tab
  if (tabQuery && mainTabs.some((t) => t.key === tabQuery)) {
    activeTab.value = tabQuery
  }
  await loadHeroData()
  await ensureCategoryLists()
  await loadTabData()
  refreshReminders()
})

watch(foodMode, (mode) => {
  if (mode === 'custom') ensureCategoryLists()
})

watch(activeTab, (tab) => {
  if (tab === 'glucose') loadGlucoseHistory()
  else loadPresets()
})
watch(checkinDate, () => {
  loadRecords()
  if (activeTab.value === 'glucose') loadGlucoseHistory()
})
watch(glucoseTrendDays, () => {
  if (activeTab.value === 'glucose') loadGlucoseHistory()
})

onUnmounted(() => {
  glucoseChart?.dispose()
  glucoseChart = null
})

async function loadHeroData() {
  const [status, st, ach] = await Promise.all([getTodayStatus(), getCheckinStats(), getAchievements()])
  todayStatus.value = status
  stats.value = {
    total_points: st.total_points ?? status.total_points ?? 0,
    streak_days: st.streak_days ?? status.streak_days ?? 0,
    total_checkins: st.total_checkins ?? 0,
    week_checkins: st.week_checkins ?? 0,
  }
  achievements.value = ach
}

async function loadTabData() {
  await Promise.all([loadPresets(), loadRecords()])
}

async function loadPresets() {
  presetsLoading.value = true
  try {
    if (activeTab.value === 'food') {
      if (!foodCategories.value.length) {
        foodCategories.value = await getFoodCategories()
        selectedCategoryId.value = foodCategories.value[0]?.category_id || ''
      }
      if (selectedCategoryId.value) {
        foodPresets.value = await getFoodPresets(selectedCategoryId.value)
      }
    } else if (activeTab.value === 'medication') {
      medicationPresets.value = await getMedicationPresets()
    } else if (activeTab.value === 'exercise') {
      exercisePresets.value = await getExercisePresets()
    }
  } finally {
    presetsLoading.value = false
  }
}

async function ensureCategoryLists() {
  if (!foodCategories.value.length) {
    foodCategories.value = await getFoodCategories()
    if (!selectedCategoryId.value) {
      selectedCategoryId.value = foodCategories.value[0]?.category_id || ''
    }
  }
}

async function loadRecords() {
  const date = checkinDate.value
  const [food, med, ex, glu] = await Promise.all([
    getFoodRecords(date), getMedicationRecords(date),
    getExerciseRecords(date), getGlucoseRecords(date),
  ])
  foodRecords.value = food
  medicationRecords.value = med
  exerciseRecords.value = ex
  glucoseRecords.value = glu
}

function switchTab(key) {
  activeTab.value = key
}

async function loadGlucoseHistory() {
  const end = checkinDate.value
  const start = dayjs(end).subtract(glucoseTrendDays.value - 1, 'day').format('YYYY-MM-DD')
  try {
    glucoseHistory.value = await getGlucoseHistory({ start_date: start, end_date: end })
    await nextTick()
    renderGlucoseChart()
  } catch (e) {
    glucoseHistory.value = { records: [], summary: { count: 0 } }
    await nextTick()
    renderGlucoseChart()
    ElMessage.error(e.message || '加载血糖趋势失败')
  }
}

function glucoseStatusLabel(status) {
  return GLUCOSE_STATUS_LABELS[status] || GLUCOSE_STATUS_LABELS.unknown
}

function formatChartLabel(record) {
  if (!record.record_time) return record.checkin_date || ''
  return dayjs(record.record_time).format('M/D HH:mm')
}

function renderGlucoseChart() {
  if (!glucoseChartRef.value) return
  if (!glucoseChart) glucoseChart = echarts.init(glucoseChartRef.value)

  const records = glucoseHistory.value.records || []
  const labels = records.map(formatChartLabel)
  const values = records.map((r) => Number(r.glucose_value))

  glucoseChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter(params) {
        const idx = params[0]?.dataIndex
        const r = records[idx]
        if (!r) return ''
        return `${r.measure_context_label}<br/>${r.glucose_value} mmol/L<br/>${dayjs(r.record_time).format('YYYY-MM-DD HH:mm')}`
      },
    },
    grid: { left: 40, right: 16, top: 24, bottom: 36 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { fontSize: 10, rotate: labels.length > 8 ? 35 : 0 },
    },
    yAxis: {
      type: 'value',
      name: 'mmol/L',
      min: records.length ? (v) => Math.max(0, Math.floor((v.min ?? 3) - 1)) : 0,
      max: records.length ? (v) => Math.ceil((v.max ?? 8) + 1) : 12,
    },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { color: '#ef4444', width: 2 },
      itemStyle: { color: '#ef4444' },
      markArea: {
        silent: true,
        itemStyle: { color: 'rgba(148, 163, 184, 0.18)' },
        data: [[{ yAxis: 3.9 }, { yAxis: 6.1 }]],
      },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { type: 'dashed', color: '#94a3b8' },
        data: [{ yAxis: 6.1, label: { formatter: '6.1', fontSize: 10 } }],
      },
    }],
  }, true)
}

function changeDate(delta) {
  checkinDate.value = dayjs(checkinDate.value).add(delta, 'day').format('YYYY-MM-DD')
  loadRecords()
}

async function selectCategory(id) {
  selectedCategoryId.value = id
  foodPresets.value = await getFoodPresets(id)
}

function openFoodPreset(f) {
  foodDialogFood.value = f
  foodDialogAmount.value = f.is_liquid ? 200 : 100
  foodDialogUnit.value = f.is_liquid ? 2 : 1
  foodDialogVisible.value = true
}

function openMedPreset(d) {
  medDialogDrug.value = d
  medDialogDosage.value = ''
  medDialogTaken.value = true
  medDialogVisible.value = true
}

function openExPreset(e) {
  exDialogItem.value = e
  exDialogDuration.value = 30
  exDialogVisible.value = true
}

function calcGrams(amount, unit, mlToG) {
  if (unit === 2) return amount * (mlToG || 1)
  return amount
}

function calcFoodCalories(item) {
  const grams = calcGrams(item.input_amount, item.input_unit, item.ml_to_g_ratio)
  return Math.round((item.calories_per_gram || 0) * grams)
}

function calcPresetFoodCalories() {
  const f = foodDialogFood.value
  if (!f) return 0
  const grams = calcGrams(foodDialogAmount.value, foodDialogUnit.value, f.ml_to_g_ratio)
  return Math.round(f.calories_per_gram * grams)
}

function calcExCalories(kpm, mins) {
  return Math.round((kpm || 0) * (mins || 0))
}

function formatFoodAmount(r) {
  if (r.input_unit === 2) return `${r.input_amount}ml（≈${r.grams}g）`
  return `${r.input_amount}g`
}

function formatTime(t) {
  if (!t) return ''
  return dayjs(t).format('HH:mm')
}

function onImgError(e) {
  e.target.style.display = 'none'
}

function triggerFoodUpload() { foodFileInput.value?.click() }
function triggerMedUpload() { medFileInput.value?.click() }

async function handleUpload(type, file, target) {
  if (!file) return
  target.uploading = true
  try {
    const res = await uploadCheckinImage(type, file)
    target.image_object_key = res.object_key
    target.image_url = res.image_url
    ElMessage.success('图片上传成功')
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    target.uploading = false
  }
}

function onFoodFileChange(e) {
  handleUpload('food', e.target.files?.[0], customFood.value)
  e.target.value = ''
}

function onMedFileChange(e) {
  handleUpload('medical', e.target.files?.[0], customMed.value)
  e.target.value = ''
}

async function submitFoodPreset() {
  const f = foodDialogFood.value
  if (!f || !foodDialogAmount.value) return ElMessage.warning('请填写食用量')
  submitting.value = true
  try {
    await createFoodCheckin({
      checkin_date: checkinDate.value,
      meal_period: mealPeriod.value,
      source_type: 1,
      food_id: f.food_id,
      input_unit: foodDialogUnit.value,
      input_amount: foodDialogAmount.value,
      ml_to_g_ratio: f.ml_to_g_ratio,
      image_object_key: f.image_object_key,
    })
    ElMessage.success('打卡成功')
    foodDialogVisible.value = false
    await Promise.all([loadRecords(), loadHeroData()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitCustomFood() {
  const c = customFood.value
  if (!c.category_id) return ElMessage.warning('请选择食物分类')
  if (!c.name?.trim()) return ElMessage.warning('请填写食物名称')
  if (!c.image_object_key) return ElMessage.warning('请先上传图片')
  if (!c.input_amount) return ElMessage.warning('请填写食用量')
  submitting.value = true
  try {
    await createFoodCheckin({
      checkin_date: checkinDate.value,
      meal_period: mealPeriod.value,
      source_type: 2,
      category_id: c.category_id,
      food_name: c.name.trim(),
      calories_per_gram: c.calories_per_gram,
      input_unit: c.is_liquid ? c.input_unit : 1,
      input_amount: c.input_amount,
      ml_to_g_ratio: c.is_liquid ? c.ml_to_g_ratio : 1,
      image_object_key: c.image_object_key,
    })
    ElMessage.success('打卡成功')
    customFood.value = {
      name: '', category_id: foodCategories.value[0]?.category_id || '', calories_per_gram: 1,
      is_liquid: false, ml_to_g_ratio: 1, input_unit: 1, input_amount: 100,
      image_object_key: '', image_url: '', uploading: false,
    }
    await Promise.all([loadRecords(), loadHeroData()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitMedPreset() {
  const d = medDialogDrug.value
  if (!d || !medDialogDosage.value?.trim()) return ElMessage.warning('请填写剂量')
  submitting.value = true
  try {
    await createMedicationCheckin({
      checkin_date: checkinDate.value,
      source_type: 1,
      drug_id: d.drug_id,
      dosage: medDialogDosage.value.trim(),
      taken: medDialogTaken.value,
      image_object_key: d.image_object_key,
    })
    ElMessage.success('打卡成功')
    medDialogVisible.value = false
    await Promise.all([loadRecords(), loadHeroData()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitCustomMed() {
  const c = customMed.value
  if (!c.name?.trim() || !c.dosage?.trim()) return ElMessage.warning('请填写药品名称和剂量')
  if (!c.image_object_key) return ElMessage.warning('请先上传图片')
  submitting.value = true
  try {
    await createMedicationCheckin({
      checkin_date: checkinDate.value,
      source_type: 2,
      drug_name: c.name.trim(),
      dosage: c.dosage.trim(),
      taken: c.taken,
      image_object_key: c.image_object_key,
    })
    ElMessage.success('打卡成功')
    customMed.value = { name: '', dosage: '', taken: true, image_object_key: '', image_url: '', uploading: false }
    await Promise.all([loadRecords(), loadHeroData()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitExPreset() {
  const e = exDialogItem.value
  if (!e || !exDialogDuration.value) return ElMessage.warning('请填写运动分钟数')
  submitting.value = true
  try {
    await createExerciseCheckin({
      checkin_date: checkinDate.value,
      source_type: 1,
      exercise_id: e.exercise_id,
      duration_minutes: exDialogDuration.value,
    })
    ElMessage.success('打卡成功')
    exDialogVisible.value = false
    await Promise.all([loadRecords(), loadHeroData()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitCustomEx() {
  const c = customEx.value
  if (!c.name?.trim() || !c.duration) return ElMessage.warning('请填写完整信息')
  submitting.value = true
  try {
    await createExerciseCheckin({
      checkin_date: checkinDate.value,
      source_type: 2,
      exercise_name: c.name.trim(),
      calories_per_minute: c.calories_per_minute,
      duration_minutes: c.duration,
    })
    ElMessage.success('打卡成功')
    customEx.value = { name: '', calories_per_minute: 5, duration: 30 }
    await Promise.all([loadRecords(), loadHeroData()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitGlucose() {
  const v = glucoseForm.value.value
  if (v == null) return ElMessage.warning('请填写血糖值')
  if (v > 20 || v < 2) {
    try { await ElMessageBox.confirm(`血糖值 ${v} mmol/L 异常，确认提交？`, '异常确认') } catch { return }
  }
  submitting.value = true
  try {
    const res = await createGlucoseCheckin({
      checkin_date: checkinDate.value,
      glucose_value: v,
      measure_context: glucoseForm.value.context,
    })
    ElMessage.success(`打卡成功！+${res.points_earned || 15} 积分`)
    await Promise.all([loadRecords(), loadHeroData(), loadGlucoseHistory()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* 与首页 site.css 设计 token 对齐 */
.reminder-banner-wrap {
  padding: 12px 16px 0;
  max-width: 1200px;
  margin: 0 auto;
}
.reminder-banner {
  margin-bottom: 8px;
}
.reminder-banner__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.checkin-page {
  --ck-border: var(--warm-200);
  --ck-accent-bg: #f0fdfa;
  --ck-accent-border: #ccfbf1;
  min-height: 100%;
  width: 100%;
  box-sizing: border-box;
  background: var(--warm-50);
  padding: 24px clamp(16px, 2vw, 32px) 32px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--ck-border);
  transition: all 0.3s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(13, 148, 136, 0.1);
}

.stat-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.stat-icon--primary { background: var(--ck-accent-border); color: var(--health-700); }
.stat-icon--accent { background: #fef3c7; color: #f59e0b; }
.stat-icon--orange { background: #ffedd5; color: #f97316; }

.stat-value {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  color: #292524;
  line-height: 1.1;
}
.stat-label {
  margin: 4px 0 0;
  font-size: 14px;
  color: #a8a29e;
}

.stat-badge {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 8px;
  border-radius: 8px;
  white-space: nowrap;
}
.stat-badge--primary { color: var(--health-700); background: var(--ck-accent-bg); }
.stat-badge--accent { color: #d97706; background: #fffbeb; }
.stat-badge--orange { color: #ea580c; background: #fff7ed; }

.stat-card--progress {
  display: flex;
  align-items: center;
  gap: 20px;
}

.progress-ring-wrap {
  position: relative;
  width: 64px;
  height: 64px;
  flex-shrink: 0;
}
.progress-ring {
  transform: rotate(-90deg);
  width: 64px;
  height: 64px;
}
.progress-ring-circle {
  transition: stroke-dashoffset 0.5s ease;
}
.progress-ring-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--health-700);
}

.progress-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #292524;
}
.progress-sub {
  margin: 2px 0 0;
  font-size: 14px;
  color: #a8a29e;
}

.page-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.workspace-layout {
  display: grid;
  grid-template-columns: minmax(260px, 300px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.workspace-aside {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: sticky;
  top: 24px;
}

.checkin-workspace {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
  padding: 24px;
}

.checkin-inner-panel {
  background: #fafaf9;
  border-radius: 14px;
  border: 1px solid var(--ck-border);
  padding: 24px;
}

.sidebar-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.sidebar-block:not(:first-child) {
  padding-top: 20px;
  border-top: 1px solid var(--ck-border);
}

.date-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  background: #fff;
  border-radius: 16px;
  padding: 16px 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--ck-border);
}

.date-section--embedded {
  background: transparent;
  box-shadow: none;
  border: none;
  border-bottom: 1px solid var(--ck-border);
  border-radius: 0;
  padding: 4px 0 20px;
  margin-bottom: 4px;
}

.date-nav-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 12px;
  background: var(--ck-accent-bg);
  color: var(--health-700);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
}
.date-nav-btn:hover:not(:disabled) { background: var(--ck-accent-border); }
.date-nav-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.date-center {
  display: flex;
  align-items: center;
  gap: 12px;
}
.date-badge {
  padding: 6px 16px;
  background: linear-gradient(90deg, var(--health-500), var(--health-600));
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  border-radius: 999px;
}
.date-main {
  font-size: 18px;
  font-weight: 600;
  color: #292524;
}
.date-weekday {
  font-size: 14px;
  color: #a8a29e;
}

.main-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.layout-sidebar {
  display: flex;
  flex-direction: column;
}

.panel-card {
  background: #fff;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--ck-border);
}

.panel-label {
  margin: 0 0 18px;
  font-size: 14px;
  font-weight: 600;
  color: #57534e;
  letter-spacing: 0.02em;
}

.category-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-nav-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border: none;
  border-radius: 12px;
  background: transparent;
  font-size: 16px;
  font-weight: 500;
  color: #57534e;
  cursor: pointer;
  transition: all 0.2s;
}
.category-nav-btn:hover {
  background: var(--ck-accent-bg);
  color: var(--health-700);
}
.category-nav-btn.active {
  background: linear-gradient(90deg, var(--health-500), var(--health-600));
  color: #fff;
}

.meal-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.meal-chip {
  padding: 10px 18px;
  border-radius: 999px;
  border: none;
  background: #f5f5f4;
  font-size: 15px;
  font-weight: 500;
  color: #78716c;
  cursor: pointer;
  transition: all 0.2s;
}
.meal-chip:hover {
  background: var(--ck-accent-border);
  color: var(--health-700);
}
.meal-chip.active {
  background: var(--health-600);
  color: #fff;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.quick-action-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 12px;
  border: 2px dashed #99f6e4;
  background: transparent;
  color: var(--health-700);
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.quick-action-btn:hover,
.quick-action-btn.active {
  border-color: var(--health-600);
  background: var(--ck-accent-bg);
}
.quick-action-btn--muted {
  border-color: #e7e5e4;
  color: #78716c;
}
.quick-action-btn--muted:hover,
.quick-action-btn--muted.active {
  border-color: #a8a29e;
  background: #fafaf9;
}

.layout-main {
  min-width: 0;
}

.module-panel {
  background: #fafaf9;
  border-radius: 14px;
  padding: 24px;
  border: 1px solid var(--ck-border);
}

.panel-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: #292524;
}

.mode-switch {
  display: flex;
  background: #f5f5f4;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 20px;
}
.mode-switch button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 10px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #78716c;
  cursor: pointer;
  transition: all 0.2s;
}
.mode-switch button.active {
  background: #fff;
  color: var(--health-700);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.mode-switch.med button.active { color: #6366f1; }
.mode-switch.ex button.active { color: #d97706; }

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}
.cat-tab {
  padding: 8px 20px;
  border-radius: 999px;
  border: 2px solid transparent;
  background: #f5f5f4;
  font-size: 14px;
  font-weight: 500;
  color: #78716c;
  cursor: pointer;
  transition: all 0.2s;
}
.cat-tab:hover {
  border-color: #99f6e4;
  color: var(--health-700);
}
.cat-tab.active {
  background: var(--ck-accent-bg);
  color: var(--health-700);
  border-color: var(--health-600);
}

.food-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.food-item {
  background: #fafaf9;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid #f5f5f4;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.food-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(13, 148, 136, 0.15);
}

.food-img-wrap {
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: #f5f5f4;
  display: flex;
  align-items: center;
  justify-content: center;
}
.food-img-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s;
}
.food-item:hover .food-img-wrap img { transform: scale(1.1); }
.food-placeholder { font-size: 32px; }

.food-name {
  display: block;
  padding: 16px 16px 4px;
  font-size: 14px;
  font-weight: 600;
  color: #292524;
}
.food-kcal {
  display: block;
  padding: 0 16px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--health-700);
}

.drug-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.drug-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  background: #fafaf9;
  cursor: pointer;
  border: 1px solid #f5f5f4;
  transition: all 0.2s;
}
.drug-item:hover {
  background: var(--ck-accent-bg);
  border-color: var(--ck-accent-border);
}
.drug-thumb {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #eef2ff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}
.drug-thumb img { width: 100%; height: 100%; object-fit: cover; }

.exercise-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.exercise-item {
  padding: 16px;
  border-radius: 12px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  cursor: pointer;
  transition: all 0.2s;
}
.exercise-item:hover { transform: translateY(-2px); }
.ex-name { display: block; font-weight: 700; color: #92400e; margin-bottom: 4px; }
.ex-kcal { font-size: 13px; color: #b45309; }

.glucose-input-card {
  background: #fef2f2;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  border: 1px solid #fecaca;
}
.glucose-submit { width: 100%; margin-top: 4px; }

.glucose-trend-section { margin-bottom: 16px; }
.trend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.trend-head .panel-title { margin-bottom: 0; }

.glucose-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.g-stat {
  background: #fafaf9;
  border-radius: 10px;
  padding: 10px 4px;
  text-align: center;
  border: 1px solid #f5f5f4;
}
.g-stat-num { display: block; font-size: 16px; font-weight: 700; color: #ef4444; }
.g-stat-lbl { font-size: 11px; color: #a8a29e; }

.glucose-chart { width: 100%; height: 220px; }
.chart-hint { margin: 8px 0 0; font-size: 12px; color: #a8a29e; text-align: center; }

.glucose-card .glucose-value-row { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
.glucose-value { font-size: 22px; color: #ef4444; }
.glucose-unit { font-size: 12px; color: #a8a29e; }
.glucose-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}
.status-normal { background: var(--ck-accent-border); color: var(--health-700); }
.status-elevated { background: #fef9c3; color: #ca8a04; }
.status-high { background: #fee2e2; color: #dc2626; }
.status-low { background: #dbeafe; color: #2563eb; }

.upload-box {
  width: 120px;
  height: 120px;
  border: 2px dashed #99f6e4;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #a8a29e;
  cursor: pointer;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}
.upload-box.med { border-color: #c7d2fe; }
.upload-preview { width: 100%; height: 100%; object-fit: cover; }
.upload-mask {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}

.compact-form :deep(.el-form-item) { margin-bottom: 14px; }
.amount-row { display: flex; gap: 8px; align-items: center; width: 100%; }
.unit-tag { font-size: 14px; color: #78716c; padding: 0 8px; }
.preview-kcal {
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: var(--health-700);
  margin: 8px 0 16px;
}
.submit-btn { width: 100%; height: 44px; font-weight: 600; }

.module-panel :deep(.el-button--primary) {
  --el-button-bg-color: var(--health-600);
  --el-button-border-color: var(--health-600);
  --el-button-hover-bg-color: var(--health-700);
  --el-button-hover-border-color: var(--health-700);
}

.records-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e7e5e4;
}

.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}
.records-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #292524;
}

.daily-kcal-summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.daily-kcal-label { font-size: 14px; color: #a8a29e; }
.daily-kcal-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--health-700);
  line-height: 1;
}
.daily-kcal-unit { font-size: 14px; color: #a8a29e; }

.calorie-progress { margin-bottom: 24px; }
.calorie-progress-head {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #78716c;
  margin-bottom: 8px;
}
.calorie-progress-pct { color: var(--health-700); font-weight: 500; }
.calorie-progress-bar {
  height: 12px;
  background: var(--ck-accent-border);
  border-radius: 999px;
  overflow: hidden;
}
.calorie-progress-fill {
  height: 100%;
  background: var(--health-600);
  border-radius: 999px;
  transition: width 0.5s ease;
}

.record-list { display: flex; flex-direction: column; gap: 12px; }
.record-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-radius: 12px;
}
.food-record-card {
  background: var(--ck-accent-bg);
  border: 1px solid var(--ck-border);
}
.record-card:not(.food-record-card) {
  background: #fafaf9;
  border: 1px solid #f5f5f4;
}
.record-thumb {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
  font-size: 24px;
}
.record-thumb img { width: 100%; height: 100%; object-fit: cover; }
.record-body { flex: 1; min-width: 0; }
.record-body.full { padding-left: 4px; }
.record-title { font-size: 14px; font-weight: 600; color: #292524; }
.record-meta { font-size: 13px; color: #a8a29e; margin-top: 2px; }
.record-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}
.record-right strong { color: #292524; font-size: 15px; }
.record-status { font-size: 12px; color: var(--health-700); }
.record-right span { font-size: 12px; color: #a8a29e; }

.records-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  text-align: center;
}
.records-empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: var(--ck-accent-bg);
  color: var(--health-400);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.records-empty p {
  margin: 0;
  font-weight: 500;
  color: #a8a29e;
}
.records-empty span {
  margin-top: 4px;
  font-size: 14px;
  color: #d6d3d1;
}

.achievement-section {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--ck-border);
}

.achievement-collapse {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--ck-border);
  min-width: 0;
  overflow: hidden;
}

.achievement-collapse__summary {
  display: none;
  list-style: none;
  cursor: pointer;
}

.achievement-collapse__summary::-webkit-details-marker {
  display: none;
}

@media (min-width: 769px) {
  .achievement-collapse__summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;
    pointer-events: none;
    cursor: default;
  }
}

.mobile-checkin-nav {
  display: none;
}

.achievement-section .achievement-grid,
.achievement-collapse .achievement-grid {
  grid-template-columns: 1fr;
  gap: 12px;
}

.achievement-section .achievement-card,
.achievement-collapse .achievement-card {
  padding: 16px;
}

.achievement-section .achievement-icon,
.achievement-collapse .achievement-icon {
  width: 48px;
  height: 48px;
  font-size: 22px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.section-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #292524;
}

.ach-count {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}
.ach-count-done { color: var(--health-700); font-weight: 700; }
.ach-count-sep { color: #d6d3d1; }
.ach-count-total { color: #a8a29e; }

.achievement-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.achievement-card {
  border-radius: 16px;
  padding: 20px;
  text-align: center;
  background: #fafaf9;
  border: 1px solid #f5f5f4;
  opacity: 0.6;
  transition: all 0.3s ease;
}
.achievement-card:hover { transform: translateY(-2px); }
.achievement-card.unlocked { opacity: 1; }
.achievement-card.unlocked.tone-0 {
  background: var(--ck-accent-bg);
  border-color: var(--ck-accent-border);
}
.achievement-card.unlocked.tone-1 {
  background: #fffbeb;
  border-color: #fde68a;
}

.achievement-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: #e7e5e4;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
  font-size: 24px;
}
.achievement-card.unlocked.tone-0 .achievement-icon {
  background: linear-gradient(135deg, var(--health-500), var(--health-700));
  box-shadow: 0 8px 24px rgba(13, 148, 136, 0.2);
}
.achievement-card.unlocked.tone-1 .achievement-icon {
  background: #f59e0b;
  box-shadow: 0 8px 24px rgba(245, 158, 11, 0.25);
}

.ach-name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #292524;
}
.achievement-card:not(.unlocked) .ach-name { color: #78716c; }

.ach-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: #a8a29e;
}

.ach-status {
  margin-top: 12px;
  font-size: 12px;
  font-weight: 500;
  color: #a8a29e;
}
.achievement-card.unlocked .ach-status { color: var(--health-700); }

.analysis-banner {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 16px;
  cursor: pointer;
  border: 1px solid var(--ck-border);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
}
.analysis-banner:hover {
  box-shadow: 0 8px 32px rgba(13, 148, 136, 0.1);
}

.banner-left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
}
.banner-icon {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--health-500), var(--health-700));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(13, 148, 136, 0.25);
  transition: transform 0.3s;
}
.analysis-banner:hover .banner-icon { transform: scale(1.08); }

.banner-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.banner-text strong {
  font-size: 16px;
  color: #292524;
}
.banner-text span {
  font-size: 13px;
  color: #a8a29e;
  line-height: 1.4;
}

.banner-right {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  font-size: 13px;
  color: #a8a29e;
}
.banner-arrow {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--ck-accent-bg);
  color: var(--health-700);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.analysis-banner:hover .banner-arrow {
  background: var(--health-600);
  color: #fff;
}

.dialog-preset { text-align: center; }
.dialog-img {
  width: 100px;
  height: 100px;
  margin: 0 auto 12px;
  border-radius: 12px;
  background: #f5f5f4;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-size: 40px;
}
.dialog-img img { width: 100%; height: 100%; object-fit: cover; }
.dialog-meta { color: #78716c; font-size: 14px; margin-bottom: 12px; }

@media (max-width: 1200px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .food-grid { grid-template-columns: repeat(3, 1fr); }
  .workspace-layout {
    grid-template-columns: 1fr;
  }
  .workspace-aside {
    position: static;
  }
  .achievement-section .achievement-grid,
  .achievement-collapse .achievement-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .checkin-page--mobile-ref {
    padding: 12px 16px calc(80px + env(safe-area-inset-bottom));
    overflow-x: hidden;
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
    background: linear-gradient(180deg, #ecfdf5 0%, var(--warm-50) 120px, var(--warm-50) 100%);
  }

  .checkin-page--mobile-ref .stat-card:hover {
    transform: none;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  }

  .checkin-page--mobile-ref .reminder-banner-wrap {
    margin: 0 0 12px;
    padding: 0;
    max-width: none;
  }

  .checkin-page--mobile-ref .reminder-banner {
    border-radius: 14px;
    border: 1px solid #fde68a;
    box-shadow: 0 2px 12px rgba(245, 158, 11, 0.1);
  }

  .checkin-page--mobile-ref .reminder-banner__body {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .checkin-page--mobile-ref .reminder-banner__body .el-button {
    align-self: flex-start;
    border-radius: 999px;
    padding: 8px 16px;
  }

  /* 今日进度条 */
  .mobile-today-progress {
    margin-bottom: 16px;
    padding: 14px 16px;
    background: #fff;
    border-radius: 18px;
    border: 1px solid var(--ck-border);
    box-shadow: 0 4px 20px rgba(13, 148, 136, 0.08);
  }

  .mobile-today-progress__info {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 12px;
  }

  .mobile-today-progress__ring {
    position: relative;
    width: 44px;
    height: 44px;
    flex-shrink: 0;
  }

  .mobile-today-progress__ring svg {
    width: 44px;
    height: 44px;
  }

  .mobile-today-progress__ring span {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 700;
    color: var(--health-700);
  }

  .mobile-today-progress__text {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  .mobile-today-progress__text strong {
    font-size: 15px;
    font-weight: 700;
    color: var(--warm-800);
  }

  .mobile-today-progress__text span {
    font-size: 12px;
    color: var(--warm-500);
  }

  .mobile-today-progress__bar {
    height: 8px;
    border-radius: 999px;
    background: #ecfdf5;
    overflow: hidden;
  }

  .mobile-today-progress__fill {
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--health-400), var(--health-600));
    transition: width 0.4s ease;
  }

  /* 统计：三列网格 */
  .checkin-page--mobile-ref .stats-grid--mobile {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
    margin-bottom: 16px;
    width: 100%;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref {
    padding: 12px 10px;
    border-radius: 16px;
    position: relative;
    overflow: hidden;
    min-width: 0;
    border: 1px solid rgba(255, 255, 255, 0.9);
    box-shadow: 0 2px 16px rgba(15, 23, 42, 0.06);
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref:active {
    transform: scale(0.97);
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    opacity: 0.45;
    transform: translate(35%, -45%);
    pointer-events: none;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref:nth-child(1)::after {
    background: var(--ck-accent-bg);
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref:nth-child(2)::after {
    background: #fffbeb;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref:nth-child(3)::after {
    background: #fff7ed;
  }

  .checkin-page--mobile-ref .stat-card__mobile-head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 4px;
    margin-bottom: 6px;
    position: relative;
    z-index: 1;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref .stat-icon {
    width: 22px;
    height: 22px;
    border-radius: 8px;
    margin-bottom: 0;
    flex-shrink: 0;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref .stat-badge {
    font-size: 9px;
    padding: 2px 5px;
    border-radius: 999px;
    max-width: 100%;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref .stat-value {
    position: relative;
    z-index: 1;
    font-size: 20px;
    font-weight: 700;
    line-height: 1.2;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref .stat-label {
    position: relative;
    z-index: 1;
    font-size: 11px;
    margin-top: 2px;
  }

  /* 快捷入口：双列 */
  .checkin-page--mobile-ref .mobile-checkin-nav {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin-bottom: 16px;
    width: 100%;
  }

  .checkin-page--mobile-ref .mobile-nav-chip {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 12px 14px;
    border-radius: 16px;
    border: none;
    font-size: 14px;
    font-weight: 500;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04), 0 1px 4px rgba(0, 0, 0, 0.04);
    min-width: 0;
    cursor: pointer;
    transition: transform 0.2s ease, box-shadow 0.2s ease;
  }

  .checkin-page--mobile-ref .mobile-nav-chip--analysis {
    background: linear-gradient(135deg, #f0fdfa 0%, #ccfbf1 100%);
    color: var(--health-700);
    box-shadow: 0 4px 16px rgba(13, 148, 136, 0.12);
  }

  .checkin-page--mobile-ref .mobile-nav-chip--analysis .el-icon {
    color: var(--health-600);
    font-size: 18px;
  }

  .checkin-page--mobile-ref .mobile-nav-chip--reminder {
    background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
    color: #b45309;
    box-shadow: 0 4px 16px rgba(245, 158, 11, 0.12);
  }

  .checkin-page--mobile-ref .mobile-nav-chip--reminder .el-icon {
    color: #d97706;
    font-size: 18px;
  }

  .checkin-page--mobile-ref .mobile-nav-chip:active {
    transform: scale(0.98);
  }

  .checkin-page--mobile-ref .page-body,
  .checkin-page--mobile-ref .workspace-layout,
  .checkin-page--mobile-ref .checkin-workspace,
  .checkin-page--mobile-ref .main-layout,
  .checkin-page--mobile-ref .layout-sidebar,
  .checkin-page--mobile-ref .layout-main,
  .checkin-page--mobile-ref .workspace-aside {
    width: 100%;
    max-width: 100%;
    min-width: 0;
    box-sizing: border-box;
  }

  .checkin-page--mobile-ref .page-body {
    gap: 0;
  }

  .checkin-page--mobile-ref .workspace-layout {
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .checkin-page--mobile-ref .checkin-workspace.panel-card {
    display: flex;
    flex-direction: column;
    gap: 0;
    order: 1;
    padding: 16px;
    background: #fff;
    border: 1px solid var(--ck-border);
    border-radius: 20px;
    box-shadow: 0 4px 24px rgba(13, 148, 136, 0.08);
    position: relative;
    overflow: hidden;
  }

  .checkin-page--mobile-ref .checkin-workspace.panel-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 16px;
    right: 16px;
    height: 3px;
    border-radius: 0 0 3px 3px;
    background: linear-gradient(90deg, var(--health-400), var(--health-600));
  }

  .checkin-page--mobile-ref .main-layout {
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .checkin-page--mobile-ref .analysis-banner {
    display: none;
  }

  .checkin-page--mobile-ref .workspace-aside {
    order: 2;
    margin-top: 20px;
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .checkin-page--mobile-ref .date-section--embedded {
    background: var(--warm-50);
    border-radius: 14px;
    padding: 12px;
    box-shadow: none;
    border: 1px solid var(--ck-border);
    margin-bottom: 16px;
    gap: 8px;
    width: 100%;
  }

  .checkin-page--mobile-ref .date-nav-btn {
    width: 36px;
    height: 36px;
    flex-shrink: 0;
    border-radius: 12px;
    background: #f5f5f4;
    color: #a8a29e;
  }

  .checkin-page--mobile-ref .date-nav-btn:not(:disabled):active {
    background: var(--ck-accent-bg);
    color: var(--health-600);
  }

  .checkin-page--mobile-ref .date-center {
    flex: 1;
    min-width: 0;
    gap: 4px;
    flex-wrap: wrap;
    justify-content: center;
  }

  .checkin-page--mobile-ref .date-badge {
    padding: 4px 12px;
    font-size: 12px;
    border-radius: 999px;
    box-shadow: 0 2px 8px rgba(13, 148, 136, 0.15);
  }

  .checkin-page--mobile-ref .date-main {
    font-size: 15px;
    font-weight: 700;
  }

  .checkin-page--mobile-ref .date-weekday {
    font-size: 13px;
  }

  .checkin-page--mobile-ref .layout-sidebar {
    position: static;
    margin: 0 0 4px;
    padding: 0;
    background: transparent;
  }

  .checkin-page--mobile-ref .checkin-inner-panel,
  .checkin-page--mobile-ref .sidebar-panel {
    padding: 0;
    border: none;
    background: transparent;
    width: 100%;
  }

  .checkin-page--mobile-ref .sidebar-block {
    margin-bottom: 16px;
    padding-top: 0 !important;
    border-top: none !important;
    width: 100%;
    overflow: hidden;
  }

  .checkin-page--mobile-ref .panel-label,
  .checkin-page--mobile-ref .food-section-label {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    font-size: 14px;
    font-weight: 600;
    color: #44403c;
  }

  .checkin-page--mobile-ref .panel-label::before,
  .checkin-page--mobile-ref .food-section-label::before {
    content: '';
    width: 4px;
    height: 16px;
    background: linear-gradient(180deg, var(--health-500), var(--health-600));
    border-radius: 2px;
    flex-shrink: 0;
  }

  /* 横向滚动行：必须限制宽度防止撑开页面 */
  .checkin-page--mobile-ref .category-nav,
  .checkin-page--mobile-ref .meal-chips,
  .checkin-page--mobile-ref .category-tabs {
    display: flex;
    flex-direction: row;
    flex-wrap: nowrap;
    align-items: center;
    gap: 8px;
    width: 100%;
    max-width: 100%;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
    margin: 0;
    padding: 2px 0 6px;
  }

  .checkin-page--mobile-ref .category-nav::-webkit-scrollbar,
  .checkin-page--mobile-ref .meal-chips::-webkit-scrollbar,
  .checkin-page--mobile-ref .category-tabs::-webkit-scrollbar {
    display: none;
  }

  .checkin-page--mobile-ref .category-nav-btn {
    width: auto;
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 10px 18px;
    font-size: 14px;
    font-weight: 500;
    border-radius: 999px;
    white-space: nowrap;
    border: none;
    transition: transform 0.2s ease;
  }

  .checkin-page--mobile-ref .meal-chip,
  .checkin-page--mobile-ref .cat-tab {
    flex: 0 0 auto;
    width: auto;
    padding: 10px 18px;
    font-size: 14px;
    font-weight: 500;
    border-radius: 999px;
    white-space: nowrap;
    transition: transform 0.2s ease;
  }

  .checkin-page--mobile-ref .category-nav-btn:not(.active),
  .checkin-page--mobile-ref .meal-chip:not(.active),
  .checkin-page--mobile-ref .cat-tab:not(.active) {
    background: #fff;
    color: #78716c;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  }

  .checkin-page--mobile-ref .category-nav-btn.active,
  .checkin-page--mobile-ref .meal-chip.active {
    background: linear-gradient(135deg, var(--health-500), var(--health-600));
    color: #fff;
    box-shadow: 0 4px 12px rgba(13, 148, 136, 0.25);
  }

  .checkin-page--mobile-ref .cat-tab.active {
    background: #fff;
    border: 2px solid var(--health-500);
    color: var(--health-600);
    box-shadow: none;
  }

  .checkin-page--mobile-ref .category-nav-btn:active,
  .checkin-page--mobile-ref .meal-chip:active,
  .checkin-page--mobile-ref .cat-tab:active {
    transform: scale(0.95);
  }

  .checkin-page--mobile-ref .quick-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    width: 100%;
  }

  .checkin-page--mobile-ref .quick-action-btn {
    width: auto;
    min-width: 0;
    max-width: 100%;
    border-radius: 16px;
    padding: 14px 8px;
    font-size: 13px;
    justify-content: center;
    gap: 6px;
  }

  .checkin-page--mobile-ref .quick-action-btn span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .checkin-page--mobile-ref .quick-action-btn:active {
    transform: scale(0.98);
  }

  .checkin-page--mobile-ref .layout-main {
    margin-top: 4px;
  }

  .checkin-page--mobile-ref .module-panel {
    background: transparent;
    border: none;
    padding: 0;
    border-radius: 0;
    width: 100%;
  }

  .checkin-page--mobile-ref .mode-switch {
    background: #fff;
    border-radius: 16px;
    padding: 4px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    margin-bottom: 16px;
    width: 100%;
  }

  .checkin-page--mobile-ref .mode-switch button {
    padding: 10px;
    font-size: 13px;
    border-radius: 12px;
  }

  .checkin-page--mobile-ref .mode-switch button.active {
    background: linear-gradient(135deg, var(--health-500), var(--health-600));
    color: #fff;
    box-shadow: 0 2px 8px rgba(13, 148, 136, 0.2);
  }

  .checkin-page--mobile-ref .submit-btn {
    height: 48px;
    border-radius: 12px;
    font-size: 15px;
    background: linear-gradient(135deg, var(--health-500), var(--health-600));
    border: none;
    box-shadow: 0 4px 14px rgba(13, 148, 136, 0.3);
  }

  .checkin-page--mobile-ref .calorie-progress-bar {
    height: 10px;
    border-radius: 999px;
    background: #ecfdf5;
  }

  .checkin-page--mobile-ref .calorie-progress-fill {
    border-radius: 999px;
    background: linear-gradient(90deg, var(--health-400), var(--health-600));
  }

  .checkin-page--mobile-ref .records-empty {
    padding: 32px 16px;
    background: var(--warm-50);
    border-radius: 14px;
    border: 1px dashed var(--ck-border);
  }

  .checkin-page--mobile-ref .records-empty-icon {
    width: 56px;
    height: 56px;
    border-radius: 14px;
    background: linear-gradient(135deg, #ecfdf5, #ccfbf1);
  }

  /* 食物：双列卡片 */
  .checkin-page--mobile-ref .food-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin: 0 0 16px;
    width: 100%;
    overflow: visible;
    padding: 0;
  }

  .checkin-page--mobile-ref .food-item {
    background: #fff;
    border-radius: 16px;
    border: 1px solid rgba(231, 229, 228, 0.8);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04), 0 1px 4px rgba(0, 0, 0, 0.04);
    overflow: hidden;
    min-width: 0;
  }

  .checkin-page--mobile-ref .food-item:active {
    transform: scale(0.98);
  }

  .checkin-page--mobile-ref .food-img-wrap {
    position: relative;
  }

  .checkin-page--mobile-ref .food-add-badge {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.9);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--health-600);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  }

  .checkin-page--mobile-ref .food-name {
    padding: 10px 10px 2px;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .checkin-page--mobile-ref .food-kcal {
    padding: 0 10px 10px;
    font-size: 11px;
  }

  .checkin-page--mobile-ref .exercise-list {
    grid-template-columns: 1fr;
    gap: 10px;
    width: 100%;
  }

  .checkin-page--mobile-ref .exercise-item,
  .checkin-page--mobile-ref .drug-item {
    background: #fff;
    border-radius: 16px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  }

  .checkin-page--mobile-ref .glucose-input-card {
    border-radius: 16px;
    padding: 16px;
    width: 100%;
    box-sizing: border-box;
  }

  .checkin-page--mobile-ref .glucose-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .checkin-page--mobile-ref .glucose-chart {
    height: 200px;
  }

  .checkin-page--mobile-ref .trend-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .checkin-page--mobile-ref .records-section {
    margin-top: 16px;
    padding: 16px;
    background: #fff;
    border-radius: 16px;
    border: 1px solid var(--ck-border);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
    width: 100%;
    box-sizing: border-box;
  }

  .checkin-page--mobile-ref .records-head {
    margin-bottom: 12px;
    gap: 8px;
  }

  .checkin-page--mobile-ref .records-title {
    font-size: 15px;
  }

  .checkin-page--mobile-ref .daily-kcal-value {
    font-size: 18px;
  }

  .checkin-page--mobile-ref .record-card {
    gap: 10px;
    padding: 12px;
    border-radius: 12px;
  }

  .checkin-page--mobile-ref .record-thumb {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    font-size: 20px;
  }

  .checkin-page--mobile-ref .custom-form {
    background: #fff;
    border-radius: 16px;
    padding: 16px;
    border: 1px solid var(--ck-border);
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    width: 100%;
    box-sizing: border-box;
  }

  /* 成就墙：置于底部，默认展开 */
  .checkin-page--mobile-ref .achievement-collapse {
    display: block;
    width: 100%;
    padding: 0;
    overflow: hidden;
    border-radius: 16px;
    background: #fff;
    border: 1px solid var(--ck-border);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  }

  .checkin-page--mobile-ref .achievement-collapse__summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 16px;
    list-style: none;
    cursor: pointer;
    border-bottom: 1px solid var(--ck-border);
  }

  .checkin-page--mobile-ref .achievement-collapse__summary::-webkit-details-marker {
    display: none;
  }

  .checkin-page--mobile-ref .achievement-collapse__summary h3 {
    margin: 0;
    font-size: 15px;
    font-weight: 700;
  }

  .checkin-page--mobile-ref .achievement-collapse .achievement-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    overflow: visible;
    margin: 0;
    padding: 12px 16px 16px;
  }

  .checkin-page--mobile-ref .achievement-collapse .achievement-card {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr);
    grid-template-rows: auto auto auto;
    align-items: center;
    gap: 0 12px;
    text-align: left;
    padding: 12px 14px;
    border-radius: 12px;
  }

  .checkin-page--mobile-ref .achievement-collapse .achievement-icon {
    grid-row: 1 / span 3;
    grid-column: 1;
    width: 44px;
    height: 44px;
    font-size: 20px;
    margin: 0;
  }

  .checkin-page--mobile-ref .achievement-collapse .ach-name {
    grid-column: 2;
    grid-row: 1;
    font-size: 14px;
    margin: 0;
  }

  .checkin-page--mobile-ref .achievement-collapse .ach-desc {
    grid-column: 2;
    grid-row: 2;
    font-size: 12px;
    margin: 2px 0 0;
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .checkin-page--mobile-ref .achievement-collapse .ach-status {
    grid-column: 2;
    grid-row: 3;
    margin-top: 4px;
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  .checkin-page--mobile-ref {
    padding: 12px 12px calc(76px + env(safe-area-inset-bottom));
  }

  .checkin-page--mobile-ref .stats-grid--mobile {
    gap: 8px;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref {
    padding: 12px;
  }

  .checkin-page--mobile-ref .stat-card--mobile-ref .stat-value {
    font-size: 20px;
  }

  .checkin-page--mobile-ref .date-main {
    font-size: 16px;
  }

  .checkin-page--mobile-ref .quick-action-btn span {
    font-size: 12px;
  }

  .checkin-page--mobile-ref .category-nav-btn span {
    font-size: 13px;
  }
}
</style>
