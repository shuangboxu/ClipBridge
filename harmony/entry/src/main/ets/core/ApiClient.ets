import { http } from '@kit.NetworkKit'
import {
  AccountProfile,
  AdminRequestRecord,
  AdminSettingsRecord,
  AdminUserRecord,
  ApiError,
  AuthResult,
  BandwidthRequestRecord,
  ClipboardHistoryCleanupResult,
  ClipboardHistoryResult,
  ClipboardItem,
  ClipboardHistorySettings,
  DeviceRecord,
  FileListResult,
  FileRecord,
  LocalFile,
  QuotaRequestRecord,
  RequestStatusFilter,
  SharePolicyPayload,
  ShareListResult,
  ShareRecord,
  ShareStatusFilter,
  StoredSession,
  SyncPullResult,
  TokenBundle,
  JsonObject,
  asArray,
  asObject,
  normalizeBaseUrl,
  parseAccountProfile,
  parseAdminRequestRecord,
  parseAdminSettingsRecord,
  parseAdminUserRecord,
  parseAuthResult,
  parseBandwidthRequestRecord,
  parseClipboardItem,
  parseDeviceRecord,
  parseFileRecord,
  parseHistorySettings,
  parseJsonObject,
  parseObjectList,
  parseQuotaRequestRecord,
  parseShareRecord,
  parseTokenBundle,
  readBoolean,
  readNumber,
  readNullableNumber,
  readString,
} from './Models'

interface JsonRequestConfig {
  baseUrl: string
  method: http.RequestMethod
  path: string
  accessToken?: string
  body?: RequestPayload
  multiFormDataList?: http.MultiFormData[]
}

class RequestPayload {
  username?: string
  password?: string
  platform?: string
  device_name?: string
  refresh_token?: string
  current_password?: string
  new_password?: string
  device_id?: string
  content_type?: string
  text_content?: string
  days?: number
  retention_days?: number
  history_limit?: number
  seq?: number
  original_name?: string
  never_expires?: boolean
  burn_mode?: string
  allow_copy_content?: boolean
  is_encrypted?: boolean
  expire_seconds?: number
  burn_after_seconds?: number
  requested_quota_mb?: number
  reason?: string
  requested_upload_kbps?: number
  requested_download_kbps?: number
  max_user_count?: number
  default_storage_quota_mb?: number
  default_upload_bandwidth_kbps?: number
  default_download_bandwidth_kbps?: number
  max_user_upload_bandwidth_kbps?: number
  max_user_download_bandwidth_kbps?: number
  max_upload_file_mb?: number
  allow_registration?: boolean
  storage_quota_mb?: number
  upload_bandwidth_kbps?: number
  download_bandwidth_kbps?: number
  is_admin?: boolean
  approved_quota_mb?: number
  review_note?: string
  approved_upload_kbps?: number
  approved_download_kbps?: number
}

type HeaderMap = Record<string, string>

interface JsonRequestResult {
  data: JsonObject
  tokens?: TokenBundle
}

interface BinaryRequestResult {
  data: ArrayBuffer
  tokens?: TokenBundle
}

interface DeviceOfflineResult {
  device: DeviceRecord
  currentDeviceForcedOffline: boolean
}

interface UploadTextResult {
  item: ClipboardItem
  deduplicated: boolean
}

interface DeleteClipboardResult {
  deletedCount: number
  latestSeq: number
  currentDeviceAckSeq: number
}

interface DeleteFileResult {
  file: FileRecord
  diskRemoved: boolean
}

interface AdminSettingsResult {
  settings: AdminSettingsRecord
  currentUserCount: number
}

interface DeleteUserResult {
  success: boolean
  deletedUserId: string
}

interface AdminSettingsUpdateInput {
  maxUserCount: number
  defaultStorageQuotaMb: number
  defaultUploadBandwidthKbps: number
  defaultDownloadBandwidthKbps: number
  maxUserUploadBandwidthKbps: number
  maxUserDownloadBandwidthKbps: number
  maxUploadFileMb: number
  allowRegistration: boolean
}

interface AdminUserUpdateInput {
  storageQuotaMb: number
  uploadBandwidthKbps: number
  downloadBandwidthKbps: number
  isAdmin: boolean
}

interface RequestErrorShape {
  code?: number
  message?: string
}

