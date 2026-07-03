export const GENDER_LABELS = { male: '男', female: '女', unknown: '未知' }

/** 与后端 DIABETES_TYPE 整型及健康评估问卷一致 */
export const DIABETES_TYPE_LABELS = {
  0: '无',
  1: '糖尿病前期',
  2: '1型糖尿病',
  3: '2型糖尿病',
  4: '妊娠糖尿病',
  9: '未知',
}

export const DIABETES_TYPE_OPTIONS = [
  { value: 0, label: '无' },
  { value: 1, label: '糖尿病前期' },
  { value: 2, label: '1型糖尿病' },
  { value: 3, label: '2型糖尿病' },
  { value: 4, label: '妊娠糖尿病' },
  { value: 9, label: '未知' },
]

export const SMOKING_LABELS = {
  0: '从不',
  1: '曾经吸烟',
  2: '目前吸烟',
}

export const SMOKING_OPTIONS = [
  { value: 0, label: '从不' },
  { value: 1, label: '曾经吸烟' },
  { value: 2, label: '目前吸烟' },
]

export const EXERCISE_LABELS = {
  0: '无',
  1: '较少',
  2: '中等',
  3: '较多',
}

export const EXERCISE_OPTIONS = [
  { value: 0, label: '无' },
  { value: 1, label: '较少（每周1-2次）' },
  { value: 2, label: '中等（每周3-4次）' },
  { value: 3, label: '较多（每周5次以上）' },
]

export const DIET_LABELS = {
  balanced: '均衡饮食',
  high_fat: '高脂饮食',
  high_sugar: '高糖饮食',
  'high-fat': '高脂饮食',
  'high-sugar': '高糖饮食',
}

export const DIET_OPTIONS = [
  { value: 'balanced', label: '均衡饮食' },
  { value: 'high_sugar', label: '高糖饮食' },
  { value: 'high_fat', label: '高脂饮食' },
]

export const CONSULT_STATUS_LABELS = { active: '进行中', closed: '已结束' }
