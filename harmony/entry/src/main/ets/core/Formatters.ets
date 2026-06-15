export function formatBytes(value: number): string {
  if (value <= 0) {
    return '0 B'
  }
  if (value < 1024) {
    return `${Math.round(value)} B`
  }

  const kb = 1024
  const mb = kb * 1024
  const gb = mb * 1024

  if (value < mb) {
    return `${(value / kb).toFixed(1)} KB`
  }
  if (value < gb) {
    return `${(value / mb).toFixed(1)} MB`
  }
  return `${(value / gb).toFixed(1)} GB`
}

export function formatKbps(value: number): string {
  if (value <= 0) {
    return '-'
  }
  if (value < 1024) {
    return `${value} Kbps`
  }
  return `${(value / 1024).toFixed(1)} Mbps`
}

export function formatTime(value: string): string {
  if (value.trim().length === 0) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hour = pad(date.getHours())
  const minute = pad(date.getMinutes())
  return `${month}-${day} ${hour}:${minute}`
}

export function statusLabel(status: string): string {
  const normalized = status.trim().toLowerCase()
  if (normalized === 'active') {
    return '可访问'
  }
  if (normalized === 'expired') {
    return '已过期'
  }
  if (normalized === 'consumed') {
    return '已焚毁'
  }
  if (normalized === 'revoked') {
    return '已撤销'
  }
  if (normalized === 'approved') {
    return '已批准'
  }
  if (normalized === 'rejected') {
    return '已拒绝'
  }
  if (normalized === 'pending') {
    return '待处理'
  }
  return status.length > 0 ? status : '未知状态'
}

export function remainingSecondsText(value: number): string {
  if (value <= 0) {
    return '-'
  }
  if (value < 60) {
    return `${value} 秒`
  }
  if (value < 3600) {
    return `${Math.floor(value / 60)} 分钟`
  }
  return `${Math.floor(value / 3600)} 小时`
}

export function publicShareLink(baseUrl: string, token: string): string {
  const normalizedBaseUrl = baseUrl.trim().replace(/\/$/, '')
  if (normalizedBaseUrl.length === 0 || token.trim().length === 0) {
    return ''
  }
  return `${normalizedBaseUrl}/share.html?token=${encodeURIComponent(token.trim())}`
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : `${value}`
}
