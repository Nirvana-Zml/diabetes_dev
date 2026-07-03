import { ref, computed } from 'vue'
import dayjs from 'dayjs'
import { buildDateDisplay } from '../checkin/utils'

const checkinDate = ref(dayjs().format('YYYY-MM-DD'))

export function useCheckinDate() {
  const isToday = computed(() => checkinDate.value === dayjs().format('YYYY-MM-DD'))
  const dateDisplay = computed(() => buildDateDisplay(checkinDate.value))

  function changeDate(delta) {
    checkinDate.value = dayjs(checkinDate.value).add(delta, 'day').format('YYYY-MM-DD')
  }

  function setCheckinDate(dateStr) {
    if (dateStr) checkinDate.value = dateStr
  }

  return { checkinDate, isToday, dateDisplay, changeDate, setCheckinDate }
}
