/** Mock 占位数据，对接后端后由真实 API 替换 */

export const mockBanners = [
  { id: 'b1', title: '糖尿病预防从日常做起', image: 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=800&h=300&fit=crop', link: '/health-info' },
  { id: 'b2', title: '科学饮食控糖指南', image: 'https://images.unsplash.com/photo-1490645935967-10de6ba17061?w=800&h=300&fit=crop', link: '/health-info' },
  { id: 'b3', title: '运动与血糖管理', image: 'https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?w=800&h=300&fit=crop', link: '/health-info' },
]

export const mockCategories = [
  { id: 'diabetes_basics', name: '糖尿病基础', icon: '📚', desc: '认识糖尿病' },
  { id: 'diet', name: '饮食管理', icon: '🥗', desc: '科学控糖饮食' },
  { id: 'exercise', name: '运动康复', icon: '🏃', desc: '合理运动方案' },
  { id: 'medication', name: '用药指导', icon: '💊', desc: '规范用药知识' },
  { id: 'complications', name: '并发症', icon: '🩺', desc: '预防与护理' },
]

export const mockVideos = [
  { id: 'v1', title: '什么是糖尿病？', cover: 'https://images.unsplash.com/photo-1559757148-5c350d0d3c56?w=300&h=180&fit=crop', duration: '05:32' },
  { id: 'v2', title: '如何正确测血糖', cover: 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300&h=180&fit=crop', duration: '03:18' },
]

export const mockDoctors = [
  { doctor_id: 'doc_001', name: '张明德', title: '主任医师', department: '内分泌科', hospital: '市第一人民医院', avatar_url: 'https://i.pravatar.cc/120?img=11', rating: 4.9, consultation_count: 1280, status: 'online', introduction: '擅长2型糖尿病及并发症诊治' },
  { doctor_id: 'doc_002', name: '李雅琴', title: '副主任医师', department: '内分泌科', hospital: '市中心医院', avatar_url: 'https://i.pravatar.cc/120?img=5', rating: 4.8, consultation_count: 956, status: 'online', introduction: '糖尿病营养与运动干预专家' },
  { doctor_id: 'doc_003', name: '王建国', title: '主治医师', department: '内分泌科', hospital: '省人民医院', avatar_url: 'https://i.pravatar.cc/120?img=12', rating: 4.7, consultation_count: 620, status: 'offline', introduction: '糖尿病前期干预与健康管理' },
  { doctor_id: 'doc_004', name: '陈丽华', title: '主任医师', department: '内分泌科', hospital: '协和医院', avatar_url: 'https://i.pravatar.cc/120?img=9', rating: 5.0, consultation_count: 2100, status: 'busy', introduction: '妊娠糖尿病及特殊人群管理' },
]

export const mockArticles = [
  { article_id: 'art_001', title: '糖尿病的早期症状及预防措施', summary: '糖尿病在早期可能会出现多饮、多食、多尿以及体重减轻等典型症状...', cover_image: 'https://images.unsplash.com/photo-1610348725531-843dff563e2c?w=400&h=250&fit=crop', category: 'diabetes_basics', view_count: 1200, published_at: '2026-06-20T10:00:00+08:00', tags: ['糖尿病', '预防'] },
  { article_id: 'art_002', title: '糖尿病患者的饮食指南', summary: '糖尿病患者的饮食需要严格控制碳水化合物的摄入，合理搭配蛋白质...', cover_image: 'https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?w=400&h=250&fit=crop', category: 'diet', view_count: 856, published_at: '2026-06-18T14:30:00+08:00', tags: ['饮食', '控糖'] },
  { article_id: 'art_003', title: '适合糖尿病患者的运动方式', summary: '规律运动有助于改善胰岛素敏感性，降低血糖水平...', cover_image: 'https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=400&h=250&fit=crop', category: 'exercise', view_count: 642, published_at: '2026-06-15T09:00:00+08:00', tags: ['运动', '血糖'] },
  { article_id: 'art_004', title: '糖尿病用药注意事项', summary: '降糖药物需在医生指导下使用，注意药物相互作用和低血糖风险...', cover_image: 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=400&h=250&fit=crop', category: 'medication', view_count: 523, published_at: '2026-06-12T16:00:00+08:00', tags: ['用药', '安全'] },
]

export const mockArticleContent = `# 糖尿病的早期症状及预防措施

糖尿病是一种以高血糖为特征的代谢性疾病。了解早期症状有助于及早发现和干预。

## 典型症状

- **多饮**：口渴感明显增强，饮水量增多
- **多食**：食欲亢进，进食量增加
- **多尿**：尿量增多，夜尿频繁
- **体重减轻**：不明原因的体重下降

## 预防措施

1. 保持健康体重，BMI 控制在 18.5~23.9
2. 均衡饮食，减少精制糖和饱和脂肪摄入
3. 每周至少 150 分钟中等强度有氧运动
4. 定期体检，监测空腹血糖和糖化血红蛋白

> 参考来源：《中国2型糖尿病防治指南》2024版
`

export const mockUserProfile = {
  user_id: 'user_001',
  username: 'demo_user',
  nickname: '糖友小明',
  avatar_url: 'https://i.pravatar.cc/150?img=33',
  phone: '138****8888',
  email: 'demo@example.com',
  gender: 'male',
  birth_date: '1980-05-15',
  points: 520,
  streak_days: 12,
  role: 'user',
  privacy_settings: {
    data_visible: true,
    checkin_notify: true,
    consult_notify: true,
    marketing_notify: false,
  },
}

export const mockHealthAlert = {
  has_alert: true,
  level: 'warning',
  message: '近期空腹血糖持续偏高（6.5 mmol/L），建议关注饮食并咨询医生。',
}

export const mockHealthTrendSummary =
  '本月您的空腹血糖均值较上月略有上升，体重保持稳定。建议增加每周运动频次，并定期监测餐后血糖。'

export const mockHealthRecord = {
  height: 170,
  weight: 72,
  bmi: 24.9,
  fasting_glucose: 6.5,
  postprandial_glucose: 8.2,
  random_glucose: null,
  hba1c: 6.1,
  systolic_bp: 130,
  diastolic_bp: 82,
  diabetes_type: 9,
  diagnosed_date: null,
  is_pregnant: false,
  family_history: true,
  is_insulin_taken: false,
  smoking: 1,
  alcohol: 1,
  exercise_freq: 1,
  diet_type: 'balanced',
  test_source: 1,
  recorded_at: '2026-06-20T09:00:00+08:00',
  family_histories: [
    { relation: '父亲', member_age: 68, is_alive: true, disease_name: '2型糖尿病', is_diabetes: true, diagnosed_age: 55, note: '' },
  ],
  medical_histories: [
    { disease_name: '高血压', diagnosed_date: '2020-03-01', status: 2, note: '药物控制中' },
  ],
  medications: [
    { drug_name: '二甲双胍', dosage: '0.5g', frequency_desc: '每日两次', is_insulin: false, status: 1 },
  ],
}

export const mockRiskResult = {
  assessment_id: 'ra_001',
  risk_level: 'medium',
  risk_score: 62,
  bmi: 24.9,
  bmi_level: 'overweight',
  glucose_level: 'prediabetes',
  factors: [
    { name: '超重', level: '中危', description: 'BMI 24.9 属于超重范围', weight: 0.3 },
    { name: '空腹血糖异常', level: '高危', description: '空腹血糖6.5mmol/L属于糖尿病前期', weight: 0.35 },
    { name: '家族史', level: '中危', description: '有糖尿病家族史', weight: 0.2 },
    { name: '运动不足', level: '低危', description: '运动频率偏低', weight: 0.15 },
  ],
  suggestions: [
    '建议进一步做OGTT检查确认糖耐量状态',
    '建议每周至少150分钟中等强度有氧运动',
    '建议咨询营养师制定个性化饮食方案',
  ],
  confidence: 'medium',
  assessed_at: '2026-06-20T15:30:00+08:00',
}

export const mockHealthPlan = {
  plan_id: 'plan_001',
  title: '个性化健康管理方案',
  summary: '基于您的健康画像，为您定制低 GI 饮食与规律运动计划。',
  daily_calories: 1800,
  version: 2,
  generated_at: '2026-06-21T08:00:00+08:00',
  is_favorite: 0,
  diet_plan: {
    meal_plan: {
      breakfast: {
        time: '07:00-08:00',
        foods: [
          { name: '全麦面包', amount: '2片', calories: 160, gi_level: 'low' },
          { name: '水煮蛋', amount: '1个', calories: 70, gi_level: 'low' },
          { name: '脱脂牛奶', amount: '200ml', calories: 120, gi_level: 'low' },
        ],
        total_calories: 350,
      },
      lunch: {
        time: '12:00-13:00',
        foods: [
          { name: '糙米饭', amount: '100g', calories: 210, gi_level: 'medium' },
          { name: '清蒸鱼', amount: '150g', calories: 120, gi_level: 'low' },
          { name: '炒青菜', amount: '200g', calories: 60, gi_level: 'low' },
        ],
        total_calories: 550,
      },
      dinner: {
        time: '18:00-19:00',
        foods: [
          { name: '杂粮粥', amount: '1碗', calories: 140, gi_level: 'medium' },
          { name: '凉拌豆腐', amount: '150g', calories: 80, gi_level: 'low' },
          { name: '炒西兰花', amount: '200g', calories: 50, gi_level: 'low' },
        ],
        total_calories: 450,
      },
      snack: {
        time: '15:00-16:00',
        foods: [
          { name: '苹果', amount: '半个', calories: 60, gi_level: 'low' },
          { name: '坚果', amount: '10g', calories: 60, gi_level: 'low' },
        ],
        total_calories: 120,
      },
    },
    diet_principles: [
      '控制总热量摄入，每日约1800kcal',
      '选择低GI食物，避免精制糖',
      '三餐定时定量，适当加餐',
    ],
    foods_to_avoid: ['含糖饮料', '糕点甜点', '油炸食品'],
    foods_to_recommend: ['全谷物', '绿叶蔬菜', '优质蛋白', '低糖水果'],
  },
  exercise_plan: {
    weekly_goal: '每周至少150分钟中等强度有氧运动',
    items: [
      { type: '快走', duration: '30分钟', frequency: '每日', intensity: '中等', calories_burned: 150, caution: '餐后1小时进行' },
      { type: '力量训练', duration: '20分钟', frequency: '每周3次', intensity: '轻度', calories_burned: 100, caution: '避免空腹' },
    ],
  },
  rest_plan: {
    wake_up: '06:30',
    sleep: '22:30',
    nap: '午休20-30分钟',
    glucose_monitor_times: ['空腹', '早餐后2h', '晚餐前', '睡前'],
    routine_tips: ['固定作息时间，避免熬夜'],
  },
  medication_note: '请遵医嘱按时服用降糖药物，注意监测低血糖症状。请在医生指导下执行。',
}

export const mockAchievements = [
  { id: 'first_checkin', name: '初来乍到', unlocked: true, unlocked_at: '2024-01-05', badge_url: '' },
  { id: 'streak_7', name: '坚持不懈', unlocked: true, unlocked_at: '2024-01-12', badge_url: '' },
  { id: 'exercise_master', name: '运动达人', unlocked: true, unlocked_at: '2024-01-18', badge_url: '' },
  { id: 'food_expert', name: '饮食专家', unlocked: true, unlocked_at: '2024-01-20', badge_url: '' },
  { id: 'glucose_guard', name: '血糖卫士', unlocked: true, unlocked_at: '2024-01-22', badge_url: '' },
  { id: 'early_bird', name: '早起鸟', unlocked: true, unlocked_at: '2024-01-25', badge_url: '' },
  { id: 'century', name: '百日坚持', unlocked: false, badge_url: '' },
  { id: 'medication_streak', name: '药不能停', unlocked: false, badge_url: '' },
  { id: 'calorie_master', name: '热量掌控者', unlocked: false, badge_url: '' },
  { id: 'all_rounder', name: '全能健将', unlocked: false, badge_url: '' },
  { id: 'night_diner', name: '深夜食堂', unlocked: false, badge_url: '' },
  { id: 'perfect_day', name: '完美一周', unlocked: false, badge_url: '' },
  // 后端兼容字段
  { name: '首次打卡', unlocked: true, badge_url: '' },
  { name: '连续打卡达人', unlocked: true, badge_url: '' },
]

/** mock 模式下成就墙进度统计 */
export const mockAchievementStats = {
  total_checkins: 156,
  streak_days: 12,
  exercise_count: 30,
  food_count: 100,
  medication_count: 32,
  glucose_count: 45,
  medication_streak: 18,
  glucose_streak: 7,
  early_bird_streak: 7,
  calorie_streak: 4,
  dinner_count: 13,
  perfect_days: 0,
}

export const mockTodayCheckins = [
  { checkin_type: 'diet', checkin_date: '2026-06-24', points_earned: 10 },
  { checkin_type: 'exercise', checkin_date: '2026-06-24', points_earned: 10 },
]

export const mockQaAnswer = `## 糖尿病患者可以吃水果吗？

**可以适量食用**，但需要注意以下几点：

1. **选择低 GI 水果**：如苹果、梨、柚子、草莓等
2. **控制份量**：每次约一个拳头大小，最好在两餐之间食用
3. **监测血糖**：食用后注意观察血糖变化
4. **避免果汁**：果汁糖分吸收快，容易引起血糖波动

**参考来源**：《中国2型糖尿病防治指南》2024版

⚠️ 以上内容仅供参考，不能替代专业医生的诊断和治疗建议。`

export const mockAiSummary = '您本周饮食打卡完成率85%，运动打卡比上周增加2天。建议保持规律作息，餐后1小时进行适度运动，有助于稳定血糖。'

export const mockConsultMessages = [
  { message_id: 'msg_1', sender_type: 'user', content: '医生您好，最近空腹血糖一直在7.5左右，需要调整用药吗？', sent_at: '2026-06-24T09:00:00+08:00' },
  { message_id: 'msg_2', sender_type: 'doctor', content: '您好，请问您目前的用药方案是什么？最近饮食和运动有变化吗？', sent_at: '2026-06-24T09:05:00+08:00' },
]
