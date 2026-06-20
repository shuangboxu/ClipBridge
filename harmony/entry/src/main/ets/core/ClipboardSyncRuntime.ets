import { common } from '@kit.AbilityKit'
import { pasteboard } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { ApiClient } from './ApiClient'
import { StoredSession, TokenBundle, emptySession, hasCompleteAuth } from './Models'
import { SessionStore } from './SessionStore'

const DOMAIN = 0x0000

export interface BackgroundUploadResult {
  attempted: boolean
  uploaded: boolean
  deduplicated: boolean
  latestAckSeq: number
  reason: string
}

export class ClipboardSyncRuntime {
  private readonly sessionStore: SessionStore
  private readonly apiClient: ApiClient
  private currentSession: StoredSession = emptySession()

  constructor(context: common.UIAbilityContext) {
    this.sessionStore = new SessionStore(context)
    this.currentSession = this.sessionStore.readSession()
    this.apiClient = new ApiClient(
      () => this.currentSession,
      (tokens: TokenBundle) => {
        this.applyTokens(tokens)
      },
    )
  }

  async uploadClipboardIfNeeded(): Promise<BackgroundUploadResult> {
    this.reloadSession()

    const unavailableReason = this.resolveUnavailableReason()
    if (unavailableReason.length > 0) {
      return this.buildBackgroundResult(false, false, false, this.sessionStore.readLastAckSeq(), unavailableReason)
    }

    const text = await this.readClipboardText()
    if (text.length === 0) {
      return this.buildBackgroundResult(false, false, false, this.sessionStore.readLastAckSeq(), '系统剪贴板为空')
    }

    const uploadResult = await this.apiClient.uploadText(text)
    const latestAckSeq = uploadResult.item.seq > 0 ? uploadResult.item.seq : this.sessionStore.readLastAckSeq()
    if (uploadResult.item.seq > 0) {
      await this.apiClient.ackSync(uploadResult.item.seq)
      this.sessionStore.saveLastAckSeq(uploadResult.item.seq)
    }

    return this.buildBackgroundResult(
      true,
      true,
      uploadResult.deduplicated,
      latestAckSeq,
      uploadResult.deduplicated ? '剪贴板内容已去重' : '剪贴板内容已上传',
    )
  }

  private reloadSession(): void {
    this.currentSession = this.sessionStore.readSession()
  }

  private resolveUnavailableReason(): string {
    if (!this.sessionStore.isSyncEnabled()) {
      return '同步开关未开启'
    }
    if (!hasCompleteAuth(this.currentSession)) {
      return '登录态已失效'
    }
    return ''
  }

  private async readClipboardText(): Promise<string> {
    try {
      const systemPasteboard = pasteboard.getSystemPasteboard()
      if (!systemPasteboard.hasDataSync()) {
        return ''
      }
      const data = await systemPasteboard.getData()
      const text = data.getPrimaryText()
      return typeof text === 'string' ? text.trim() : ''
    } catch (error) {
      hilog.warn(DOMAIN, 'ClipBridgeSync', 'Failed to read clipboard: %{public}s', JSON.stringify(error))
      return ''
    }
  }

  private applyTokens(tokens: TokenBundle): void {
    // 中文注释：后台同步刷新到新 token 后，必须同时更新内存和本地存储，
    // 否则下一个请求仍然会带旧 token，形成“每次都 401”的假死状态。
    this.currentSession = {
      baseUrl: this.currentSession.baseUrl,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      currentDeviceId: this.currentSession.currentDeviceId,
      username: this.currentSession.username,
      deviceName: this.currentSession.deviceName,
      isAdmin: this.currentSession.isAdmin,
      storageQuotaBytes: this.currentSession.storageQuotaBytes,
      storageUsedBytes: this.currentSession.storageUsedBytes,
      storageFreeBytes: this.currentSession.storageFreeBytes,
      uploadBandwidthKbps: this.currentSession.uploadBandwidthKbps,
      downloadBandwidthKbps: this.currentSession.downloadBandwidthKbps,
      maxUploadFileBytes: this.currentSession.maxUploadFileBytes,
    }
    this.sessionStore.updateTokens(tokens)
  }

  private buildBackgroundResult(
    attempted: boolean,
    uploaded: boolean,
    deduplicated: boolean,
    latestAckSeq: number,
    reason: string,
  ): BackgroundUploadResult {
    return {
      attempted,
      uploaded,
      deduplicated,
      latestAckSeq,
      reason,
    }
  }
}
