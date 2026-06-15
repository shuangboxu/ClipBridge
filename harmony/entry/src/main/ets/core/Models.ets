export type JsonPrimitive = string | number | boolean | null
export type JsonValue = JsonPrimitive | Object | Array<Object>
export type JsonArray = Array<Object>
export type JsonObject = Record<string, JsonValue>

export interface TokenBundle {
  accessToken: string
  refreshToken: string
}

export interface StoredSession {
  baseUrl: string
  accessToken: string
  refreshToken: string
  currentDeviceId: string
  username: string
  deviceName: string
  isAdmin: boolean
  storageQuotaBytes: number
  storageUsedBytes: number
  storageFreeBytes: number
  uploadBandwidthKbps: number
  downloadBandwidthKbps: number
  maxUploadFileBytes: number
}

export interface SystemLimitsRecord {
  maxUserCount: number
  defaultStorageQuotaBytes: number
  defaultUploadBandwidthKbps: number
  defaultDownloadBandwidthKbps: number
  maxUserUploadBandwidthKbps: number
  maxUserDownloadBandwidthKbps: number
  maxUploadFileBytes: number
  allowRegistration: boolean
}

export interface AuthResult {
  username: string
  currentDeviceId: string
  deviceName: string
  platform: string
  isAdmin: boolean
  storageQuotaBytes: number
  uploadBandwidthKbps: number
  downloadBandwidthKbps: number
  tokens: TokenBundle
}

export interface AccountProfile {
  username: string
  currentDeviceId: string
  isAdmin: boolean
  storageQuotaBytes: number
  storageUsedBytes: number
  storageFreeBytes: number
  uploadBandwidthKbps: number
  downloadBandwidthKbps: number
  maxUploadFileBytes: number
  limits: SystemLimitsRecord
}

export interface DeviceRecord {
  id: string
  platform: string
  deviceName: string
  lastSeenAt: string
  isActive: boolean
  createdAt: string
}

export interface ClipboardItem {
  id: string
  seq: number
  contentType: string
  textContent: string
  originDeviceId: string
  isCurrentDeviceOrigin: boolean
  createdAt: string
}

export interface ClipboardHistoryResult {
  items: ClipboardItem[]
  hasMore: boolean
  latestSeq: number
  currentDeviceAckSeq: number
}

export interface ClipboardHistorySettings {
  retentionDays: number
  historyLimit: number
  updatedAt: string
}

export interface ClipboardHistoryCleanupResult {
  deletedCount: number
  settings: ClipboardHistorySettings
  latestSeq: number
  currentDeviceAckSeq: number
}

export interface SyncPullResult {
  items: ClipboardItem[]
  nextSinceSeq: number
}

export interface FileRecord {
  id: string
  originalName: string
  contentType: string
  sizeBytes: number
  fileSha256: string
  originDeviceId: string
  originDeviceName: string
  createdAt: string
}

export interface FileListResult {
  files: FileRecord[]
  page: number
  totalPages: number
  totalFiles: number
  totalBytes: number
  maxUploadBytes: number
}

export interface ShareFileRecord {
  originalName: string
  contentType: string
  sizeBytes: number
  isImage: boolean
}

export interface ShareRecord {
  id: string
  token: string
  status: string
  contentKind: string
  hasTextContent: boolean
  hasFileContent: boolean
  isEncrypted: boolean
  requiresPassword: boolean
  textPreview: string
  file: ShareFileRecord | null
  allowCopyContent: boolean
  burnMode: string
  burnAfterSeconds: number
  remainingSeconds: number
  expiresAt: string
  firstOpenedAt: string
  burnDeadline: string
  consumedAt: string
  revokedAt: string
  openCount: number
  createdAt: string
  updatedAt: string
}

export interface ShareListResult {
  shares: ShareRecord[]
  page: number
  totalPages: number
  totalShares: number
  maxUploadBytes: number
}

export interface QuotaRequestRecord {
  id: string
  userId: string
  username: string
  requestedQuotaBytes: number
  currentQuotaBytes: number
  reason: string
  status: string
  reviewedBy: string
  reviewedByUsername: string
  reviewNote: string
  createdAt: string
  reviewedAt: string
}