const PATCH_METHOD = 'PATCH' as http.RequestMethod
const HEADER_AUTHORIZATION = 'Authorization'
const HEADER_CONTENT_TYPE = 'Content-Type'
const CONTENT_TYPE_JSON = 'application/json'

export class ApiClient {
  private readonly getSession: () => StoredSession
  private readonly onTokensUpdated: (tokens: TokenBundle) => void

  constructor(getSession: () => StoredSession, onTokensUpdated: (tokens: TokenBundle) => void) {
    this.getSession = getSession
    this.onTokensUpdated = onTokensUpdated
  }

  async testConnection(baseUrl: string): Promise<void> {
    await this.requestJson({
      baseUrl: normalizeBaseUrl(baseUrl),
      method: http.RequestMethod.GET,
      path: '/healthz',
    })
  }

  async register(
    baseUrl: string,
    username: string,
    password: string,
    deviceName: string,
  ): Promise<AuthResult> {
    const body = new RequestPayload()
    body.username = username
    body.password = password
    body.platform = 'harmonyos'
    body.device_name = deviceName
    const data = await this.requestJson({
      baseUrl: normalizeBaseUrl(baseUrl),
      method: http.RequestMethod.POST,
      path: '/v1/auth/register',
      body,
    })
    return parseAuthResult(data)
  }

  async login(
    baseUrl: string,
    username: string,
    password: string,
    deviceName: string,
  ): Promise<AuthResult> {
    const body = new RequestPayload()
    body.username = username
    body.password = password
    body.platform = 'harmonyos'
    body.device_name = deviceName
    const data = await this.requestJson({
      baseUrl: normalizeBaseUrl(baseUrl),
      method: http.RequestMethod.POST,
      path: '/v1/auth/login',
      body,
    })
    return parseAuthResult(data)
  }

  async refresh(baseUrl: string, refreshToken: string): Promise<TokenBundle> {
    const body = new RequestPayload()
    body.refresh_token = refreshToken
    const data = await this.requestJson({
      baseUrl: normalizeBaseUrl(baseUrl),
      method: http.RequestMethod.POST,
      path: '/v1/auth/refresh',
      body,
    })
    return parseTokenBundle(asObject(data.tokens))
  }

  async logout(): Promise<void> {
    const session = this.getSession()
    if (session.accessToken.trim().length === 0) {
      return
    }

    try {
      const body = new RequestPayload()
      body.refresh_token = session.refreshToken
      await this.requestJson({
        baseUrl: session.baseUrl,
        method: http.RequestMethod.POST,
        path: '/v1/auth/logout',
        accessToken: session.accessToken,
        body,
      })
    } catch (_) {
      // 退出登录失败时不阻塞本地清理，避免网络问题导致用户退不出去。
    }
  }

  async getCurrentAccount(): Promise<AccountProfile> {
    const result = await this.requestAuthenticatedJson(http.RequestMethod.GET, '/v1/account/me')
    return parseAccountProfile(result.data)
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    const body = new RequestPayload()
    body.current_password = currentPassword
    body.new_password = newPassword
    await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/account/password', body)
  }

  async listDevices(): Promise<DeviceRecord[]> {
    const result = await this.requestAuthenticatedJson(http.RequestMethod.GET, '/v1/devices')
    return parseObjectList(asArray(result.data.devices), parseDeviceRecord)
  }

  async updateDeviceName(deviceId: string, deviceName: string): Promise<DeviceRecord> {
    const body = new RequestPayload()
    body.device_id = deviceId
    body.device_name = deviceName
    const result = await this.requestAuthenticatedJson(PATCH_METHOD, '/v1/devices', body)
    return parseDeviceRecord(asObject(result.data.device))
  }

  async forceOfflineDevice(deviceId: string): Promise<DeviceOfflineResult> {
    const body = new RequestPayload()
    body.device_id = deviceId
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/devices/offline', body)
    return {
      device: parseDeviceRecord(asObject(result.data.device)),
      currentDeviceForcedOffline: readBoolean(result.data, 'current_device_forced_offline'),
    }
  }

  async uploadText(text: string): Promise<UploadTextResult> {
    const body = new RequestPayload()
    body.content_type = 'text'
    body.text_content = text
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/clipboard/items', body)
    return {
      item: parseClipboardItem(asObject(result.data.item)),
      deduplicated: readBoolean(result.data, 'deduplicated'),
    }
  }

