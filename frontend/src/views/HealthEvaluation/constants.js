/** 健康评估问卷常量（与 DIABETES_HEALTH 表字段对齐） */

export const GENDER_LABELS = { 0: '未知', 1: '男', 2: '女', male: '男', female: '女', unknown: '未知' }

export const DIABETES_TYPE_OPTIONS = [
  { value: 0, label: '无' },
  { value: 1, label: '糖尿病前期' },
  { value: 2, label: '1型糖尿病' },
  { value: 3, label: '2型糖尿病' },
  { value: 4, label: '妊娠糖尿病' },
  { value: 9, label: '未知' },
]

export const SMOKING_OPTIONS = [
  { value: 0, label: '从不' },
  { value: 1, label: '曾经吸烟' },
  { value: 2, label: '目前吸烟' },
]

export const ALCOHOL_OPTIONS = [
  { value: 0, label: '从不' },
  { value: 1, label: '偶尔' },
  { value: 2, label: '规律饮酒' },
]

export const EXERCISE_OPTIONS = [
  { value: 0, label: '无' },
  { value: 1, label: '较少（每周1-2次）' },
  { value: 2, label: '中等（每周3-4次）' },
  { value: 3, label: '较多（每周5次以上）' },
]

export const DIET_OPTIONS = [
  { value: 'balanced', label: '均衡饮食' },
  { value: 'high_sugar', label: '高糖饮食' },
  { value: 'high_fat', label: '高脂饮食' },
]

/** 将 high-sugar / high-fat 等历史值统一为表单使用的下划线写法 */
export function normalizeDietType(value) {
  if (!value) return 'balanced'
  if (value === 'high-sugar') return 'high_sugar'
  if (value === 'high-fat') return 'high_fat'
  return value
}

export const TEST_SOURCE_OPTIONS = [
  { value: 1, label: '自测' },
  { value: 2, label: '医院检查' },
]

export const RELATION_OPTIONS = ['父亲', '母亲', '兄弟姐妹', '子女', '祖父母', '外祖父母', '其他']

export const MEDICAL_STATUS_OPTIONS = [
  { value: 1, label: '进行中' },
  { value: 2, label: '已控制' },
  { value: 3, label: '已治愈' },
]

export const MEDICATION_STATUS_OPTIONS = [
  { value: 1, label: '在用' },
  { value: 2, label: '停用' },
]

export const QUESTIONNAIRE_STEPS = [
  { title: '基本信息', desc: '系统自动读取' },
  { title: '体征指标', desc: '身高体重与血糖' },
  { title: '生活方式', desc: '吸烟饮酒与运动' },
  { title: '糖尿病状态', desc: '分型与妊娠' },
  { title: '家族病史', desc: '亲属疾病史' },
  { title: '既往病史', desc: '个人疾病史' },
  { title: '用药情况', desc: '当前用药' },
]

export function emptyFamilyHistory() {
  return { relation: '父亲', member_age: null, is_alive: true, disease_name: '糖尿病', is_diabetes: true, diagnosed_age: null, note: '' }
}

export function emptyMedicalHistory() {
  return { disease_name: '', diagnosed_date: null, status: 1, note: '' }
}

export function emptyMedication() {
  return { drug_name: '', dosage: '', frequency_desc: '', is_insulin: false, status: 1 }
}

export function createDefaultForm() {
  return {
    height: 170,
    weight: 72,
    fasting_glucose: 5.6,
    postprandial_glucose: null,
    random_glucose: null,
    hba1c: null,
    systolic_bp: 120,
    diastolic_bp: 80,
    diabetes_type: 9,
    diagnosed_date: null,
    is_pregnant: false,
    family_history: false,
    is_insulin_taken: false,
    smoking: 0,
    alcohol: 0,
    exercise_freq: 1,
    diet_type: 'balanced',
    test_source: 1,
    family_histories: [],
    medical_histories: [],
    medications: [],
  }
}

export function calcAge(birthDate) {
  if (!birthDate) return null
  const birth = new Date(birthDate)
  if (Number.isNaN(birth.getTime())) return null
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const m = today.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age -= 1
  return age
}

export function genderLabel(gender) {
  if (typeof gender === 'string') return GENDER_LABELS[gender] || gender
  return GENDER_LABELS[gender] ?? '未知'
}