export interface BandwidthRequestRecord {
  id: string
  userId: string
  username: string
  requestedUploadKbps: number
  requestedDownloadKbps: number
  currentUploadKbps: number
  currentDownloadKbps: number
  reason: string
  status: string
  reviewedBy: string
  reviewedByUsername: string
  reviewNote: string
  createdAt: string
  reviewedAt: string
}

export interface AdminRequestRecord {
  id: string
  userId: string
  username: string
  reason: string
  status: string
  reviewedBy: string
  reviewedByUsername: string
  reviewNote: string
  createdAt: string
  reviewedAt: string
}

export interface AdminSettingsRecord {
  maxUserCount: number
  defaultStorageQuotaBytes: number
  defaultUploadBandwidthKbps: number
  defaultDownloadBandwidthKbps: number
  maxUserUploadBandwidthKbps: number
  maxUserDownloadBandwidthKbps: number
  maxUploadFileBytes: number
  allowRegistration: boolean
  updatedAt: string
}

export interface AdminUserRecord {
  id: string
  username: string
  isAdmin: boolean
  storageQuotaBytes: number
  storageUsedBytes: number
  storageFreeBytes: number
  uploadBandwidthKbps: number
  downloadBandwidthKbps: number
  hasPendingQuotaRequest: boolean
  hasPendingBandwidthRequest: boolean
  hasPendingAdminRequest: boolean
  lastActiveAt: string
  createdAt: string
  updatedAt: string
}

export interface LocalFile {
  uri: string
  path: string
  displayName: string
  contentType: string
  sizeBytes: number
}

export enum ShareComposeMode {
  Text = 'text',
  File = 'file',
}

export enum ShareStrategyKey {
  Never = 'never',
  Expire = 'expire',
  Once = 'once',
}

export enum ShareStatusFilter {
  All = 'all',
  Active = 'active',
  Expired = 'expired',
  Consumed = 'consumed',
  Revoked = 'revoked',
}

export enum RequestStatusFilter {
  All = 'all',
  Pending = 'pending',
  Approved = 'approved',
  Rejected = 'rejected',
}

export enum ShareExpirePreset {
  OneHour = '1h',
  OneDay = '24h',
  SevenDays = '7d',
}

export enum ShareCountdownPreset {
  TenSeconds = '10s',
  ThirtySeconds = '30s',
  OneMinute = '60s',
  FiveMinutes = '300s',
}

export interface ShareNeverRule {
  allowCopyText: boolean
}

export interface ShareExpireRule {
  preset: ShareExpirePreset
  allowCopyText: boolean
}

export interface ShareOnceRule {
  showCountdown: boolean
  countdownPreset: ShareCountdownPreset
  allowCopyText: boolean
}

export interface ShareRuleConfig {
  never: ShareNeverRule
  expire: ShareExpireRule
  once: ShareOnceRule
}

export interface SharePolicyPayload {
  neverExpires: boolean
  expireSeconds: number
  burnMode: string
  burnAfterSeconds: number
  allowCopyContent: boolean
}

export interface ShareStrategySummary {
  title: string
  description: string
  copyLabel: string
}

export class ApiError extends Error {
  httpCode: number

  constructor(message: string, httpCode: number = 0) {
    super(message)
    this.httpCode = httpCode
  }
}

export function emptySession(): StoredSession {
  return {
    baseUrl: '',
    accessToken: '',
    refreshToken: '',
    currentDeviceId: '',
    username: '',
    deviceName: '',
    isAdmin: false,
    storageQuotaBytes: 0,
    storageUsedBytes: 0,
    storageFreeBytes: 0,
    uploadBandwidthKbps: 0,
    downloadBandwidthKbps: 0,
    maxUploadFileBytes: 0,
  }
}

export function emptySystemLimits(): SystemLimitsRecord {
  return {
    maxUserCount: 0,
    defaultStorageQuotaBytes: 0,
    defaultUploadBandwidthKbps: 0,
    defaultDownloadBandwidthKbps: 0,
    maxUserUploadBandwidthKbps: 0,
    maxUserDownloadBandwidthKbps: 0,
    maxUploadFileBytes: 0,
    allowRegistration: false,
  }
}