  async listClipboardItems(limit: number, beforeSeq: number | null): Promise<ClipboardHistoryResult> {
    const query = beforeSeq === null ? `?limit=${limit}` : `?limit=${limit}&before_seq=${beforeSeq}`
    const result = await this.requestAuthenticatedJson(http.RequestMethod.GET, `/v1/clipboard/items${query}`)
    return {
      items: parseObjectList(asArray(result.data.items), parseClipboardItem),
      hasMore: readBoolean(result.data, 'has_more'),
      latestSeq: readNumber(result.data, 'latest_seq'),
      currentDeviceAckSeq: readNumber(result.data, 'current_device_ack_seq'),
    }
  }

  async deleteClipboardItem(itemId: string): Promise<DeleteClipboardResult> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.DELETE,
      `/v1/clipboard/items/${encodeURIComponent(itemId.trim())}`,
    )
    return {
      deletedCount: readNumber(result.data, 'deleted_count'),
      latestSeq: readNumber(result.data, 'latest_seq'),
      currentDeviceAckSeq: readNumber(result.data, 'current_device_ack_seq'),
    }
  }

  async clearClipboardHistory(): Promise<ClipboardHistoryCleanupResult> {
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/clipboard/history/clear')
    return this.parseHistoryCleanupResult(result.data)
  }

  async cleanupClipboardHistory(days: number): Promise<ClipboardHistoryCleanupResult> {
    const body = new RequestPayload()
    body.days = days
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/clipboard/history/cleanup', body)
    return this.parseHistoryCleanupResult(result.data)
  }

  async getHistorySettings(): Promise<ClipboardHistorySettings> {
    const result = await this.requestAuthenticatedJson(http.RequestMethod.GET, '/v1/clipboard/history/settings')
    return parseHistorySettings(asObject(result.data.settings))
  }

  async updateHistorySettings(retentionDays: number, historyLimit: number): Promise<ClipboardHistoryCleanupResult> {
    const body = new RequestPayload()
    body.retention_days = retentionDays
    body.history_limit = historyLimit
    const result = await this.requestAuthenticatedJson(http.RequestMethod.PUT, '/v1/clipboard/history/settings', body)
    return this.parseHistoryCleanupResult(result.data)
  }

  async pullSync(sinceSeq: number, limit: number): Promise<SyncPullResult> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      `/v1/sync/pull?since_seq=${sinceSeq}&limit=${limit}`,
    )
    return {
      items: parseObjectList(asArray(result.data.items), parseClipboardItem),
      nextSinceSeq: readNumber(result.data, 'next_since_seq'),
    }
  }

  async ackSync(seq: number): Promise<void> {
    const body = new RequestPayload()
    body.seq = seq
    await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/sync/ack', body)
  }

  async listFiles(page: number, pageSize: number): Promise<FileListResult> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      `/v1/files?page=${page}&page_size=${pageSize}`,
    )
    const pagination = asObject(result.data.pagination)
    const summary = asObject(result.data.summary)
    return {
      files: parseObjectList(asArray(result.data.files), parseFileRecord),
      page: readNumber(pagination, 'page', page),
      totalPages: readNumber(pagination, 'total_pages'),
      totalFiles: readNumber(summary, 'total_files'),
      totalBytes: readNumber(summary, 'total_bytes'),
      maxUploadBytes: readNumber(summary, 'max_upload_bytes'),
    }
  }

  async uploadFile(localFile: LocalFile): Promise<FileRecord> {
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/files', undefined, [
      this.buildFileFormData(localFile, 'file'),
    ])
    return parseFileRecord(asObject(result.data.file))
  }

  async renameFile(fileId: string, originalName: string): Promise<FileRecord> {
    const body = new RequestPayload()
    body.original_name = originalName
    const result = await this.requestAuthenticatedJson(
      PATCH_METHOD,
      `/v1/files/${encodeURIComponent(fileId.trim())}`,
      body,
    )
    return parseFileRecord(asObject(result.data.file))
  }

  async deleteFile(fileId: string): Promise<DeleteFileResult> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.DELETE,
      `/v1/files/${encodeURIComponent(fileId.trim())}`,
    )
    return {
      file: parseFileRecord(asObject(result.data.file)),
      diskRemoved: readBoolean(result.data, 'disk_removed', true),
    }
  }

  async downloadFileBytes(fileId: string): Promise<ArrayBuffer> {
    const result = await this.requestAuthenticatedBinary(
      `/v1/files/${encodeURIComponent(fileId.trim())}/download`,
    )
    return result.data
  }

  async listShares(page: number, pageSize: number, status: ShareStatusFilter): Promise<ShareListResult> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      `/v1/shares?page=${page}&page_size=${pageSize}&status=${status}`,
    )
    const pagination = asObject(result.data.pagination)
    const summary = asObject(result.data.summary)
    return {
      shares: parseObjectList(asArray(result.data.shares), parseShareRecord),
      page: readNumber(pagination, 'page', page),
      totalPages: readNumber(pagination, 'total_pages'),
      totalShares: readNumber(pagination, 'total'),
      maxUploadBytes: readNumber(summary, 'max_upload_bytes'),
    }
  }

  async createTextShare(textContent: string, policy: SharePolicyPayload): Promise<ShareRecord> {
    const body = new RequestPayload()
    body.text_content = textContent
    body.never_expires = policy.neverExpires
    body.burn_mode = policy.burnMode
    body.allow_copy_content = policy.allowCopyContent
    body.is_encrypted = false
    if (!policy.neverExpires) {
      body.expire_seconds = policy.expireSeconds
    }
    if (policy.burnMode === 'countdown') {
      body.burn_after_seconds = policy.burnAfterSeconds
    }

    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/shares/text', body)
    return parseShareRecord(asObject(result.data.share))
  }

  async createFileShare(localFile: LocalFile, policy: SharePolicyPayload): Promise<ShareRecord> {
    const formData: http.MultiFormData[] = [
      this.buildFileFormData(localFile, 'file'),
      this.buildTextFormData('never_expires', String(policy.neverExpires)),
      this.buildTextFormData('burn_mode', policy.burnMode),
      this.buildTextFormData('allow_copy_content', String(policy.allowCopyContent)),
      this.buildTextFormData('is_encrypted', 'false'),
    ]

    if (!policy.neverExpires) {
      formData.push(this.buildTextFormData('expire_seconds', `${policy.expireSeconds}`))
    }
    if (policy.burnMode === 'countdown') {
      formData.push(this.buildTextFormData('burn_after_seconds', `${policy.burnAfterSeconds}`))
    }

    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/shares/file', undefined, formData)
    return parseShareRecord(asObject(result.data.share))
  }

  async revokeShare(shareId: string): Promise<ShareRecord> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.POST,
      `/v1/shares/${encodeURIComponent(shareId.trim())}/revoke`,
    )
    return parseShareRecord(asObject(result.data.share))
  }

  async createQuotaRequest(requestedQuotaMb: number, reason: string): Promise<QuotaRequestRecord> {
    const body = new RequestPayload()
    body.requested_quota_mb = requestedQuotaMb
    body.reason = reason
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/account/quota-requests', body)
    return parseQuotaRequestRecord(asObject(result.data.request))
  }

  async listQuotaRequests(status: RequestStatusFilter): Promise<QuotaRequestRecord[]> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      `/v1/account/quota-requests?status=${status}`,
    )
    return parseObjectList(asArray(result.data.requests), parseQuotaRequestRecord)
  }

  async createBandwidthRequest(
    requestedUploadKbps: number,
    requestedDownloadKbps: number,
    reason: string,
  ): Promise<BandwidthRequestRecord> {
    const body = new RequestPayload()
    body.requested_upload_kbps = requestedUploadKbps
    body.requested_download_kbps = requestedDownloadKbps
    body.reason = reason
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/account/bandwidth-requests', body)
    return parseBandwidthRequestRecord(asObject(result.data.request))
  }

  async listBandwidthRequests(status: RequestStatusFilter): Promise<BandwidthRequestRecord[]> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      `/v1/account/bandwidth-requests?status=${status}`,
    )
    return parseObjectList(asArray(result.data.requests), parseBandwidthRequestRecord)
  }

  async createAdminRequest(reason: string): Promise<AdminRequestRecord> {
    const body = new RequestPayload()
    body.reason = reason
    const result = await this.requestAuthenticatedJson(http.RequestMethod.POST, '/v1/account/admin-requests', body)
    return parseAdminRequestRecord(asObject(result.data.request))
  }

  async listAdminRequests(status: RequestStatusFilter): Promise<AdminRequestRecord[]> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      `/v1/account/admin-requests?status=${status}`,
    )
    return parseObjectList(asArray(result.data.requests), parseAdminRequestRecord)
  }

  async getAdminSettings(): Promise<AdminSettingsResult> {
    const result = await this.requestAuthenticatedJson(http.RequestMethod.GET, '/v1/admin/settings')
    return {
      settings: parseAdminSettingsRecord(asObject(result.data.settings)),
      currentUserCount: readNumber(result.data, 'current_user_count'),
    }
  }

  async updateAdminSettings(input: AdminSettingsUpdateInput): Promise<AdminSettingsResult> {
    const body = new RequestPayload()
    body.max_user_count = input.maxUserCount
    body.default_storage_quota_mb = input.defaultStorageQuotaMb
    body.default_upload_bandwidth_kbps = input.defaultUploadBandwidthKbps
    body.default_download_bandwidth_kbps = input.defaultDownloadBandwidthKbps
    body.max_user_upload_bandwidth_kbps = input.maxUserUploadBandwidthKbps
    body.max_user_download_bandwidth_kbps = input.maxUserDownloadBandwidthKbps
    body.max_upload_file_mb = input.maxUploadFileMb
    body.allow_registration = input.allowRegistration
    const result = await this.requestAuthenticatedJson(http.RequestMethod.PUT, '/v1/admin/settings', body)
    return {
      settings: parseAdminSettingsRecord(asObject(result.data.settings)),
      currentUserCount: readNumber(result.data, 'current_user_count'),
    }
  }

  async listAdminUsers(): Promise<AdminUserRecord[]> {
    const result = await this.requestAuthenticatedJson(http.RequestMethod.GET, '/v1/admin/users')
    return parseObjectList(asArray(result.data.users), parseAdminUserRecord)
  }

  async updateAdminUser(userId: string, input: AdminUserUpdateInput): Promise<AdminUserRecord> {
    const body = new RequestPayload()
    body.storage_quota_mb = input.storageQuotaMb
    body.upload_bandwidth_kbps = input.uploadBandwidthKbps
    body.download_bandwidth_kbps = input.downloadBandwidthKbps
    body.is_admin = input.isAdmin
    const result = await this.requestAuthenticatedJson(
      PATCH_METHOD,
      `/v1/admin/users/${encodeURIComponent(userId.trim())}`,
      body,
    )
    return parseAdminUserRecord(asObject(result.data.user))
  }

  async deleteAdminUser(userId: string): Promise<DeleteUserResult> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.DELETE,
      `/v1/admin/users/${encodeURIComponent(userId.trim())}`,
    )
    return {
      success: readBoolean(result.data, 'success'),
      deletedUserId: readString(result.data, 'deleted_user_id'),
    }
  }

  async listPendingQuotaRequests(): Promise<QuotaRequestRecord[]> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      '/v1/admin/quota-requests?status=pending',
    )
    return parseObjectList(asArray(result.data.requests), parseQuotaRequestRecord)
  }

  async approveQuotaRequest(requestId: string, approvedQuotaMb: number, reviewNote: string): Promise<QuotaRequestRecord> {
    const body = new RequestPayload()
    body.approved_quota_mb = approvedQuotaMb
    body.review_note = reviewNote
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.POST,
      `/v1/admin/quota-requests/${encodeURIComponent(requestId.trim())}/approve`,
      body,
    )
    return parseQuotaRequestRecord(asObject(result.data.request))
  }

  async rejectQuotaRequest(requestId: string, reviewNote: string): Promise<QuotaRequestRecord> {
    const body = new RequestPayload()
    body.review_note = reviewNote
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.POST,
      `/v1/admin/quota-requests/${encodeURIComponent(requestId.trim())}/reject`,
      body,
    )
    return parseQuotaRequestRecord(asObject(result.data.request))
  }

  async listPendingBandwidthRequests(): Promise<BandwidthRequestRecord[]> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      '/v1/admin/bandwidth-requests?status=pending',
    )
    return parseObjectList(asArray(result.data.requests), parseBandwidthRequestRecord)
  }

  async approveBandwidthRequest(
    requestId: string,
    approvedUploadKbps: number,
    approvedDownloadKbps: number,
    reviewNote: string,
  ): Promise<BandwidthRequestRecord> {
    const body = new RequestPayload()
    body.approved_upload_kbps = approvedUploadKbps
    body.approved_download_kbps = approvedDownloadKbps
    body.review_note = reviewNote
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.POST,
      `/v1/admin/bandwidth-requests/${encodeURIComponent(requestId.trim())}/approve`,
      body,
    )
    return parseBandwidthRequestRecord(asObject(result.data.request))
  }

  async rejectBandwidthRequest(requestId: string, reviewNote: string): Promise<BandwidthRequestRecord> {
    const body = new RequestPayload()
    body.review_note = reviewNote
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.POST,
      `/v1/admin/bandwidth-requests/${encodeURIComponent(requestId.trim())}/reject`,
      body,
    )
    return parseBandwidthRequestRecord(asObject(result.data.request))
  }

  async listPendingAdminRequests(): Promise<AdminRequestRecord[]> {
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.GET,
      '/v1/admin/admin-requests?status=pending',
    )
    return parseObjectList(asArray(result.data.requests), parseAdminRequestRecord)
  }

  async approveAdminRequest(requestId: string, reviewNote: string): Promise<AdminRequestRecord> {
    const body = new RequestPayload()
    body.review_note = reviewNote
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.POST,
      `/v1/admin/admin-requests/${encodeURIComponent(requestId.trim())}/approve`,
      body,
    )
    return parseAdminRequestRecord(asObject(result.data.request))
  }

  async rejectAdminRequest(requestId: string, reviewNote: string): Promise<AdminRequestRecord> {
    const body = new RequestPayload()
    body.review_note = reviewNote
    const result = await this.requestAuthenticatedJson(
      http.RequestMethod.POST,
      `/v1/admin/admin-requests/${encodeURIComponent(requestId.trim())}/reject`,
      body,
    )
    return parseAdminRequestRecord(asObject(result.data.request))
  }

  private async requestAuthenticatedJson(
    method: http.RequestMethod,
    path: string,
    body?: RequestPayload,
    multiFormDataList?: http.MultiFormData[],
  ): Promise<JsonRequestResult> {
    const session = this.getSession()
    try {
      return {
        data: await this.requestJson({
          baseUrl: session.baseUrl,
          method,
          path,
          accessToken: session.accessToken,
          body,
          multiFormDataList,
        }),
      }
    } catch (error) {
      if (!this.isUnauthorized(error)) {
        throw this.mapRequestError(error as Object)
      }

      // 所有受保护接口统一在这里自动 refresh 一次，页面层只需要处理业务成败。
      const tokens = await this.refresh(session.baseUrl, session.refreshToken)
      this.onTokensUpdated(tokens)
      const refreshed = this.getSession()
      return {
        data: await this.requestJson({
          baseUrl: refreshed.baseUrl,
          method,
          path,
          accessToken: refreshed.accessToken,
          body,
          multiFormDataList,
        }),
        tokens,
      }
    }
  }

  private async requestAuthenticatedBinary(path: string): Promise<BinaryRequestResult> {
    const session = this.getSession()
    try {
      return {
        data: await this.requestBinary({
          baseUrl: session.baseUrl,
          method: http.RequestMethod.GET,
          path,
          accessToken: session.accessToken,
        }),
      }
    } catch (error) {
      if (!this.isUnauthorized(error)) {
        throw this.mapRequestError(error as Object)
      }

      const tokens = await this.refresh(session.baseUrl, session.refreshToken)
      this.onTokensUpdated(tokens)
      const refreshed = this.getSession()
      return {
        data: await this.requestBinary({
          baseUrl: refreshed.baseUrl,
          method: http.RequestMethod.GET,
          path,
          accessToken: refreshed.accessToken,
        }),
        tokens,
      }
    }
  }

  private async requestJson(config: JsonRequestConfig): Promise<JsonObject> {
    const response = await this.executeRequest(config, false)
    const bodyText = this.toText(response.result)
    if (response.responseCode < 200 || response.responseCode >= 300) {
      throw new ApiError(this.extractErrorMessage(bodyText, response.responseCode), Number(response.responseCode))
    }

    const envelope = parseJsonObject(bodyText)
    const code = readNumber(envelope, 'code', -1)
    if (code !== 0) {
      throw new ApiError(readString(envelope, 'message', '请求失败'), code)
    }
    return asObject(envelope.data)
  }

  private async requestBinary(config: JsonRequestConfig): Promise<ArrayBuffer> {
    const response = await this.executeRequest(config, true)
    if (response.responseCode < 200 || response.responseCode >= 300) {
      const bodyText = this.toText(response.result)
      throw new ApiError(this.extractErrorMessage(bodyText, response.responseCode), Number(response.responseCode))
    }

    if (response.result instanceof ArrayBuffer) {
      return response.result
    }

    // 下载接口预期一定是二进制；如果这里拿到字符串，说明服务端响应不符合预期。
    throw new ApiError('服务端返回了非二进制文件内容', Number(response.responseCode))
  }

  private async executeRequest(config: JsonRequestConfig, expectBuffer: boolean): Promise<http.HttpResponse> {
    const request = http.createHttp()
    try {
      const options: http.HttpRequestOptions = {
        method: config.method,
        connectTimeout: 15000,
        readTimeout: expectBuffer ? 60000 : 20000,
        usingCache: false,
        expectDataType: expectBuffer ? http.HttpDataType.ARRAY_BUFFER : http.HttpDataType.STRING,
        header: this.buildHeaders(config.accessToken, config.multiFormDataList !== undefined),
      }
      if (config.body !== undefined) {
        options.extraData = config.body
      }
      if (config.multiFormDataList !== undefined) {
        options.multiFormDataList = config.multiFormDataList
      }

      const response = await request.request(
        `${normalizeBaseUrl(config.baseUrl)}${config.path}`,
        options,
      )
      return response
    } catch (error) {
      throw this.mapRequestError(error)
    } finally {
      request.destroy()
    }
  }

  private buildHeaders(accessToken?: string, isMultipart: boolean = false): HeaderMap {
    const headers: HeaderMap = {}
    if (accessToken && accessToken.trim().length > 0) {
      headers.Authorization = `Bearer ${accessToken}`
    }
    if (!isMultipart) {
      headers['Content-Type'] = CONTENT_TYPE_JSON
    }
    return headers
  }

  private buildFileFormData(localFile: LocalFile, fieldName: string): http.MultiFormData {
    return {
      name: fieldName,
      contentType: localFile.contentType.length > 0 ? localFile.contentType : 'application/octet-stream',
      remoteFileName: localFile.displayName,
      filePath: localFile.path,
    }
  }

  private buildTextFormData(name: string, value: string): http.MultiFormData {
    return {
      name,
      contentType: 'text/plain',
      data: value,
    }
  }

  private parseHistoryCleanupResult(data: JsonObject): ClipboardHistoryCleanupResult {
    return {
      deletedCount: readNumber(data, 'deleted_count'),
      settings: parseHistorySettings(asObject(data.settings)),
      latestSeq: readNumber(data, 'latest_seq'),
      currentDeviceAckSeq: readNumber(data, 'current_device_ack_seq'),
    }
  }

  private extractErrorMessage(bodyText: string, httpCode: number): string {
    if (bodyText.trim().length === 0) {
      return `请求失败（HTTP ${httpCode}）`
    }

    try {
      const envelope = parseJsonObject(bodyText)
      return readString(envelope, 'message', `请求失败（HTTP ${httpCode}）`)
    } catch (_) {
      return bodyText.trim().length > 0 ? bodyText.trim() : `请求失败（HTTP ${httpCode}）`
    }
  }

  private toText(value: string | Object | ArrayBuffer): string {
    if (typeof value === 'string') {
      return value
    }
    if (value instanceof ArrayBuffer) {
      const bytes = new Uint8Array(value)
      let result = ''
      for (let index = 0; index < bytes.length; index += 1) {
        result += String.fromCharCode(bytes[index])
      }
      return result
    }
    return JSON.stringify(value)
  }

  private mapRequestError(error: Object): ApiError {
    if (error instanceof ApiError) {
      return error
    }

    const source = error as RequestErrorShape
    const code = typeof source.code === 'number' ? Number(source.code) : 0
    const message = typeof source.message === 'string' ? source.message : '网络异常，请检查服务地址和网络连接'
    return new ApiError(message, this.translateBusinessCode(code))
  }

  private translateBusinessCode(code: number): number {
    if (code === 2300094) {
      return 401
    }
    return code
  }

  private isUnauthorized(error: Object): boolean {
    return error instanceof ApiError && error.httpCode === 401
  }
}
