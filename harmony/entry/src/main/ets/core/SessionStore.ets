import { common } from '@kit.AbilityKit'
import { preferences } from '@kit.ArkData'
import { StoredSession, TokenBundle, emptySession } from './Models'

const STORE_NAME = 'clipbridge_session'

const KEY_BASE_URL = 'base_url'
const KEY_ACCESS_TOKEN = 'access_token'
const KEY_REFRESH_TOKEN = 'refresh_token'
const KEY_CURRENT_DEVICE_ID = 'current_device_id'
const KEY_USERNAME = 'username'
const KEY_DEVICE_NAME = 'device_name'
const KEY_IS_ADMIN = 'is_admin'
const KEY_STORAGE_QUOTA_BYTES = 'storage_quota_bytes'
const KEY_STORAGE_USED_BYTES = 'storage_used_bytes'
const KEY_STORAGE_FREE_BYTES = 'storage_free_bytes'
const KEY_UPLOAD_BANDWIDTH_KBPS = 'upload_bandwidth_kbps'
const KEY_DOWNLOAD_BANDWIDTH_KBPS = 'download_bandwidth_kbps'
const KEY_MAX_UPLOAD_FILE_BYTES = 'max_upload_file_bytes'
const KEY_SYNC_ENABLED = 'sync_enabled'
const KEY_LAST_ACK_SEQ = 'last_ack_seq'

export class SessionStore {
  private readonly store: preferences.Preferences

  constructor(context: common.UIAbilityContext) {
    this.store = preferences.getPreferencesSync(context, { name: STORE_NAME })
  }

  readSession(): StoredSession {
    const session = emptySession()
    session.baseUrl = this.readString(KEY_BASE_URL)
    session.accessToken = this.readString(KEY_ACCESS_TOKEN)
    session.refreshToken = this.readString(KEY_REFRESH_TOKEN)
    session.currentDeviceId = this.readString(KEY_CURRENT_DEVICE_ID)
    session.username = this.readString(KEY_USERNAME)
    session.deviceName = this.readString(KEY_DEVICE_NAME)
    session.isAdmin = this.readBoolean(KEY_IS_ADMIN)
    session.storageQuotaBytes = this.readNumber(KEY_STORAGE_QUOTA_BYTES)
    session.storageUsedBytes = this.readNumber(KEY_STORAGE_USED_BYTES)
    session.storageFreeBytes = this.readNumber(KEY_STORAGE_FREE_BYTES)
    session.uploadBandwidthKbps = this.readNumber(KEY_UPLOAD_BANDWIDTH_KBPS)
    session.downloadBandwidthKbps = this.readNumber(KEY_DOWNLOAD_BANDWIDTH_KBPS)
    session.maxUploadFileBytes = this.readNumber(KEY_MAX_UPLOAD_FILE_BYTES)
    return session
  }

  isSyncEnabled(): boolean {
    return this.readBoolean(KEY_SYNC_ENABLED)
  }

  readLastAckSeq(): number {
    return this.readNumber(KEY_LAST_ACK_SEQ)
  }

  saveBaseUrl(baseUrl: string): void {
    this.putString(KEY_BASE_URL, baseUrl)
  }

  saveDeviceName(deviceName: string): void {
    // 设备名会在登录页和设备改名后复用，所以单独提供一个保存入口。
    this.putString(KEY_DEVICE_NAME, deviceName)
  }

  saveSyncEnabled(enabled: boolean): void {
    this.store.putSync(KEY_SYNC_ENABLED, enabled)
    this.flushSoon()
  }

  saveLastAckSeq(seq: number): void {
    this.store.putSync(KEY_LAST_ACK_SEQ, Math.max(0, Math.floor(seq)))
    this.flushSoon()
  }