export function emptyHistorySettings(): ClipboardHistorySettings {
  return {
    retentionDays: 0,
    historyLimit: 1000,
    updatedAt: '',
  }
}

export function emptyAdminSettings(): AdminSettingsRecord {
  return {
    maxUserCount: 0,
    defaultStorageQuotaBytes: 0,
    defaultUploadBandwidthKbps: 0,
    defaultDownloadBandwidthKbps: 0,
    maxUserUploadBandwidthKbps: 0,
    maxUserDownloadBandwidthKbps: 0,
    maxUploadFileBytes: 0,
    allowRegistration: false,
    updatedAt: '',
  }
}

export function buildDefaultShareRules(): ShareRuleConfig {
  return {
    never: {
      allowCopyText: false,
    },
    expire: {
      preset: ShareExpirePreset.OneDay,
      allowCopyText: false,
    },
    once: {
      showCountdown: true,
      countdownPreset: ShareCountdownPreset.TenSeconds,
      allowCopyText: false,
    },
  }
}

export function hasCompleteAuth(session: StoredSession): boolean {
  return session.accessToken.length > 0 &&
    session.refreshToken.length > 0 &&
    session.currentDeviceId.length > 0
}

export function asObject(value: JsonValue | undefined): JsonObject {
  if (value === null || value === undefined || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }
  return value as JsonObject
}

export function asArray(value: JsonValue | undefined): JsonArray {
  if (!Array.isArray(value)) {
    return []
  }
  return value
}

export function readString(source: JsonObject, key: string, fallback: string = ''): string {
  const value = source[key]
  if (typeof value === 'string') {
    return value
  }
  if (value === null || value === undefined) {
    return fallback
  }
  return String(value)
}

export function readNumber(source: JsonObject, key: string, fallback: number = 0): number {
  const value = source[key]
  if (typeof value === 'number') {
    return value
  }
  if (typeof value === 'string') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : fallback
  }
  return fallback
}

export function readNullableNumber(source: JsonObject, key: string): number | null {
  const value = source[key]
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return value
  }
  if (typeof value === 'string') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

export function readBoolean(source: JsonObject, key: string, fallback: boolean = false): boolean {
  const value = source[key]
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'string') {
    return value === 'true'
  }
  return fallback
}

export function parseJsonObject(text: string): JsonObject {
  if (text.trim().length === 0) {
    return {}
  }
  const parsed = JSON.parse(text) as JsonValue
  return asObject(parsed)
}

export function normalizeBaseUrl(value: string): string {
  const trimmed = value.trim()
  if (trimmed.endsWith('/')) {
    return trimmed.substring(0, trimmed.length - 1)
  }
  return trimmed
}

export function defaultDeviceName(): string {
  return 'HarmonyOS Phone'
}

export function shareStatusFilters(): ShareStatusFilter[] {
  return [
    ShareStatusFilter.All,
    ShareStatusFilter.Active,
    ShareStatusFilter.Expired,
    ShareStatusFilter.Consumed,
    ShareStatusFilter.Revoked,
  ]
}

export function requestStatusFilters(): RequestStatusFilter[] {
  return [
    RequestStatusFilter.All,
    RequestStatusFilter.Pending,
    RequestStatusFilter.Approved,
    RequestStatusFilter.Rejected,
  ]
}

export function shareStrategyKeys(): ShareStrategyKey[] {
  return [
    ShareStrategyKey.Never,
    ShareStrategyKey.Expire,
    ShareStrategyKey.Once,
  ]
}

export function shareExpirePresets(): ShareExpirePreset[] {
  return [
    ShareExpirePreset.OneHour,
    ShareExpirePreset.OneDay,
    ShareExpirePreset.SevenDays,
  ]
}

export function shareCountdownPresets(): ShareCountdownPreset[] {
  return [
    ShareCountdownPreset.TenSeconds,
    ShareCountdownPreset.ThirtySeconds,
    ShareCountdownPreset.OneMinute,
    ShareCountdownPreset.FiveMinutes,
  ]
}

