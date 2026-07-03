import { h } from 'vue'
import { ElNotification, ElButton } from 'element-plus'
import { MOBILE_MAX } from '@/composables/useBreakpoints'

export function isMobileViewport() {
  if (typeof window === 'undefined') return false
  return window.matchMedia(`(max-width: ${MOBILE_MAX}px)`).matches
}

/**
 * 站内 ElNotification 封装，手机端为顶部紧凑卡片样式
 * @param {object} options
 * @param {string} options.title
 * @param {'success'|'warning'|'info'|'error'} [options.type]
 * @param {string} [options.summary]
 * @param {Array<{ label: string, type?: string, size?: string, onClick: (e: Event) => void }>} options.actions
 * @param {number} [options.duration]
 * @param {() => void} [options.onClose]
 */
export function showInAppNotification({
  title,
  type = 'success',
  summary = '',
  actions = [],
  duration = 8000,
  onClose,
}) {
  const mobile = isMobileViewport()

  const notifyInstance = ElNotification({
    title,
    type,
    position: 'top-right',
    customClass: mobile ? 'in-app-notification in-app-notification--mobile' : 'in-app-notification',
    offset: mobile ? 0 : 20,
    message: h('div', { class: 'in-app-notification__body' }, [
      h('p', { class: 'in-app-notification__summary' }, summary),
      h(
        'div',
        { class: 'in-app-notification__actions' },
        actions.map((action) => h(ElButton, {
          type: action.type || 'default',
          size: action.size || 'small',
          onClick: action.onClick,
        }, () => action.label)),
      ),
    ]),
    duration,
    showClose: true,
    onClose,
  })

  return notifyInstance
}

/** @internal 供单元测试读取按钮节点 */
export function getNotificationActionButtons(messageVNode) {
  const actionsContainer = messageVNode?.children?.[1]
  return actionsContainer?.children || []
}