  saveAuthBundle(session: StoredSession, tokens: TokenBundle): void {
    // 登录成功时一次写入所有核心字段，避免出现只有 token 没有设备 ID 的半登录态。
    this.store.putSync(KEY_BASE_URL, session.baseUrl)
    this.store.putSync(KEY_USERNAME, session.username)
    this.store.putSync(KEY_DEVICE_NAME, session.deviceName)
    this.store.putSync(KEY_CURRENT_DEVICE_ID, session.currentDeviceId)
    this.store.putSync(KEY_IS_ADMIN, session.isAdmin)
    this.store.putSync(KEY_STORAGE_QUOTA_BYTES, session.storageQuotaBytes)
    this.store.putSync(KEY_STORAGE_USED_BYTES, session.storageUsedBytes)
    this.store.putSync(KEY_STORAGE_FREE_BYTES, session.storageFreeBytes)
    this.store.putSync(KEY_UPLOAD_BANDWIDTH_KBPS, session.uploadBandwidthKbps)
    this.store.putSync(KEY_DOWNLOAD_BANDWIDTH_KBPS, session.downloadBandwidthKbps)
    this.store.putSync(KEY_MAX_UPLOAD_FILE_BYTES, session.maxUploadFileBytes)
    this.store.putSync(KEY_ACCESS_TOKEN, tokens.accessToken)
    this.store.putSync(KEY_REFRESH_TOKEN, tokens.refreshToken)
    this.flushSoon()
  }

  saveProfileSnapshot(session: StoredSession): void {
    this.store.putSync(KEY_USERNAME, session.username)
    this.store.putSync(KEY_CURRENT_DEVICE_ID, session.currentDeviceId)
    this.store.putSync(KEY_DEVICE_NAME, session.deviceName)
    this.store.putSync(KEY_IS_ADMIN, session.isAdmin)
    this.store.putSync(KEY_STORAGE_QUOTA_BYTES, session.storageQuotaBytes)
    this.store.putSync(KEY_STORAGE_USED_BYTES, session.storageUsedBytes)
    this.store.putSync(KEY_STORAGE_FREE_BYTES, session.storageFreeBytes)
    this.store.putSync(KEY_UPLOAD_BANDWIDTH_KBPS, session.uploadBandwidthKbps)
    this.store.putSync(KEY_DOWNLOAD_BANDWIDTH_KBPS, session.downloadBandwidthKbps)
    this.store.putSync(KEY_MAX_UPLOAD_FILE_BYTES, session.maxUploadFileBytes)
    this.flushSoon()
  }

  updateTokens(tokens: TokenBundle): void {
    this.store.putSync(KEY_ACCESS_TOKEN, tokens.accessToken)
    this.store.putSync(KEY_REFRESH_TOKEN, tokens.refreshToken)
    this.flushSoon()
  }

  clearAuth(): void {
    // 退出登录保留服务地址和设备名，便于用户重新登录。
    this.store.deleteSync(KEY_ACCESS_TOKEN)
    this.store.deleteSync(KEY_REFRESH_TOKEN)
    this.store.deleteSync(KEY_CURRENT_DEVICE_ID)
    this.store.deleteSync(KEY_USERNAME)
    this.store.deleteSync(KEY_IS_ADMIN)
    this.store.deleteSync(KEY_STORAGE_QUOTA_BYTES)
    this.store.deleteSync(KEY_STORAGE_USED_BYTES)
    this.store.deleteSync(KEY_STORAGE_FREE_BYTES)
    this.store.deleteSync(KEY_UPLOAD_BANDWIDTH_KBPS)
    this.store.deleteSync(KEY_DOWNLOAD_BANDWIDTH_KBPS)
    this.store.deleteSync(KEY_MAX_UPLOAD_FILE_BYTES)
    this.store.deleteSync(KEY_LAST_ACK_SEQ)
    this.flushSoon()
  }

  private readString(key: string): string {
    return String(this.store.getSync(key, ''))
  }

  private readNumber(key: string): number {
    const value = this.store.getSync(key, 0)
    if (typeof value === 'number') {
      return value
    }
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : 0
  }

  private readBoolean(key: string): boolean {
    const value = this.store.getSync(key, false)
    if (typeof value === 'boolean') {
      return value
    }
    return String(value) === 'true'
  }

  private putString(key: string, value: string): void {
    this.store.putSync(key, value)
    this.flushSoon()
  }

  private flushSoon(): void {
    // flush 异步落盘即可，页面状态已经同步更新，不需要阻塞用户操作。
    this.store.flush().catch(() => {})
  }
}