export function shareStrategyLabel(strategy: ShareStrategyKey): string {
  switch (strategy) {
    case ShareStrategyKey.Never:
      return '不过期'
    case ShareStrategyKey.Expire:
      return '定时过期'
    case ShareStrategyKey.Once:
      return '一次性'
    default:
      return '未知策略'
  }
}

export function shareStatusLabel(filter: ShareStatusFilter): string {
  switch (filter) {
    case ShareStatusFilter.All:
      return '全部'
    case ShareStatusFilter.Active:
      return '可访问'
    case ShareStatusFilter.Expired:
      return '已过期'
    case ShareStatusFilter.Consumed:
      return '已焚毁'
    case ShareStatusFilter.Revoked:
      return '已撤销'
    default:
      return '全部'
  }
}

export function requestStatusLabel(filter: RequestStatusFilter): string {
  switch (filter) {
    case RequestStatusFilter.All:
      return '全部'
    case RequestStatusFilter.Pending:
      return '待处理'
    case RequestStatusFilter.Approved:
      return '已批准'
    case RequestStatusFilter.Rejected:
      return '已拒绝'
    default:
      return '全部'
  }
}

export function shareExpireLabel(preset: ShareExpirePreset): string {
  switch (preset) {
    case ShareExpirePreset.OneHour:
      return '1 小时'
    case ShareExpirePreset.OneDay:
      return '24 小时'
    case ShareExpirePreset.SevenDays:
      return '7 天'
    default:
      return '24 小时'
  }
}

export function shareCountdownLabel(preset: ShareCountdownPreset): string {
  switch (preset) {
    case ShareCountdownPreset.TenSeconds:
      return '10 秒'
    case ShareCountdownPreset.ThirtySeconds:
      return '30 秒'
    case ShareCountdownPreset.OneMinute:
      return '60 秒'
    case ShareCountdownPreset.FiveMinutes:
      return '5 分钟'
    default:
      return '10 秒'
  }
}

export function buildSharePolicyPayload(
  rules: ShareRuleConfig,
  strategy: ShareStrategyKey,
  allowTextCopy: boolean,
): SharePolicyPayload {
  switch (strategy) {
    case ShareStrategyKey.Never:
      return {
        neverExpires: true,
        expireSeconds: 0,
        burnMode: 'none',
        burnAfterSeconds: 0,
        allowCopyContent: allowTextCopy && rules.never.allowCopyText,
      }
    case ShareStrategyKey.Expire:
      return {
        neverExpires: false,
        expireSeconds: toExpireSeconds(rules.expire.preset),
        burnMode: 'none',
        burnAfterSeconds: 0,
        allowCopyContent: allowTextCopy && rules.expire.allowCopyText,
      }
    case ShareStrategyKey.Once:
      return {
        neverExpires: true,
        expireSeconds: 0,
        burnMode: rules.once.showCountdown ? 'countdown' : 'once',
        burnAfterSeconds: rules.once.showCountdown ? toCountdownSeconds(rules.once.countdownPreset) : 0,
        allowCopyContent: allowTextCopy && rules.once.allowCopyText,
      }
    default:
      return {
        neverExpires: false,
        expireSeconds: 24 * 60 * 60,
        burnMode: 'none',
        burnAfterSeconds: 0,
        allowCopyContent: false,
      }
  }
}

export function buildShareStrategySummary(
  rules: ShareRuleConfig,
  strategy: ShareStrategyKey,
): ShareStrategySummary {
  switch (strategy) {
    case ShareStrategyKey.Never:
      return {
        title: '不过期',
        description: '公开链接不会自动过期，需要你手动撤销。',
        copyLabel: rules.never.allowCopyText ? '文字允许复制' : '文字禁止复制',
      }
    case ShareStrategyKey.Expire:
      return {
        title: '定时过期',
        description: `${shareExpireLabel(rules.expire.preset)} 后自动失效。`,
        copyLabel: rules.expire.allowCopyText ? '文字允许复制' : '文字禁止复制',
      }
    case ShareStrategyKey.Once:
      return {
        title: '一次性',
        description: rules.once.showCountdown
          ? `首次打开后开始 ${shareCountdownLabel(rules.once.countdownPreset)} 倒计时。`
          : '公开内容仅允许成功打开一次。',
        copyLabel: rules.once.allowCopyText ? '文字允许复制' : '文字禁止复制',
      }
    default:
      return {
        title: '未知策略',
        description: '',
        copyLabel: '',
      }
  }
}

export function parseTokenBundle(source: JsonObject): TokenBundle {
  return {
    accessToken: readString(source, 'access_token'),
    refreshToken: readString(source, 'refresh_token'),
  }
}

export function parseAuthResult(source: JsonObject): AuthResult {
  const user = asObject(source['user'])
  const device = asObject(source['device'])
  const tokens = asObject(source['tokens'])
  return {
    username: readString(user, 'username'),
    currentDeviceId: readString(device, 'id'),
    deviceName: readString(device, 'device_name'),
    platform: readString(device, 'platform', 'harmonyos'),
    isAdmin: readBoolean(user, 'is_admin'),
    storageQuotaBytes: readNumber(user, 'storage_quota_bytes'),
    uploadBandwidthKbps: readNumber(user, 'upload_bandwidth_kbps'),
    downloadBandwidthKbps: readNumber(user, 'download_bandwidth_kbps'),
    tokens: parseTokenBundle(tokens),
  }
}

export function parseAccountProfile(source: JsonObject): AccountProfile {
  const user = asObject(source['user'])
  const limits = asObject(source['limits'])
  return {
    username: readString(user, 'username'),
    currentDeviceId: readString(source, 'current_device_id'),
    isAdmin: readBoolean(user, 'is_admin'),
    storageQuotaBytes: readNumber(user, 'storage_quota_bytes'),
    storageUsedBytes: readNumber(source, 'storage_used_bytes'),
    storageFreeBytes: readNumber(source, 'storage_free_bytes'),
    uploadBandwidthKbps: readNumber(user, 'upload_bandwidth_kbps'),
    downloadBandwidthKbps: readNumber(user, 'download_bandwidth_kbps'),
    maxUploadFileBytes: readNumber(limits, 'max_upload_file_bytes'),
    limits: parseSystemLimits(limits),
  }
}

export function parseSystemLimits(source: JsonObject): SystemLimitsRecord {
  return {
    maxUserCount: readNumber(source, 'max_user_count'),
    defaultStorageQuotaBytes: readNumber(source, 'default_storage_quota_bytes'),
    defaultUploadBandwidthKbps: readNumber(source, 'default_upload_bandwidth_kbps'),
    defaultDownloadBandwidthKbps: readNumber(source, 'default_download_bandwidth_kbps'),
    maxUserUploadBandwidthKbps: readNumber(source, 'max_user_upload_bandwidth_kbps'),
    maxUserDownloadBandwidthKbps: readNumber(source, 'max_user_download_bandwidth_kbps'),
    maxUploadFileBytes: readNumber(source, 'max_upload_file_bytes'),
    allowRegistration: readBoolean(source, 'allow_registration'),
  }
}

export function parseDeviceRecord(source: JsonObject): DeviceRecord {
  return {
    id: readString(source, 'id'),
    platform: readString(source, 'platform', 'unknown'),
    deviceName: readString(source, 'device_name'),
    lastSeenAt: readString(source, 'last_seen_at'),
    isActive: readBoolean(source, 'is_active'),
    createdAt: readString(source, 'created_at'),
  }
}

export function parseClipboardItem(source: JsonObject): ClipboardItem {
  return {
    id: readString(source, 'id'),
    seq: readNumber(source, 'seq'),
    contentType: readString(source, 'content_type'),
    textContent: readString(source, 'text_content'),
    originDeviceId: readString(source, 'origin_device_id'),
    isCurrentDeviceOrigin: readBoolean(source, 'is_current_device_origin'),
    createdAt: readString(source, 'created_at'),
  }
}

export function parseHistorySettings(source: JsonObject): ClipboardHistorySettings {
  return {
    retentionDays: readNumber(source, 'retention_days'),
    historyLimit: readNumber(source, 'history_limit', 1000),
    updatedAt: readString(source, 'updated_at'),
  }
}

export function parseFileRecord(source: JsonObject): FileRecord {
  return {
    id: readString(source, 'id'),
    originalName: readString(source, 'original_name'),
    contentType: readString(source, 'content_type'),
    sizeBytes: readNumber(source, 'size_bytes'),
    fileSha256: readString(source, 'file_sha256'),
    originDeviceId: readString(source, 'origin_device_id'),
    originDeviceName: readString(source, 'origin_device_name'),
    createdAt: readString(source, 'created_at'),
  }
}

export function parseShareFileRecord(source: JsonObject): ShareFileRecord {
  return {
    originalName: readString(source, 'original_name'),
    contentType: readString(source, 'content_type'),
    sizeBytes: readNumber(source, 'size_bytes'),
    isImage: readBoolean(source, 'is_image'),
  }
}

export function parseShareRecord(source: JsonObject): ShareRecord {
  return {
    id: readString(source, 'id'),
    token: readString(source, 'token'),
    status: readString(source, 'status'),
    contentKind: readString(source, 'content_kind'),
    hasTextContent: readBoolean(source, 'has_text_content'),
    hasFileContent: readBoolean(source, 'has_file_content'),
    isEncrypted: readBoolean(source, 'is_encrypted'),
    requiresPassword: readBoolean(source, 'requires_password'),
    textPreview: readString(source, 'text_preview'),
    file: source['file'] ? parseShareFileRecord(asObject(source['file'])) : null,
    allowCopyContent: readBoolean(source, 'allow_copy_content'),
    burnMode: readString(source, 'burn_mode'),
    burnAfterSeconds: readNumber(source, 'burn_after_seconds'),
    remainingSeconds: readNumber(source, 'remaining_seconds'),
    expiresAt: readString(source, 'expires_at'),
    firstOpenedAt: readString(source, 'first_opened_at'),
    burnDeadline: readString(source, 'burn_deadline'),
    consumedAt: readString(source, 'consumed_at'),
    revokedAt: readString(source, 'revoked_at'),
    openCount: readNumber(source, 'open_count'),
    createdAt: readString(source, 'created_at'),
    updatedAt: readString(source, 'updated_at'),
  }
}

export function parseQuotaRequestRecord(source: JsonObject): QuotaRequestRecord {
  return {
    id: readString(source, 'id'),
    userId: readString(source, 'user_id'),
    username: readString(source, 'username'),
    requestedQuotaBytes: readNumber(source, 'requested_quota_bytes'),
    currentQuotaBytes: readNumber(source, 'current_quota_bytes'),
    reason: readString(source, 'reason'),
    status: readString(source, 'status'),
    reviewedBy: readString(source, 'reviewed_by'),
    reviewedByUsername: readString(source, 'reviewed_by_username'),
    reviewNote: readString(source, 'review_note'),
    createdAt: readString(source, 'created_at'),
    reviewedAt: readString(source, 'reviewed_at'),
  }
}

export function parseBandwidthRequestRecord(source: JsonObject): BandwidthRequestRecord {
  return {
    id: readString(source, 'id'),
    userId: readString(source, 'user_id'),
    username: readString(source, 'username'),
    requestedUploadKbps: readNumber(source, 'requested_upload_kbps'),
    requestedDownloadKbps: readNumber(source, 'requested_download_kbps'),
    currentUploadKbps: readNumber(source, 'current_upload_kbps'),
    currentDownloadKbps: readNumber(source, 'current_download_kbps'),
    reason: readString(source, 'reason'),
    status: readString(source, 'status'),
    reviewedBy: readString(source, 'reviewed_by'),
    reviewedByUsername: readString(source, 'reviewed_by_username'),
    reviewNote: readString(source, 'review_note'),
    createdAt: readString(source, 'created_at'),
    reviewedAt: readString(source, 'reviewed_at'),
  }
}

export function parseAdminRequestRecord(source: JsonObject): AdminRequestRecord {
  return {
    id: readString(source, 'id'),
    userId: readString(source, 'user_id'),
    username: readString(source, 'username'),
    reason: readString(source, 'reason'),
    status: readString(source, 'status'),
    reviewedBy: readString(source, 'reviewed_by'),
    reviewedByUsername: readString(source, 'reviewed_by_username'),
    reviewNote: readString(source, 'review_note'),
    createdAt: readString(source, 'created_at'),
    reviewedAt: readString(source, 'reviewed_at'),
  }
}

export function parseAdminSettingsRecord(source: JsonObject): AdminSettingsRecord {
  return {
    maxUserCount: readNumber(source, 'max_user_count'),
    defaultStorageQuotaBytes: readNumber(source, 'default_storage_quota_bytes'),
    defaultUploadBandwidthKbps: readNumber(source, 'default_upload_bandwidth_kbps'),
    defaultDownloadBandwidthKbps: readNumber(source, 'default_download_bandwidth_kbps'),
    maxUserUploadBandwidthKbps: readNumber(source, 'max_user_upload_bandwidth_kbps'),
    maxUserDownloadBandwidthKbps: readNumber(source, 'max_user_download_bandwidth_kbps'),
    maxUploadFileBytes: readNumber(source, 'max_upload_file_bytes'),
    allowRegistration: readBoolean(source, 'allow_registration'),
    updatedAt: readString(source, 'updated_at'),
  }
}

export function parseAdminUserRecord(source: JsonObject): AdminUserRecord {
  return {
    id: readString(source, 'id'),
    username: readString(source, 'username'),
    isAdmin: readBoolean(source, 'is_admin'),
    storageQuotaBytes: readNumber(source, 'storage_quota_bytes'),
    storageUsedBytes: readNumber(source, 'storage_used_bytes'),
    storageFreeBytes: readNumber(source, 'storage_free_bytes'),
    uploadBandwidthKbps: readNumber(source, 'upload_bandwidth_kbps'),
    downloadBandwidthKbps: readNumber(source, 'download_bandwidth_kbps'),
    hasPendingQuotaRequest: readBoolean(source, 'has_pending_quota_request'),
    hasPendingBandwidthRequest: readBoolean(source, 'has_pending_bandwidth_request'),
    hasPendingAdminRequest: readBoolean(source, 'has_pending_admin_request'),
    lastActiveAt: readString(source, 'last_active_at'),
    createdAt: readString(source, 'created_at'),
    updatedAt: readString(source, 'updated_at'),
  }
}

export function parseObjectList<T>(source: JsonArray, parser: (value: JsonObject) => T): T[] {
  const result: T[] = []
  for (let index = 0; index < source.length; index += 1) {
    result.push(parser(asObject(source[index])))
  }
  return result
}

export function shareExpirePresetFromValue(value: string): ShareExpirePreset {
  switch (value) {
    case ShareExpirePreset.OneHour:
      return ShareExpirePreset.OneHour
    case ShareExpirePreset.OneDay:
      return ShareExpirePreset.OneDay
    case ShareExpirePreset.SevenDays:
      return ShareExpirePreset.SevenDays
    default:
      return ShareExpirePreset.OneDay
  }
}

export function shareCountdownPresetFromValue(value: string): ShareCountdownPreset {
  switch (value) {
    case ShareCountdownPreset.TenSeconds:
      return ShareCountdownPreset.TenSeconds
    case ShareCountdownPreset.ThirtySeconds:
      return ShareCountdownPreset.ThirtySeconds
    case ShareCountdownPreset.OneMinute:
      return ShareCountdownPreset.OneMinute
    case ShareCountdownPreset.FiveMinutes:
      return ShareCountdownPreset.FiveMinutes
    default:
      return ShareCountdownPreset.TenSeconds
  }
}

function toExpireSeconds(preset: ShareExpirePreset): number {
  switch (preset) {
    case ShareExpirePreset.OneHour:
      return 60 * 60
    case ShareExpirePreset.OneDay:
      return 24 * 60 * 60
    case ShareExpirePreset.SevenDays:
      return 7 * 24 * 60 * 60
    default:
      return 24 * 60 * 60
  }
}

function toCountdownSeconds(preset: ShareCountdownPreset): number {
  switch (preset) {
    case ShareCountdownPreset.TenSeconds:
      return 10
    case ShareCountdownPreset.ThirtySeconds:
      return 30
    case ShareCountdownPreset.OneMinute:
      return 60
    case ShareCountdownPreset.FiveMinutes:
      return 300
    default:
      return 10
  }
}
