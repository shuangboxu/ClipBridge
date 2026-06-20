import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit'
import { promptAction } from '@kit.ArkUI'
import { pasteboard } from '@kit.BasicServicesKit'
import { ApiClient } from '../core/ApiClient'
import { ClipboardSyncRuntime } from '../core/ClipboardSyncRuntime'
import { FileTransfer } from '../core/FileTransfer'
import { formatBytes, formatKbps, formatTime, publicShareLink, remainingSecondsText, statusLabel } from '../core/Formatters'
import {
  AccountProfile,
  AdminRequestRecord,
  AdminSettingsRecord,
  AdminUserRecord,
  ApiError,
  AuthResult,
  BandwidthRequestRecord,
  ClipboardHistorySettings,
  ClipboardItem,
  DeviceRecord,
  FileRecord,
  QuotaRequestRecord,
  RequestStatusFilter,
  ShareComposeMode,
  ShareCountdownPreset,
  ShareExpirePreset,
  ShareRecord,
  ShareRuleConfig,
  ShareStatusFilter,
  ShareStrategyKey,
  StoredSession,
  ShareStrategySummary,
  TokenBundle,
  buildDefaultShareRules,
  buildSharePolicyPayload,
  buildShareStrategySummary,
  defaultDeviceName,
  emptyAdminSettings,
  emptyHistorySettings,
  emptySession,
  hasCompleteAuth,
  requestStatusFilters,
  requestStatusLabel,
  shareCountdownLabel,
  shareCountdownPresets,
  shareExpireLabel,
  shareExpirePresets,
  shareStatusFilters,
  shareStatusLabel,
  shareStrategyKeys,
  shareStrategyLabel,
} from '../core/Models'
import { SessionStore } from '../core/SessionStore'
import { ShareRulesStore } from '../core/ShareRulesStore'

const PAGE_SIZE = 20
const PULL_LIMIT = 50
const PAGE_PADDING = 16
const PAGE_SPACE = 12
const CONTENT_PADDING = 18
const CONTENT_SPACE = 16
const PANEL_RADIUS = 24
const HOME_PAGE_PADDING = 20
const HOME_PAGE_SPACE = 18
const HOME_SYNC_HERO_SIZE = 220
const HOME_SYNC_IMAGE_SIZE = 158
const READ_PASTEBOARD_PERMISSION = 'ohos.permission.READ_PASTEBOARD'

const COLOR_BACKGROUND = '#F5F7FA'
const COLOR_CARD = '#FFFFFF'
const COLOR_CARD_MUTED = '#F8FAFD'
const COLOR_BORDER = '#E3E8EF'
const COLOR_PRIMARY = '#2F7CF6'
const COLOR_PRIMARY_SOFT = '#EAF2FF'
const COLOR_ACCENT = '#0FA38C'
const COLOR_ACCENT_SOFT = '#E6F8F3'
const COLOR_WARN = '#F59E0B'
const COLOR_WARN_SOFT = '#FFF4DD'
const COLOR_DANGER = '#E5484D'
const COLOR_DANGER_SOFT = '#FFE9EA'
const COLOR_TEXT = '#1F2937'
const COLOR_SUBTEXT = '#667085'

enum AuthMode {
  Login = 'login',
  Register = 'register',
}

enum MainTab {
  Home = 'home',
  History = 'history',
  Ai = 'ai',
  Settings = 'settings',
}

enum DetailPage {
  None = 'none',
  Files = 'files',
  Shares = 'shares',
  ShareRules = 'shareRules',
  HistorySettings = 'historySettings',
  Account = 'account',
  Security = 'security',
  Devices = 'devices',
  Requests = 'requests',
  AdminSettings = 'adminSettings',
  AdminUsers = 'adminUsers',
  AdminReviews = 'adminReviews',
}

interface AdminUserDraft {
  storageQuotaMb: string
  uploadBandwidthKbps: string
  downloadBandwidthKbps: string
  isAdmin: boolean
}

interface QuotaReviewDraft {
  approvedQuotaMb: string
  reviewNote: string
}

interface BandwidthReviewDraft {
  approvedUploadKbps: string
  approvedDownloadKbps: string
  reviewNote: string
}

interface SettingLink {
  title: string
  page: DetailPage
}

interface SegmentOption {
  label: string
  selected: boolean
  onClick: () => void
}

@Entry
@Component
struct Index {
  private sessionStore?: SessionStore
  private shareRulesStore?: ShareRulesStore
  private apiClient?: ApiClient
  private clipboardSyncRuntime?: ClipboardSyncRuntime
  private fileTransfer?: FileTransfer
  private clipboardUpdateListener?: () => void
  private clipboardObserverRegistered: boolean = false
  private clipboardAutoSyncRunning: boolean = false
  private ignoreNextClipboardUpdate: boolean = false
  private lastAutoUploadedClipboardText: string = ''
  private initialized: boolean = false

  @State bootLoading: boolean = true
  @State loggedIn: boolean = false

  @State authMode: AuthMode = AuthMode.Login
  @State authBaseUrl: string = ''
  @State authUsername: string = ''
  @State authPassword: string = ''
  @State authDeviceName: string = defaultDeviceName()
  @State authLoading: boolean = false
  @State authMessage: string = ''
  @State authAllowRegistrationHint: boolean = false

  @State currentSession: StoredSession = emptySession()
  @State syncEnabled: boolean = false
  @State activeTab: MainTab = MainTab.Home
  @State activeTabIndex: number = 0
  @State detailPage: DetailPage = DetailPage.None

  @State homeManualText: string = ''
  @State homeActionLoading: boolean = false

  @State historyItems: ClipboardItem[] = []
  @State historyLoading: boolean = false
  @State historyLoadingMore: boolean = false
  @State historyError: string = ''
  @State historyHasMore: boolean = false
  @State historyBeforeSeq: number = 0
  @State historySearchQuery: string = ''
  @State historyDraftText: string = ''
  @State historyLatestSeq: number = 0
  @State historyAckSeq: number = 0
  @State historySettings: ClipboardHistorySettings = emptyHistorySettings()
  @State historySettingsLoading: boolean = false
  @State historyRetentionDaysInput: string = '0'
  @State historyLimitInput: string = '1000'

  @State devices: DeviceRecord[] = []
  @State devicesLoading: boolean = false
  @State devicesError: string = ''
  @State deviceRenameDrafts: Record<string, string> = {}
  @State devicePendingOfflineId: string = ''

  @State files: FileRecord[] = []
  @State filesLoading: boolean = false
  @State filesError: string = ''
  @State filesSearchQuery: string = ''
  @State filesPage: number = 1
  @State filesTotalPages: number = 0
  @State filesTotalFiles: number = 0
  @State filesTotalBytes: number = 0
  @State filesMaxUploadBytes: number = 0
  @State fileRenameDrafts: Record<string, string> = {}
  @State filePendingDeleteId: string = ''

  @State shareRules: ShareRuleConfig = buildDefaultShareRules()
  @State shareRulesLoading: boolean = false
  @State shareComposeMode: ShareComposeMode = ShareComposeMode.Text
  @State shareStrategy: ShareStrategyKey = ShareStrategyKey.Expire
  @State shareTextInput: string = ''
  @State shares: ShareRecord[] = []
  @State sharesLoading: boolean = false
  @State sharesCreating: boolean = false
  @State sharesError: string = ''
  @State sharesStatus: ShareStatusFilter = ShareStatusFilter.All
  @State sharesPage: number = 1
  @State sharesTotalPages: number = 0
  @State sharesTotalCount: number = 0
  @State sharesMaxUploadBytes: number = 0
  @State shareLastLink: string = ''
  @State sharePendingRevokeId: string = ''

  @State requestStatus: RequestStatusFilter = RequestStatusFilter.All
  @State requestsLoading: boolean = false
  @State requestsError: string = ''
  @State quotaRequests: QuotaRequestRecord[] = []
  @State bandwidthRequests: BandwidthRequestRecord[] = []
  @State adminRequests: AdminRequestRecord[] = []
  @State quotaRequestMbInput: string = ''
  @State quotaRequestReason: string = ''
  @State bandwidthUploadInput: string = ''
  @State bandwidthDownloadInput: string = ''
  @State bandwidthReason: string = ''
  @State adminRequestReason: string = ''

  @State adminSettingsLoading: boolean = false
  @State adminSettingsError: string = ''
  @State adminSettings: AdminSettingsRecord = emptyAdminSettings()
  @State adminCurrentUserCount: number = 0
  @State adminMaxUserCountInput: string = ''
  @State adminDefaultQuotaMbInput: string = ''
  @State adminDefaultUploadInput: string = ''
  @State adminDefaultDownloadInput: string = ''
  @State adminMaxUserUploadInput: string = ''
  @State adminMaxUserDownloadInput: string = ''
  @State adminMaxUploadFileMbInput: string = ''
  @State adminAllowRegistration: boolean = false

  @State adminUsersLoading: boolean = false
  @State adminUsersError: string = ''
  @State adminUsers: AdminUserRecord[] = []
  @State adminUserDrafts: Record<string, AdminUserDraft> = {}
  @State adminUserPendingDeleteId: string = ''

  @State adminReviewsLoading: boolean = false
  @State adminReviewsError: string = ''
  @State pendingQuotaReviews: QuotaRequestRecord[] = []
  @State pendingBandwidthReviews: BandwidthRequestRecord[] = []
  @State pendingAdminReviews: AdminRequestRecord[] = []
  @State quotaReviewDrafts: Record<string, QuotaReviewDraft> = {}
  @State bandwidthReviewDrafts: Record<string, BandwidthReviewDraft> = {}
  @State adminReviewNotes: Record<string, string> = {}

  @State serviceAddressDraft: string = ''
  @State securityCurrentPassword: string = ''
  @State securityNewPassword: string = ''
  @State securityConfirmPassword: string = ''
  @State securityLoading: boolean = false

  aboutToAppear(): void {
    this.runAsync(() => this.initializeApp())
  }

  aboutToDisappear(): void {
    this.unregisterClipboardObserver()
  }

  build() {
    Column() {
      if (this.bootLoading) {
        this.LoadingPage('正在检查登录态...')
      } else if (!this.loggedIn) {
        this.AuthPage()
      } else {
        this.ShellPage()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(COLOR_BACKGROUND)
  }

  @Builder
  private LoadingPage(message: string) {
    Column({ space: 12 }) {
      LoadingProgress()
        .width(36)
        .height(36)
        .color(COLOR_PRIMARY)
      Text('ClipBridge')
        .fontSize(24)
        .fontWeight(FontWeight.Bold)
        .fontColor(COLOR_TEXT)
      Text(message)
        .fontSize(14)
        .fontColor(COLOR_SUBTEXT)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  private AuthPage() {
    Scroll() {
      Column({ space: PAGE_SPACE }) {
        Column({ space: 8 }) {
          Text('ClipBridge')
            .fontSize(28)
            .fontWeight(FontWeight.Bold)
            .fontColor(COLOR_TEXT)
          Text('跨端剪贴板、文件与分享同步')
            .fontSize(14)
            .fontColor(COLOR_SUBTEXT)
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)

        this.MessageCard(this.authMessage, COLOR_WARN_SOFT, COLOR_WARN)

        Column({ space: 12 }) {
          this.SegmentRow(this.buildAuthModeButtons())

          this.InputField('服务地址', '例如：http://192.168.1.10:8080', this.authBaseUrl, (value: string) => {
            this.authBaseUrl = value
          })
          this.InputField('用户名', '请输入用户名', this.authUsername, (value: string) => {
            this.authUsername = value
          })
          this.PasswordField('密码', '请输入密码', this.authPassword, (value: string) => {
            this.authPassword = value
          })
          this.InputField('设备名称', '默认自动生成，可手动修改', this.authDeviceName, (value: string) => {
            this.authDeviceName = value
          })

          if (this.authMode === AuthMode.Register && !this.authAllowRegistrationHint) {
            Text('当前服务可能已关闭公开注册；如果提交后返回 403，需要由管理员开放注册。')
              .fontSize(12)
              .fontColor(COLOR_SUBTEXT)
          }

          Button(this.authMode === AuthMode.Login ? '登录并进入主页面' : '注册并进入主页面')
            .width('100%')
            .height(44)
            .backgroundColor(COLOR_PRIMARY)
            .fontColor('#FFFFFF')
            .enabled(!this.authLoading)
            .onClick(() => {
              this.runAsync(() => this.submitAuth())
            })

          Button('测试连接')
            .type(ButtonType.Normal)
            .width('100%')
            .height(40)
            .backgroundColor(COLOR_PRIMARY_SOFT)
            .fontColor(COLOR_PRIMARY)
            .enabled(!this.authLoading)
            .onClick(() => {
              this.runAsync(() => this.testServerConnection())
            })
        }
        .width('100%')
        .padding(16)
        .backgroundColor(COLOR_CARD)
        .borderRadius(10)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)
      }
      .width('100%')
      .padding(PAGE_PADDING)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private ShellPage() {
    Column() {
      this.TopBar()
      this.CurrentPage()
      this.BottomBar()
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private TopBar() {
    Column({ space: 0 }) {
      Row({ space: 12 }) {
        if (this.detailPage !== DetailPage.None) {
          Button('<')
            .width(36)
            .height(36)
            .backgroundColor(COLOR_PRIMARY_SOFT)
            .fontColor(COLOR_PRIMARY)
            .onClick(() => {
              this.detailPage = DetailPage.None
            })
        }

        Column({ space: 2 }) {
          Text(this.resolvePageTitle())
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(COLOR_TEXT)
          Text(this.currentSession.username.length > 0 ? this.currentSession.username : '未登录')
            .fontSize(12)
            .fontColor(COLOR_SUBTEXT)
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)

        if (this.detailPage === DetailPage.None) {
          Text(this.syncEnabled ? '同步已开启' : '同步已关闭')
            .fontSize(12)
            .fontColor(this.syncEnabled ? COLOR_ACCENT : COLOR_SUBTEXT)
        }
      }
      .width('100%')
      .padding({ left: PAGE_PADDING, right: PAGE_PADDING, top: 14, bottom: 12 })
      .backgroundColor(COLOR_CARD)

      Blank()
        .width('100%')
        .height(1)
        .backgroundColor(COLOR_BORDER)
    }
    .width('100%')
  }

  @Builder
  private BottomBar() {
    Column({ space: 0 }) {
      Blank()
        .width('100%')
        .height(1)
        .backgroundColor(COLOR_BORDER)

      Row({ space: 8 }) {
        this.TabButton('首页', () => {
          this.selectTab(MainTab.Home)
        })
        this.TabButton('历史', () => {
          this.selectTab(MainTab.History)
        })
        this.TabButton('设置', () => {
          this.selectTab(MainTab.Settings)
        })
      }
      .width('100%')
      .padding({ left: 12, right: 12, top: 10, bottom: 12 })
      .backgroundColor(COLOR_CARD)
    }
    .width('100%')
  }

  @Builder
  private TabButton(label: string, onClick: () => void) {
    Column({ space: 6 }) {
      Row() {}
        .width(24)
        .height(4)
        .borderRadius(2)
        .backgroundColor('#00000000')

      Text(label)
        .fontSize(13)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_SUBTEXT)
    }
    .layoutWeight(1)
    .height(52)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(COLOR_CARD)
    .borderRadius(16)
    .onClick(onClick)
  }

  @Builder
  private CurrentPage() {
    if (this.detailPage !== DetailPage.None) {
      this.DetailPageContent()
    } else if (this.activeTabIndex === 0) {
      this.HomePage()
    } else if (this.activeTabIndex === 1) {
      this.HistoryPage()
    } else {
      this.SettingsPage()
    }
  }

  @Builder
  private DetailPageContent() {
    if (this.detailPage === DetailPage.Files) {
      this.FilesPage()
    } else if (this.detailPage === DetailPage.Shares) {
      this.SharesPage()
    } else if (this.detailPage === DetailPage.ShareRules) {
      this.ShareRulesPage()
    } else if (this.detailPage === DetailPage.HistorySettings) {
      this.HistorySettingsPage()
    } else if (this.detailPage === DetailPage.Account) {
      this.AccountPage()
    } else if (this.detailPage === DetailPage.Security) {
      this.SecurityPage()
    } else if (this.detailPage === DetailPage.Devices) {
      this.DevicesPage()
    } else if (this.detailPage === DetailPage.Requests) {
      this.RequestsPage()
    } else if (this.detailPage === DetailPage.AdminSettings) {
      this.AdminSettingsPage()
    } else if (this.detailPage === DetailPage.AdminUsers) {
      this.AdminUsersPage()
    } else if (this.detailPage === DetailPage.AdminReviews) {
      this.AdminReviewsPage()
    }
  }

  @Builder
  private HomePage() {
    // 中文注释：首页保留鸿蒙端自己的手动同步工具，但整体层级尽量贴近 Android 首页，
    // 这样多端切换时用户先看到相同的同步面板和快捷入口。
    Scroll() {
      Column({ space: HOME_PAGE_SPACE }) {
        this.HomeSyncPanel()
        this.HomeShortcutPanel()
        this.HomeOverviewPanel()
        this.HomeSyncToolsPanel()
      }
      .width('100%')
      .padding({ left: HOME_PAGE_PADDING, right: HOME_PAGE_PADDING, top: 18, bottom: 18 })
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private HomeSyncPanel() {
    Column({ space: 14 }) {
      Stack({ alignContent: Alignment.Center }) {
        Row() {}
          .width(HOME_SYNC_HERO_SIZE)
          .height(HOME_SYNC_HERO_SIZE)
          .borderRadius(HOME_SYNC_HERO_SIZE / 2)
          .backgroundColor('#FFFFFF')
        Image(this.syncEnabled ? $r('app.media.sync_toggle_on') : $r('app.media.sync_toggle_off'))
          .width(HOME_SYNC_IMAGE_SIZE)
          .height(HOME_SYNC_IMAGE_SIZE)
          .objectFit(ImageFit.Contain)
      }
      .width('100%')
      .padding({ top: 4, bottom: 6 })
      .onClick(() => {
        this.runAsync(() => this.toggleHomeSyncEnabled())
      })

      Text(this.syncEnabled ? '同步已开启' : '同步已关闭')
        .width('100%')
        .fontSize(24)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.syncEnabled ? COLOR_PRIMARY : COLOR_TEXT)
        .textAlign(TextAlign.Center)

      Text(this.syncEnabled
        ? '已启用前台自动上传，退到后台时也会尝试补传当前剪贴板。'
        : '点击上方图标开启同步，随后复制文本时会自动上传到云端剪贴板。')
        .width('100%')
        .fontSize(13)
        .fontColor(COLOR_SUBTEXT)
        .textAlign(TextAlign.Center)

      Text('点击中间图标即可切换状态')
        .fontSize(12)
        .fontColor(this.syncEnabled ? COLOR_PRIMARY : COLOR_SUBTEXT)
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 24, bottom: 24 })
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(this.syncEnabled ? COLOR_PRIMARY_SOFT : COLOR_CARD)
    .borderRadius(28)
    .borderWidth(1)
    .borderColor(this.syncEnabled ? '#D7E6FF' : COLOR_BORDER)
  }

  @Builder
  private HomeShortcutPanel() {
    Column({ space: 14 }) {
      Text('快捷入口')
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)

      Row({ space: 12 }) {
        this.HomeShortcutCard(
          '文件',
          '上传、下载和管理文件记录',
          COLOR_PRIMARY_SOFT,
          COLOR_PRIMARY,
          () => {
            this.openDetailPage(DetailPage.Files)
          },
        )
        this.HomeShortcutCard(
          '分享',
          '创建公开链接并查看分享记录',
          COLOR_ACCENT_SOFT,
          COLOR_ACCENT,
          () => {
            this.openDetailPage(DetailPage.Shares)
          },
        )
      }
      .width('100%')
    }
    .width('100%')
    .padding(20)
    .backgroundColor(COLOR_CARD)
    .borderRadius(24)
    .borderWidth(1)
    .borderColor(COLOR_BORDER)
  }

  @Builder
  private HomeShortcutCard(
    title: string,
    description: string,
    backgroundColor: string,
    accentColor: string,
    onClick: () => void,
  ) {
    Column({ space: 10 }) {
      Blank()
        .width(34)
        .height(6)
        .borderRadius(3)
        .backgroundColor(accentColor)

      Text(title)
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)

      Text(description)
        .fontSize(12)
        .fontColor(COLOR_SUBTEXT)

      Text('进入')
        .fontSize(12)
        .fontColor(accentColor)
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
    .padding(16)
    .backgroundColor(backgroundColor)
    .borderRadius(18)
    .onClick(onClick)
  }

  @Builder
  private HomeOverviewPanel() {
    Column({ space: 10 }) {
      Text('当前账号概览')
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)

      this.KeyValueRow('用户名', this.currentSession.username.length > 0 ? this.currentSession.username : '未登录')
      this.KeyValueRow('账号角色', this.currentSession.isAdmin ? '管理员' : '普通用户')
      this.KeyValueRow('设备名称', this.currentSession.deviceName)
      this.KeyValueRow('存储配额', formatBytes(this.currentSession.storageQuotaBytes))
      this.KeyValueRow('已用空间', formatBytes(this.currentSession.storageUsedBytes))
      this.KeyValueRow('上传带宽', formatKbps(this.currentSession.uploadBandwidthKbps))
      this.KeyValueRow('下载带宽', formatKbps(this.currentSession.downloadBandwidthKbps))
      this.KeyValueRow('已 ACK 序号', `${this.historyAckSeq}`)
    }
    .width('100%')
    .padding(20)
    .backgroundColor(COLOR_CARD)
    .borderRadius(24)
    .borderWidth(1)
    .borderColor(COLOR_BORDER)
  }

  @Builder
  private HomeSyncToolsPanel() {
    Column({ space: 10 }) {
      Text('手动同步工具')
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)

      Text('这里保留鸿蒙端当前可用的前台同步操作，方便在首页直接调试和验证同步流程。')
        .fontSize(13)
        .fontColor(COLOR_SUBTEXT)

      TextArea({ text: this.homeManualText, placeholder: '手动输入一段文本后上传到云端剪贴板' })
        .height(100)
        .backgroundColor(COLOR_CARD_MUTED)
        .borderRadius(8)
        .onChange((value: string) => {
          this.homeManualText = value
        })

      Row({ space: 8 }) {
        Button(this.homeActionLoading ? '处理中...' : '上传输入框文本')
          .layoutWeight(1)
          .backgroundColor(COLOR_PRIMARY)
          .fontColor('#FFFFFF')
          .enabled(!this.homeActionLoading)
          .onClick(() => {
            this.runAsync(() => this.uploadManualText())
          })
        Button('读取系统剪贴板上传')
          .layoutWeight(1)
          .backgroundColor(COLOR_PRIMARY_SOFT)
          .fontColor(COLOR_PRIMARY)
          .enabled(!this.homeActionLoading)
          .onClick(() => {
            this.runAsync(() => this.uploadSystemClipboard())
          })
      }

      Row({ space: 8 }) {
        Button('补拉远端最新文本')
          .layoutWeight(1)
          .backgroundColor(COLOR_ACCENT_SOFT)
          .fontColor(COLOR_ACCENT)
          .enabled(!this.homeActionLoading)
          .onClick(() => {
            this.runAsync(() => this.pullRemoteClipboard())
          })
        Button('刷新历史')
          .layoutWeight(1)
          .backgroundColor(COLOR_CARD_MUTED)
          .fontColor(COLOR_TEXT)
          .enabled(!this.homeActionLoading)
          .onClick(() => {
            this.runAsync(() => this.loadHistory(true))
          })
      }
    }
    .width('100%')
    .padding(20)
    .backgroundColor(COLOR_CARD)
    .borderRadius(24)
    .borderWidth(1)
    .borderColor(COLOR_BORDER)
  }

  @Builder
  private HistoryPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.historyError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 10 }) {
          Text('历史记录')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('支持搜索当前已加载的历史，并手动上传文本到历史记录。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          Row({ space: 8 }) {
            this.InputFieldCompact('搜索历史内容', this.historySearchQuery, (value: string) => {
              this.historySearchQuery = value
            })
            Button('刷新')
              .width(72)
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .onClick(() => {
                this.runAsync(() => this.loadHistory(true))
              })
          }

          TextArea({ text: this.historyDraftText, placeholder: '直接在这里输入文本，也可以上传到历史记录' })
            .height(88)
            .backgroundColor(COLOR_CARD_MUTED)
            .borderRadius(14)
            .onChange((value: string) => {
              this.historyDraftText = value
            })

          Row({ space: 8 }) {
            Button('上传输入框文本')
              .layoutWeight(1)
              .backgroundColor(COLOR_PRIMARY)
              .fontColor('#FFFFFF')
              .enabled(!this.historyLoading)
              .onClick(() => {
                this.runAsync(() => this.uploadHistoryDraft())
              })
            Button('从系统剪贴板上传')
              .layoutWeight(1)
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .enabled(!this.historyLoading)
              .onClick(() => {
                this.runAsync(() => this.uploadSystemClipboard())
              })
          }
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        if (this.historyLoading && this.historyItems.length === 0) {
          this.MessageCard('正在加载历史记录...', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
        } else if (this.filteredHistoryItems().length === 0) {
          this.MessageCard('暂无历史记录，可以先手动上传一段文本。', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
        } else {
          Column({ space: 12 }) {
            ForEach(this.filteredHistoryItems(), (item: ClipboardItem) => {
              Column({ space: 10 }) {
                Row({ space: 8 }) {
                  Text(`#${item.seq}`)
                    .fontSize(14)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(COLOR_TEXT)
                  Text(item.isCurrentDeviceOrigin ? '本机上传' : '远端同步')
                    .fontSize(12)
                    .fontColor(item.isCurrentDeviceOrigin ? COLOR_ACCENT : COLOR_PRIMARY)
                  Blank()
                  Text(formatTime(item.createdAt))
                    .fontSize(12)
                    .fontColor(COLOR_SUBTEXT)
                }
                Text(item.textContent.length > 0 ? item.textContent : '(空文本)')
                  .fontSize(14)
                  .fontColor(COLOR_TEXT)
                  .maxLines(6)
                Row({ space: 8 }) {
                  Button('复制到系统剪贴板')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_PRIMARY_SOFT)
                    .fontColor(COLOR_PRIMARY)
                    .onClick(() => {
                      this.runAsync(() => this.copyText(item.textContent, '已复制到系统剪贴板'))
                    })
                  Button('删除')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_DANGER_SOFT)
                    .fontColor(COLOR_DANGER)
                    .onClick(() => {
                      this.runAsync(() => this.deleteHistoryItem(item.id))
                    })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
            }, (item: ClipboardItem) => item.id)
          }
          .width('100%')
        }

        if (this.historyHasMore) {
          Button(this.historyLoadingMore ? '正在加载更多...' : '加载更多')
            .width('100%')
            .backgroundColor(COLOR_CARD)
            .fontColor(COLOR_TEXT)
            .enabled(!this.historyLoadingMore)
            .onClick(() => {
              this.runAsync(() => this.loadHistory(false))
            })
        }
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private AiPage() {
    Scroll() {
      Column({ space: PAGE_SPACE }) {
        this.MessageCard('AI 功能将在后续接入。当前鸿蒙端已保留导航和页面位置，其余核心功能均可直接使用。', COLOR_WARN_SOFT, COLOR_WARN)
      }
      .width('100%')
      .padding(PAGE_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private SettingsPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.SettingSection('账号', [
          { title: '账号信息', page: DetailPage.Account },
          { title: '安全', page: DetailPage.Security },
          { title: '设备', page: DetailPage.Devices },
          { title: '申请', page: DetailPage.Requests },
        ])

        this.SettingSection('通用', [
          { title: '分享规则', page: DetailPage.ShareRules },
          { title: '历史设置', page: DetailPage.HistorySettings },
        ])

        if (this.currentSession.isAdmin) {
          this.SettingSection('管理', [
            { title: '全局设置', page: DetailPage.AdminSettings },
            { title: '用户管理', page: DetailPage.AdminUsers },
            { title: '审批', page: DetailPage.AdminReviews },
          ])
        }

        this.AboutSection()

        Column({ space: 10 }) {
          Button('退出登录')
            .width('100%')
            .backgroundColor(COLOR_DANGER)
            .fontColor('#FFFFFF')
            .onClick(() => {
              this.runAsync(() => this.logout(false))
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private SettingSection(title: string, entries: SettingLink[]) {
    Column({ space: 10 }) {
      Text(title)
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_SUBTEXT)
      Column({ space: 0 }) {
        ForEach(entries, (entry: SettingLink, index: number) => {
          Column() {
            Row({ space: 12 }) {
              Text(entry.title)
                .layoutWeight(1)
                .fontSize(16)
                .fontColor(COLOR_TEXT)
              Text('>')
                .fontSize(14)
                .fontColor(COLOR_SUBTEXT)
            }
              .width('100%')
              .padding({ left: 18, right: 18, top: 18, bottom: 18 })
              .onClick(() => {
                this.openDetailPage(entry.page)
              })
            if (index !== entries.length - 1) {
              Blank()
                .width('100%')
                .height(1)
                .backgroundColor(COLOR_BORDER)
            }
          }
        }, (entry: SettingLink) => entry.title)
      }
      .width('100%')
      .backgroundColor(COLOR_CARD)
      .borderRadius(PANEL_RADIUS)
      .borderWidth(1)
      .borderColor(COLOR_BORDER)
    }
    .width('100%')
  }

  @Builder
  private AboutSection() {
    Column({ space: 10 }) {
      Text('关于')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_SUBTEXT)

      Column({ space: 0 }) {
        this.AboutRow('GitHub 仓库', 'github.com/shuangboxu/ClipBridge')
        Blank()
          .width('100%')
          .height(1)
          .backgroundColor(COLOR_BORDER)
        this.AboutRow('Windows 下载', '即将开放')
        Blank()
          .width('100%')
          .height(1)
          .backgroundColor(COLOR_BORDER)
        this.AboutRow('Android 下载', '即将开放')
      }
      .width('100%')
      .backgroundColor(COLOR_CARD)
      .borderRadius(PANEL_RADIUS)
      .borderWidth(1)
      .borderColor(COLOR_BORDER)
    }
    .width('100%')
  }

  @Builder
  private AboutRow(title: string, value: string) {
    Row({ space: 12 }) {
      Text(title)
        .layoutWeight(1)
        .fontSize(16)
        .fontColor(COLOR_TEXT)
      Text(value)
        .fontSize(13)
        .fontColor(COLOR_SUBTEXT)
    }
    .width('100%')
    .padding({ left: 18, right: 18, top: 18, bottom: 18 })
  }

  @Builder
  private AccountPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        Column({ space: 10 }) {
          Text('账号信息')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          this.KeyValueRow('用户名', this.currentSession.username)
          this.KeyValueRow('设备名称', this.currentSession.deviceName)
          this.KeyValueRow('账号角色', this.currentSession.isAdmin ? '管理员' : '普通用户')
          this.KeyValueRow('存储配额', formatBytes(this.currentSession.storageQuotaBytes))
          this.KeyValueRow('已用空间', formatBytes(this.currentSession.storageUsedBytes))
          this.KeyValueRow('上传带宽', formatKbps(this.currentSession.uploadBandwidthKbps))
          this.KeyValueRow('下载带宽', formatKbps(this.currentSession.downloadBandwidthKbps))
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Column({ space: 10 }) {
          Text('服务器地址')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('修改后会清理当前登录态，并要求重新登录。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          this.InputField('服务地址', '保存后会清理登录态并重新登录', this.serviceAddressDraft, (value: string) => {
            this.serviceAddressDraft = value
          })
          Button('保存并重新登录')
            .width('100%')
            .backgroundColor(COLOR_PRIMARY)
            .fontColor('#FFFFFF')
            .onClick(() => {
              this.runAsync(() => this.saveServiceAddress())
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private SecurityPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        Column({ space: 10 }) {
          Text('修改密码')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('密码更新后，其它设备需要重新登录。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          this.PasswordField('当前密码', '请输入当前密码', this.securityCurrentPassword, (value: string) => {
            this.securityCurrentPassword = value
          })
          this.PasswordField('新密码', '请输入新密码', this.securityNewPassword, (value: string) => {
            this.securityNewPassword = value
          })
          this.PasswordField('确认新密码', '请再次输入新密码', this.securityConfirmPassword, (value: string) => {
            this.securityConfirmPassword = value
          })
          Button(this.securityLoading ? '修改中...' : '更新密码')
            .width('100%')
            .backgroundColor(COLOR_PRIMARY)
            .fontColor('#FFFFFF')
            .enabled(!this.securityLoading)
            .onClick(() => {
              this.runAsync(() => this.changePassword())
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private DevicesPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.devicesError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 12 }) {
          Row({ space: 12 }) {
            Column({ space: 4 }) {
              Text('设备概览')
                .fontSize(18)
                .fontWeight(FontWeight.Medium)
                .fontColor(COLOR_TEXT)
              Text('这里展示当前账号下登记的设备，以及最近活跃情况。')
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)

            Button(this.devicesLoading ? '刷新中...' : '刷新')
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .enabled(!this.devicesLoading)
              .onClick(() => {
                this.runAsync(() => this.loadDevices())
              })
          }

          this.KeyValueRow('总设备', `${this.devices.length}`)
          this.KeyValueRow('在线', `${this.onlineDeviceCount()}`)
          this.KeyValueRow('最近活动', this.latestDeviceSeenText())
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        if (this.devices.length === 0 && !this.devicesLoading) {
          this.MessageCard('当前账号下还没有可显示的设备记录。', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
        } else {
          Column({ space: 12 }) {
            ForEach(this.devices, (device: DeviceRecord) => {
              Column({ space: 10 }) {
                Row({ space: 8 }) {
                  Text(device.deviceName.length > 0 ? device.deviceName : 'unnamed-device')
                    .fontSize(18)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(COLOR_TEXT)
                  Text(device.isActive ? '在线' : '已下线')
                    .fontSize(12)
                    .fontColor(device.isActive ? COLOR_ACCENT : COLOR_WARN)
                  if (device.id === this.currentSession.currentDeviceId) {
                    Text('当前设备')
                      .fontSize(12)
                      .fontColor(COLOR_PRIMARY)
                  }
                }

                this.KeyValueRow('平台', device.platform)
                this.KeyValueRow('设备 ID', device.id)
                this.KeyValueRow('最近在线', formatTime(device.lastSeenAt))
                this.KeyValueRow('创建时间', formatTime(device.createdAt))

                this.InputFieldCompact('修改设备名', this.deviceRenameDraft(device), (value: string) => {
                  this.updateDeviceRenameDraft(device.id, value)
                })

                Row({ space: 8 }) {
                  Button('保存名称')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_PRIMARY_SOFT)
                    .fontColor(COLOR_PRIMARY)
                    .onClick(() => {
                      this.runAsync(() => this.renameDevice(device.id))
                    })
                  Button(this.devicePendingOfflineId === device.id ? '确认下线' : '强制下线')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_DANGER_SOFT)
                    .fontColor(COLOR_DANGER)
                    .onClick(() => {
                      if (this.devicePendingOfflineId === device.id) {
                        this.devicePendingOfflineId = ''
                        this.runAsync(() => this.forceOfflineDevice(device.id))
                      } else {
                        this.devicePendingOfflineId = device.id
                      }
                    })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(device.id === this.currentSession.currentDeviceId ? '#CFE0FF' : COLOR_BORDER)
            }, (device: DeviceRecord) => device.id)
          }
          .width('100%')
        }
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private FilesPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.filesError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 12 }) {
          Text('文件中心')
            .fontSize(20)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('支持搜索当前已加载的文件记录，并直接进行下载、重命名和删除。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          this.InputFieldCompact('本地搜索文件名、类型或来源设备', this.filesSearchQuery, (value: string) => {
            this.filesSearchQuery = value
          })
          this.KeyValueRow('文件总数', `${this.filesTotalFiles}`)
          this.KeyValueRow('文件总大小', formatBytes(this.filesTotalBytes))
          this.KeyValueRow('单文件上限', formatBytes(this.filesMaxUploadBytes))

          Row({ space: 8 }) {
            Button('选择文件上传')
              .layoutWeight(1)
              .backgroundColor(COLOR_PRIMARY)
              .fontColor('#FFFFFF')
              .enabled(!this.filesLoading)
              .onClick(() => {
                this.runAsync(() => this.uploadFile())
              })
            Button(this.filesLoading ? '刷新中...' : '刷新')
              .layoutWeight(1)
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .enabled(!this.filesLoading)
              .onClick(() => {
                this.runAsync(() => this.loadFiles(this.filesPage))
              })
          }
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        if (this.filteredFiles().length === 0 && !this.filesLoading) {
          this.MessageCard(
            this.filesSearchQuery.trim().length > 0 ? '当前页没有匹配的文件记录' : '还没有文件记录',
            COLOR_PRIMARY_SOFT,
            COLOR_PRIMARY,
          )
        } else {
          Column({ space: 12 }) {
            ForEach(this.filteredFiles(), (file: FileRecord) => {
              Column({ space: 10 }) {
                Text(file.originalName.length > 0 ? file.originalName : '未命名文件')
                  .fontSize(18)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(COLOR_TEXT)
                Text(`${formatBytes(file.sizeBytes)} · ${formatTime(file.createdAt)}`)
                  .fontSize(13)
                  .fontColor(COLOR_SUBTEXT)
                this.KeyValueRow('类型', file.contentType.length > 0 ? file.contentType : 'application/octet-stream')
                this.KeyValueRow('来源设备', file.originDeviceName.length > 0 ? file.originDeviceName : '未知设备')
                this.InputFieldCompact('重命名', this.fileRenameDraft(file), (value: string) => {
                  this.updateFileRenameDraft(file.id, value)
                })
                Row({ space: 8 }) {
                  Button('下载')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_ACCENT_SOFT)
                    .fontColor(COLOR_ACCENT)
                    .onClick(() => {
                      this.runAsync(() => this.downloadFile(file))
                    })
                  Button('保存文件名')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_PRIMARY_SOFT)
                    .fontColor(COLOR_PRIMARY)
                    .onClick(() => {
                      this.runAsync(() => this.renameFile(file.id))
                    })
                  Button(this.filePendingDeleteId === file.id ? '再次确认删除' : '删除')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_DANGER_SOFT)
                    .fontColor(COLOR_DANGER)
                    .onClick(() => {
                      if (this.filePendingDeleteId === file.id) {
                        this.filePendingDeleteId = ''
                        this.runAsync(() => this.deleteFile(file.id))
                      } else {
                        this.filePendingDeleteId = file.id
                      }
                    })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
            }, (file: FileRecord) => file.id)
          }
          .width('100%')
        }

        Row({ space: 8 }) {
          Button('上一页')
            .layoutWeight(1)
            .backgroundColor(COLOR_CARD_MUTED)
            .fontColor(COLOR_TEXT)
            .enabled(this.filesPage > 1 && !this.filesLoading)
            .onClick(() => {
              this.runAsync(() => this.loadFiles(this.filesPage - 1))
            })
          Button(`第 ${this.filesPage} / ${Math.max(1, this.filesTotalPages)} 页`)
            .layoutWeight(1)
            .backgroundColor(COLOR_CARD)
            .fontColor(COLOR_TEXT)
          Button('下一页')
            .layoutWeight(1)
            .backgroundColor(COLOR_CARD_MUTED)
            .fontColor(COLOR_TEXT)
            .enabled(this.filesPage < this.filesTotalPages && !this.filesLoading)
            .onClick(() => {
              this.runAsync(() => this.loadFiles(this.filesPage + 1))
            })
        }
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private SharesPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.sharesError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 10 }) {
          Text('创建分享')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('先选择分享类型和策略，再创建文本或文件分享。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          this.SegmentRow(this.buildShareComposeButtons())

          this.SegmentRow(this.buildShareStrategyButtons())
          Text(this.currentShareStrategySummary().description)
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          Text(this.currentShareStrategySummary().copyLabel)
            .fontSize(12)
            .fontColor(COLOR_SUBTEXT)

          if (this.shareComposeMode === ShareComposeMode.Text) {
            TextArea({ text: this.shareTextInput, placeholder: '请输入要分享的文本内容' })
              .height(100)
              .backgroundColor(COLOR_CARD_MUTED)
              .borderRadius(14)
              .onChange((value: string) => {
                this.shareTextInput = value
              })
          } else {
            this.MessageCard('文件分享会先弹出文件选择器，随后直接上传并创建公开链接。', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
          }

          Button(this.sharesCreating ? '创建中...' : '创建分享')
            .width('100%')
            .backgroundColor(COLOR_PRIMARY)
            .fontColor('#FFFFFF')
            .enabled(!this.sharesCreating)
            .onClick(() => {
              this.runAsync(() => this.createShare())
            })

          if (this.shareLastLink.length > 0) {
            Row({ space: 8 }) {
              Text(this.shareLastLink)
                .layoutWeight(1)
                .fontSize(12)
                .fontColor(COLOR_SUBTEXT)
              Button('复制链接')
                .backgroundColor(COLOR_ACCENT_SOFT)
                .fontColor(COLOR_ACCENT)
                .onClick(() => {
                  this.runAsync(() => this.copyText(this.shareLastLink, '公开链接已复制'))
                })
            }
          }
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Column({ space: 10 }) {
          Text('分享记录')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          this.SegmentRow(this.buildShareStatusButtons())
          Button(this.sharesLoading ? '刷新中...' : '刷新分享列表')
            .width('100%')
            .backgroundColor(COLOR_CARD_MUTED)
            .fontColor(COLOR_TEXT)
            .enabled(!this.sharesLoading)
            .onClick(() => {
              this.runAsync(() => this.loadShares(1))
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        if (this.shares.length === 0 && !this.sharesLoading) {
          this.MessageCard('还没有分享记录，先创建一个文本分享或文件分享。', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
        } else {
          Column({ space: 12 }) {
            ForEach(this.shares, (share: ShareRecord) => {
              Column({ space: 10 }) {
                Row({ space: 8 }) {
                  Text(share.contentKind === 'file' ? '文件分享' : '文本分享')
                    .fontSize(18)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(COLOR_TEXT)
                  Text(statusLabel(share.status))
                    .fontSize(12)
                    .fontColor(this.shareStatusColor(share.status))
                }
                if (share.textPreview.length > 0) {
                  Text(share.textPreview)
                    .fontSize(14)
                    .fontColor(COLOR_TEXT)
                    .maxLines(4)
                }
                if (share.file !== null) {
                  this.KeyValueRow('文件名', share.file.originalName)
                  this.KeyValueRow('文件大小', formatBytes(share.file.sizeBytes))
                }
                this.KeyValueRow('剩余时间', remainingSecondsText(share.remainingSeconds))
                this.KeyValueRow('创建时间', formatTime(share.createdAt))
                Row({ space: 8 }) {
                  Button('复制公开链接')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_PRIMARY_SOFT)
                    .fontColor(COLOR_PRIMARY)
                    .onClick(() => {
                      this.runAsync(() => this.copyText(this.resolveShareLink(share), '公开链接已复制'))
                    })
                  Button(this.sharePendingRevokeId === share.id ? '再次确认撤销' : '撤销')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_DANGER_SOFT)
                    .fontColor(COLOR_DANGER)
                    .enabled(share.status.toLowerCase() === 'active')
                    .onClick(() => {
                      if (this.sharePendingRevokeId === share.id) {
                        this.sharePendingRevokeId = ''
                        this.runAsync(() => this.revokeShare(share.id))
                      } else {
                        this.sharePendingRevokeId = share.id
                      }
                    })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
            }, (share: ShareRecord) => share.id)
          }
          .width('100%')
        }
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private ShareRulesPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        Column({ space: 10 }) {
          Text('不过期')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('公开链接不会自动失效，需要你主动撤销。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          Toggle({ type: ToggleType.Switch, isOn: this.shareRules.never.allowCopyText })
            .selectedColor(COLOR_ACCENT)
            .onChange((value: boolean) => {
              this.updateShareRuleNeverAllowCopy(value)
            })
          Text('允许公开页面直接复制文本内容')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Column({ space: 10 }) {
          Text('过期')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('这组规则会给文字分享加自动过期时间。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          this.SegmentRow(this.buildExpirePresetButtons())
          Toggle({ type: ToggleType.Switch, isOn: this.shareRules.expire.allowCopyText })
            .selectedColor(COLOR_ACCENT)
            .onChange((value: boolean) => {
              this.updateShareRuleExpireAllowCopy(value)
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Column({ space: 10 }) {
          Text('打开一次失效')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('可以直接一次焚毁，也可以在首次打开后再走倒计时焚毁。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          Toggle({ type: ToggleType.Switch, isOn: this.shareRules.once.showCountdown })
            .selectedColor(COLOR_ACCENT)
            .onChange((value: boolean) => {
              this.updateShareRuleOnceShowCountdown(value)
            })
          Text(this.shareRules.once.showCountdown ? '首次打开后倒计时焚毁' : '只允许成功打开一次')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          if (this.shareRules.once.showCountdown) {
            this.SegmentRow(this.buildCountdownPresetButtons())
          }
          Toggle({ type: ToggleType.Switch, isOn: this.shareRules.once.allowCopyText })
            .selectedColor(COLOR_ACCENT)
            .onChange((value: boolean) => {
              this.updateShareRuleOnceAllowCopy(value)
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Button('保存本地分享规则')
          .width('100%')
          .backgroundColor(COLOR_PRIMARY)
          .fontColor('#FFFFFF')
          .onClick(() => {
            this.saveShareRules()
          })
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private HistorySettingsPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        Column({ space: 10 }) {
          Text('历史保留策略')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('保留天数填 0 表示不限时间，默认最多保留 1000 条。所有端共用同一套设置。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          this.InputField('保留天数', '0 表示不限时间', this.historyRetentionDaysInput, (value: string) => {
            this.historyRetentionDaysInput = value
          })
          this.InputField('最大记录数', '默认 1000', this.historyLimitInput, (value: string) => {
            this.historyLimitInput = value
          })
          Button(this.historySettingsLoading ? '保存中...' : '保存历史设置')
            .width('100%')
            .backgroundColor(COLOR_PRIMARY)
            .fontColor('#FFFFFF')
            .enabled(!this.historySettingsLoading)
            .onClick(() => {
              this.runAsync(() => this.saveHistorySettings())
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Column({ space: 10 }) {
          Text('立即清理')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          Text('如果你刚调整了规则，或者想立刻释放历史列表，可以直接执行下面的清理操作。')
            .fontSize(13)
            .fontColor(COLOR_SUBTEXT)
          this.KeyValueRow('最近更新时间', formatTime(this.historySettings.updatedAt))
          Button('清理超出保留天数的记录')
            .width('100%')
            .backgroundColor(COLOR_WARN_SOFT)
            .fontColor(COLOR_WARN)
            .enabled(!this.historySettingsLoading)
            .onClick(() => {
              this.runAsync(() => this.cleanupHistory())
            })
          Button('清空全部历史记录')
            .width('100%')
            .backgroundColor(COLOR_DANGER_SOFT)
            .fontColor(COLOR_DANGER)
            .enabled(!this.historySettingsLoading)
            .onClick(() => {
              this.runAsync(() => this.clearHistory())
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private RequestsPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.requestsError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 10 }) {
          Row({ space: 12 }) {
            Column({ space: 4 }) {
              Text('当前额度')
                .fontSize(18)
                .fontWeight(FontWeight.Medium)
                .fontColor(COLOR_TEXT)
              Text('申请提交后会进入管理员审核队列，审核通过后会立即生效。')
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)

            Button(this.requestsLoading ? '刷新中...' : '刷新')
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .enabled(!this.requestsLoading)
              .onClick(() => {
                this.runAsync(() => this.loadRequests())
              })
          }
          this.KeyValueRow('角色', this.currentSession.isAdmin ? '管理员' : '普通用户')
          this.KeyValueRow('存储配额', formatBytes(this.currentSession.storageQuotaBytes))
          this.KeyValueRow('已用空间', formatBytes(this.currentSession.storageUsedBytes))
          this.KeyValueRow('剩余空间', formatBytes(this.currentSession.storageFreeBytes))
          this.KeyValueRow('上传带宽', formatKbps(this.currentSession.uploadBandwidthKbps))
          this.KeyValueRow('下载带宽', formatKbps(this.currentSession.downloadBandwidthKbps))
          this.KeyValueRow('单文件上限', formatBytes(this.currentSession.maxUploadFileBytes))
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Column({ space: 10 }) {
          Text('我的申请')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          this.SegmentRow(this.buildRequestStatusButtons())
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        this.RequestFormCard(
          '存储配额申请',
          '输入希望提升到的目标配额，单位是 MB。',
          this.quotaRequestMbInput,
          '期望配额（MB）',
          (value: string) => { this.quotaRequestMbInput = value },
          this.quotaRequestReason,
          '申请原因',
          (value: string) => { this.quotaRequestReason = value },
          () => { this.runAsync(() => this.submitQuotaRequest()) },
        )

        this.RequestFormCard(
          '带宽申请',
          '上传和下载都按 MB/s 填写，至少填一个高于当前值的目标更合理。',
          this.bandwidthUploadInput,
          '期望上传带宽（MB/s）',
          (value: string) => { this.bandwidthUploadInput = value },
          this.bandwidthReason,
          '申请原因',
          (value: string) => { this.bandwidthReason = value },
          () => { this.runAsync(() => this.submitBandwidthRequest()) },
          this.bandwidthDownloadInput,
          '期望下载带宽（MB/s）',
          (value: string) => { this.bandwidthDownloadInput = value },
        )

        this.RequestFormCard(
          '管理员申请',
          '说明为什么需要管理员权限，以及主要会做哪些管理操作。',
          '',
          '',
          (_value: string) => {},
          this.adminRequestReason,
          '申请原因',
          (value: string) => { this.adminRequestReason = value },
          () => { this.runAsync(() => this.submitAdminRequest()) },
          '',
          '',
          (_value: string) => {},
          true,
        )

        this.RequestRecordSection('配额申请记录', this.quotaRequests.map((item: QuotaRequestRecord) => {
          return `${item.username} / 目标 ${formatBytes(item.requestedQuotaBytes)} / ${statusLabel(item.status)}`
        }))
        this.RequestRecordSection('带宽申请记录', this.bandwidthRequests.map((item: BandwidthRequestRecord) => {
          return `${item.username} / 上行 ${formatKbps(item.requestedUploadKbps)} / 下行 ${formatKbps(item.requestedDownloadKbps)} / ${statusLabel(item.status)}`
        }))
        this.RequestRecordSection('管理员申请记录', this.adminRequests.map((item: AdminRequestRecord) => {
          return `${item.username} / ${statusLabel(item.status)} / ${item.reason}`
        }))
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private RequestFormCard(
    title: string,
    description: string,
    firstValue: string,
    firstPlaceholder: string,
    onFirstChange: (value: string) => void,
    reasonValue: string,
    reasonPlaceholder: string,
    onReasonChange: (value: string) => void,
    onSubmit: () => void,
    secondValue: string = '',
    secondPlaceholder: string = '',
    onSecondChange: (value: string) => void = (_value: string) => {},
    hideFirstField: boolean = false,
  ) {
    Column({ space: 10 }) {
      Text(title)
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)
      Text(description)
        .fontSize(13)
        .fontColor(COLOR_SUBTEXT)
      if (!hideFirstField) {
        this.InputFieldCompact(firstPlaceholder, firstValue, onFirstChange)
      }
      if (secondPlaceholder.length > 0) {
        this.InputFieldCompact(secondPlaceholder, secondValue, onSecondChange)
      }
      TextArea({ text: reasonValue, placeholder: reasonPlaceholder })
        .height(84)
        .backgroundColor(COLOR_CARD_MUTED)
        .borderRadius(8)
        .onChange(onReasonChange)
      Button('提交申请')
        .width('100%')
        .backgroundColor(COLOR_PRIMARY)
        .fontColor('#FFFFFF')
        .enabled(!this.requestsLoading)
        .onClick(onSubmit)
    }
    .width('100%')
    .padding(18)
    .backgroundColor(COLOR_CARD)
    .borderRadius(PANEL_RADIUS)
    .borderWidth(1)
    .borderColor(COLOR_BORDER)
  }

  @Builder
  private RequestRecordSection(title: string, rows: string[]) {
    Column({ space: 10 }) {
      Text(title)
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)
      if (rows.length === 0) {
        this.MessageCard('暂无记录', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
      } else {
        Column({ space: 8 }) {
          ForEach(rows, (row: string) => {
            Text(row)
              .fontSize(13)
              .fontColor(COLOR_TEXT)
              .width('100%')
              .padding(14)
              .backgroundColor(COLOR_CARD)
              .borderRadius(16)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
          })
        }
        .width('100%')
      }
    }
    .width('100%')
  }

  @Builder
  private AdminSettingsPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.adminSettingsError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 10 }) {
          Row({ space: 12 }) {
            Column({ space: 4 }) {
              Text('系统设置')
                .fontSize(18)
                .fontWeight(FontWeight.Medium)
                .fontColor(COLOR_TEXT)
              Text('这里的默认值会影响新账号和系统限制，修改后会立即生效。')
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)

            Button(this.adminSettingsLoading ? '刷新中...' : '刷新')
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .enabled(!this.adminSettingsLoading)
              .onClick(() => {
                this.runAsync(() => this.loadAdminSettings())
              })
          }
          this.KeyValueRow('当前用户数', `${this.adminCurrentUserCount}`)
          this.KeyValueRow('上次更新时间', formatTime(this.adminSettings.updatedAt))
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        Column({ space: 10 }) {
          Text('可编辑项')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(COLOR_TEXT)
          this.InputFieldCompact('最大用户数', this.adminMaxUserCountInput, (value: string) => {
            this.adminMaxUserCountInput = value
          })
          this.InputFieldCompact('默认存储配额（MB）', this.adminDefaultQuotaMbInput, (value: string) => {
            this.adminDefaultQuotaMbInput = value
          })
          this.InputFieldCompact('默认上传带宽（MB/s）', this.adminDefaultUploadInput, (value: string) => {
            this.adminDefaultUploadInput = value
          })
          this.InputFieldCompact('默认下载带宽（MB/s）', this.adminDefaultDownloadInput, (value: string) => {
            this.adminDefaultDownloadInput = value
          })
          this.InputFieldCompact('用户上传带宽上限（MB/s）', this.adminMaxUserUploadInput, (value: string) => {
            this.adminMaxUserUploadInput = value
          })
          this.InputFieldCompact('用户下载带宽上限（MB/s）', this.adminMaxUserDownloadInput, (value: string) => {
            this.adminMaxUserDownloadInput = value
          })
          this.InputFieldCompact('单文件上传上限（MB）', this.adminMaxUploadFileMbInput, (value: string) => {
            this.adminMaxUploadFileMbInput = value
          })
          Row({ space: 8 }) {
            Text('允许注册')
              .layoutWeight(1)
              .fontSize(14)
              .fontColor(COLOR_TEXT)
            Toggle({ type: ToggleType.Switch, isOn: this.adminAllowRegistration })
              .selectedColor(COLOR_ACCENT)
              .onChange((value: boolean) => {
                this.adminAllowRegistration = value
              })
          }
          Button(this.adminSettingsLoading ? '保存中...' : '保存全局设置')
            .width('100%')
            .backgroundColor(COLOR_PRIMARY)
            .fontColor('#FFFFFF')
            .enabled(!this.adminSettingsLoading)
            .onClick(() => {
              this.runAsync(() => this.saveAdminSettings())
            })
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private AdminUsersPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.adminUsersError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 10 }) {
          Row({ space: 12 }) {
            Column({ space: 4 }) {
              Text('用户管理')
                .fontSize(18)
                .fontWeight(FontWeight.Medium)
                .fontColor(COLOR_TEXT)
              Text('这里可以调整用户额度、带宽和管理员权限，也可以删除账号。')
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)

            Button(this.adminUsersLoading ? '刷新中...' : '刷新')
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .enabled(!this.adminUsersLoading)
              .onClick(() => {
                this.runAsync(() => this.loadAdminUsers())
              })
          }
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        if (this.adminUsers.length === 0 && !this.adminUsersLoading) {
          this.MessageCard('暂无用户数据', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
        } else {
          Column({ space: 12 }) {
            ForEach(this.adminUsers, (user: AdminUserRecord) => {
              Column({ space: 10 }) {
                Text(user.username)
                  .fontSize(18)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(COLOR_TEXT)
                Text(user.isAdmin ? '管理员' : '普通用户')
                  .fontSize(13)
                  .fontColor(COLOR_SUBTEXT)
                this.KeyValueRow('存储配额', formatBytes(user.storageQuotaBytes))
                this.KeyValueRow('已用空间', formatBytes(user.storageUsedBytes))
                this.KeyValueRow('剩余空间', formatBytes(user.storageFreeBytes))
                this.KeyValueRow('待审批', this.pendingFlags(user))
                this.InputFieldCompact('存储配额（MB）', this.adminUserDraft(user).storageQuotaMb, (value: string) => {
                  this.updateAdminUserStorageQuota(user, value)
                })
                this.InputFieldCompact('上传带宽（MB/s）', this.adminUserDraft(user).uploadBandwidthKbps, (value: string) => {
                  this.updateAdminUserUploadBandwidth(user, value)
                })
                this.InputFieldCompact('下载带宽（MB/s）', this.adminUserDraft(user).downloadBandwidthKbps, (value: string) => {
                  this.updateAdminUserDownloadBandwidth(user, value)
                })
                Row({ space: 8 }) {
                  Text('管理员')
                    .layoutWeight(1)
                    .fontSize(14)
                    .fontColor(COLOR_TEXT)
                  Toggle({ type: ToggleType.Switch, isOn: this.adminUserDraft(user).isAdmin })
                    .selectedColor(COLOR_ACCENT)
                    .onChange((value: boolean) => {
                      this.updateAdminUserAdminFlag(user, value)
                    })
                }
                Row({ space: 8 }) {
                  Button('保存')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_PRIMARY_SOFT)
                    .fontColor(COLOR_PRIMARY)
                    .onClick(() => {
                      this.runAsync(() => this.saveAdminUser(user.id))
                    })
                  Button(this.adminUserPendingDeleteId === user.id ? '再次确认删除' : '删除用户')
                    .layoutWeight(1)
                    .backgroundColor(COLOR_DANGER_SOFT)
                    .fontColor(COLOR_DANGER)
                    .onClick(() => {
                      if (this.adminUserPendingDeleteId === user.id) {
                        this.adminUserPendingDeleteId = ''
                        this.runAsync(() => this.deleteAdminUser(user.id))
                      } else {
                        this.adminUserPendingDeleteId = user.id
                      }
                    })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
            }, (user: AdminUserRecord) => user.id)
          }
          .width('100%')
        }
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private AdminReviewsPage() {
    Scroll() {
      Column({ space: CONTENT_SPACE }) {
        this.MessageCard(this.adminReviewsError, COLOR_DANGER_SOFT, COLOR_DANGER)

        Column({ space: 10 }) {
          Row({ space: 12 }) {
            Column({ space: 4 }) {
              Text('待审批队列')
                .fontSize(18)
                .fontWeight(FontWeight.Medium)
                .fontColor(COLOR_TEXT)
              Text('这里只展示待处理申请，批准或拒绝后会自动从当前列表移除。')
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)

            Button(this.adminReviewsLoading ? '刷新中...' : '刷新')
              .backgroundColor(COLOR_PRIMARY_SOFT)
              .fontColor(COLOR_PRIMARY)
              .enabled(!this.adminReviewsLoading)
              .onClick(() => {
                this.runAsync(() => this.loadAdminReviews())
              })
          }
        }
        .width('100%')
        .padding(18)
        .backgroundColor(COLOR_CARD)
        .borderRadius(PANEL_RADIUS)
        .borderWidth(1)
        .borderColor(COLOR_BORDER)

        this.QuotaReviewSection()
        this.BandwidthReviewSection()
        this.AdminReviewSection()
      }
      .width('100%')
      .padding(CONTENT_PADDING)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private QuotaReviewSection() {
    Column({ space: 10 }) {
      Text('配额审批')
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)
      if (this.pendingQuotaReviews.length === 0) {
        this.MessageCard('暂无待审批配额申请', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
      } else {
        Column({ space: 12 }) {
          ForEach(this.pendingQuotaReviews, (item: QuotaRequestRecord) => {
            Column({ space: 10 }) {
              Text(`${item.username} / 当前 ${formatBytes(item.currentQuotaBytes)} / 申请 ${formatBytes(item.requestedQuotaBytes)}`)
                .fontSize(14)
                .fontColor(COLOR_TEXT)
              Text(item.reason)
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
              this.InputFieldCompact('批准配额（MB）', this.quotaReviewDraft(item).approvedQuotaMb, (value: string) => {
                this.updateQuotaReviewApprovedQuota(item, value)
              })
              this.InputFieldCompact('审核备注', this.quotaReviewDraft(item).reviewNote, (value: string) => {
                this.updateQuotaReviewNote(item, value)
              })
              Row({ space: 8 }) {
                Button('批准')
                  .layoutWeight(1)
                  .backgroundColor(COLOR_ACCENT_SOFT)
                  .fontColor(COLOR_ACCENT)
                  .onClick(() => {
                    this.runAsync(() => this.approveQuotaRequest(item.id))
                  })
                Button('拒绝')
                  .layoutWeight(1)
                  .backgroundColor(COLOR_DANGER_SOFT)
                  .fontColor(COLOR_DANGER)
                  .onClick(() => {
                    this.runAsync(() => this.rejectQuotaRequest(item.id))
                  })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
            }, (item: QuotaRequestRecord) => item.id)
        }
        .width('100%')
      }
    }
    .width('100%')
  }

  @Builder
  private BandwidthReviewSection() {
    Column({ space: 10 }) {
      Text('带宽审批')
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)
      if (this.pendingBandwidthReviews.length === 0) {
        this.MessageCard('暂无待审批带宽申请', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
      } else {
        Column({ space: 12 }) {
          ForEach(this.pendingBandwidthReviews, (item: BandwidthRequestRecord) => {
            Column({ space: 10 }) {
              Text(`${item.username} / 上行 ${formatKbps(item.currentUploadKbps)} -> ${formatKbps(item.requestedUploadKbps)}`)
                .fontSize(14)
                .fontColor(COLOR_TEXT)
              Text(`下行 ${formatKbps(item.currentDownloadKbps)} -> ${formatKbps(item.requestedDownloadKbps)}`)
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
              this.InputFieldCompact('批准上传（MB/s）', this.bandwidthReviewDraft(item).approvedUploadKbps, (value: string) => {
                this.updateBandwidthReviewApprovedUpload(item, value)
              })
              this.InputFieldCompact('批准下载（MB/s）', this.bandwidthReviewDraft(item).approvedDownloadKbps, (value: string) => {
                this.updateBandwidthReviewApprovedDownload(item, value)
              })
              this.InputFieldCompact('审核备注', this.bandwidthReviewDraft(item).reviewNote, (value: string) => {
                this.updateBandwidthReviewNote(item, value)
              })
              Row({ space: 8 }) {
                Button('批准')
                  .layoutWeight(1)
                  .backgroundColor(COLOR_ACCENT_SOFT)
                  .fontColor(COLOR_ACCENT)
                  .onClick(() => {
                    this.runAsync(() => this.approveBandwidthRequest(item.id))
                  })
                Button('拒绝')
                  .layoutWeight(1)
                  .backgroundColor(COLOR_DANGER_SOFT)
                  .fontColor(COLOR_DANGER)
                  .onClick(() => {
                    this.runAsync(() => this.rejectBandwidthRequest(item.id))
                  })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
            }, (item: BandwidthRequestRecord) => item.id)
        }
        .width('100%')
      }
    }
    .width('100%')
  }

  @Builder
  private AdminReviewSection() {
    Column({ space: 10 }) {
      Text('管理员审批')
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor(COLOR_TEXT)
      if (this.pendingAdminReviews.length === 0) {
        this.MessageCard('暂无待审批管理员申请', COLOR_PRIMARY_SOFT, COLOR_PRIMARY)
      } else {
        Column({ space: 12 }) {
          ForEach(this.pendingAdminReviews, (item: AdminRequestRecord) => {
            Column({ space: 10 }) {
              Text(item.username)
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .fontColor(COLOR_TEXT)
              Text(item.reason)
                .fontSize(13)
                .fontColor(COLOR_SUBTEXT)
              this.InputFieldCompact('审核备注', this.adminReviewNote(item.id), (value: string) => {
                this.updateAdminReviewNote(item.id, value)
              })
              Row({ space: 8 }) {
                Button('批准')
                  .layoutWeight(1)
                  .backgroundColor(COLOR_ACCENT_SOFT)
                  .fontColor(COLOR_ACCENT)
                  .onClick(() => {
                    this.runAsync(() => this.approveAdminRequest(item.id))
                  })
                Button('拒绝')
                  .layoutWeight(1)
                  .backgroundColor(COLOR_DANGER_SOFT)
                  .fontColor(COLOR_DANGER)
                  .onClick(() => {
                    this.runAsync(() => this.rejectAdminRequest(item.id))
                  })
                }
              }
              .width('100%')
              .padding(18)
              .backgroundColor(COLOR_CARD)
              .borderRadius(PANEL_RADIUS)
              .borderWidth(1)
              .borderColor(COLOR_BORDER)
            }, (item: AdminRequestRecord) => item.id)
        }
        .width('100%')
      }
    }
    .width('100%')
  }

  @Builder
  private InputField(title: string, placeholder: string, value: string, onChange: (value: string) => void) {
    Column({ space: 6 }) {
      Text(title)
        .fontSize(14)
        .fontColor(COLOR_TEXT)
      TextInput({ text: value, placeholder })
        .width('100%')
        .height(46)
        .backgroundColor(COLOR_CARD_MUTED)
        .borderRadius(14)
        .onChange(onChange)
    }
    .width('100%')
  }

  @Builder
  private InputFieldCompact(placeholder: string, value: string, onChange: (value: string) => void) {
    TextInput({ text: value, placeholder })
      .width('100%')
      .height(44)
      .backgroundColor(COLOR_CARD_MUTED)
      .borderRadius(14)
      .onChange(onChange)
  }

  @Builder
  private PasswordField(title: string, placeholder: string, value: string, onChange: (value: string) => void) {
    Column({ space: 6 }) {
      Text(title)
        .fontSize(14)
        .fontColor(COLOR_TEXT)
      TextInput({ text: value, placeholder })
        .type(InputType.Password)
        .width('100%')
        .height(46)
        .backgroundColor(COLOR_CARD_MUTED)
        .borderRadius(14)
        .onChange(onChange)
    }
    .width('100%')
  }

  @Builder
  private MessageCard(message: string, backgroundColor: string, textColor: string) {
    if (message.trim().length > 0) {
      Text(message)
        .width('100%')
        .padding(14)
        .fontSize(13)
        .fontColor(textColor)
        .backgroundColor(backgroundColor)
        .borderRadius(16)
    }
  }

  @Builder
  private KeyValueRow(label: string, value: string) {
    Row({ space: 12 }) {
      Text(label)
        .width(96)
        .fontSize(14)
        .fontColor(COLOR_SUBTEXT)
      Text(value.length > 0 ? value : '-')
        .layoutWeight(1)
        .fontSize(14)
        .fontColor(COLOR_TEXT)
    }
    .width('100%')
  }

  @Builder
  private SegmentRow(items: SegmentOption[]) {
    Row({ space: 8 }) {
      ForEach(items, (item: SegmentOption) => {
        Button(item.label)
          .layoutWeight(1)
          .height(38)
          .backgroundColor(item.selected ? COLOR_PRIMARY : COLOR_CARD_MUTED)
          .fontColor(item.selected ? '#FFFFFF' : COLOR_TEXT)
          .onClick(item.onClick)
      }, (item: SegmentOption) => item.label)
    }
    .width('100%')
  }

  private async initializeApp(): Promise<void> {
    this.ensureServices()
    this.syncEnabled = this.sessionStore?.isSyncEnabled() ?? false
    this.historyAckSeq = this.sessionStore?.readLastAckSeq() ?? 0
    this.shareRules = this.shareRulesStore?.readRules() ?? buildDefaultShareRules()

    const storedSession = this.sessionStore?.readSession() ?? emptySession()
    this.currentSession = storedSession
    this.authBaseUrl = storedSession.baseUrl
    this.serviceAddressDraft = storedSession.baseUrl
    this.authDeviceName = storedSession.deviceName.length > 0 ? storedSession.deviceName : defaultDeviceName()

    if (!hasCompleteAuth(storedSession)) {
      this.bootLoading = false
      return
    }

    try {
      const profile = await this.requiredApi().getCurrentAccount()
      this.applyProfile(profile)
      this.loggedIn = true
      this.bootLoading = false
      this.refreshClipboardObserver()
      await this.loadHistory(true)
    } catch (error) {
      await this.handleProtectedError(error, '登录态已失效，请重新登录')
      this.refreshClipboardObserver()
      this.bootLoading = false
    }
  }

  private ensureServices(): void {
    if (this.initialized) {
      return
    }
    const context = getContext(this) as common.UIAbilityContext
    this.sessionStore = new SessionStore(context)
    this.shareRulesStore = new ShareRulesStore(context)
    this.apiClient = new ApiClient(
      () => this.currentSession,
      (tokens: TokenBundle) => {
        this.applyTokens(tokens)
      },
    )
    this.clipboardSyncRuntime = new ClipboardSyncRuntime(context)
    this.fileTransfer = new FileTransfer(() => context, this.apiClient)
    this.initialized = true
  }

  private requiredApi(): ApiClient {
    this.ensureServices()
    if (!this.apiClient) {
      throw new ApiError('API 客户端尚未初始化')
    }
    return this.apiClient
  }

  private requiredClipboardSyncRuntime(): ClipboardSyncRuntime {
    this.ensureServices()
    if (!this.clipboardSyncRuntime) {
      throw new ApiError('同步运行时尚未初始化')
    }
    return this.clipboardSyncRuntime
  }

  private runAsync(task: () => Promise<void>): void {
    task().catch((error: Object) => {
      this.showToast(this.extractErrorMessage(error))
    })
  }

  private async submitAuth(): Promise<void> {
    const baseUrl = this.authBaseUrl.trim()
    const username = this.authUsername.trim().toLowerCase()
    const password = this.authPassword.trim()
    const deviceName = this.authDeviceName.trim().length > 0 ? this.authDeviceName.trim() : defaultDeviceName()
    if (baseUrl.length === 0 || username.length === 0 || password.length === 0) {
      this.authMessage = '服务地址、用户名和密码不能为空'
      return
    }

    this.authLoading = true
    this.authMessage = ''
    try {
      const result = this.authMode === AuthMode.Login
        ? await this.requiredApi().login(baseUrl, username, password, deviceName)
        : await this.requiredApi().register(baseUrl, username, password, deviceName)
      await this.completeAuth(result, baseUrl, deviceName)
    } catch (error) {
      this.authMessage = this.extractErrorMessage(error)
    } finally {
      this.authLoading = false
    }
  }

  private async completeAuth(result: AuthResult, baseUrl: string, deviceName: string): Promise<void> {
    const session: StoredSession = {
      baseUrl: baseUrl.trim(),
      accessToken: result.tokens.accessToken,
      refreshToken: result.tokens.refreshToken,
      currentDeviceId: result.currentDeviceId,
      username: result.username,
      deviceName,
      isAdmin: result.isAdmin,
      storageQuotaBytes: result.storageQuotaBytes,
      storageUsedBytes: 0,
      storageFreeBytes: 0,
      uploadBandwidthKbps: result.uploadBandwidthKbps,
      downloadBandwidthKbps: result.downloadBandwidthKbps,
      maxUploadFileBytes: 0,
    }
    this.currentSession = session
    this.authBaseUrl = session.baseUrl
    this.serviceAddressDraft = session.baseUrl
    this.authDeviceName = deviceName
    this.sessionStore?.saveAuthBundle(session, result.tokens)
    this.sessionStore?.saveDeviceName(deviceName)

    const profile = await this.requiredApi().getCurrentAccount()
    this.applyProfile(profile)
    this.loggedIn = true
    this.authPassword = ''
    this.authMessage = ''
    this.setActiveTab(MainTab.Home)
    this.detailPage = DetailPage.None
    this.resetProtectedData(false)
    this.refreshClipboardObserver()
    await this.loadHistory(true)
    this.showToast('登录成功')
  }

  private applyProfile(profile: AccountProfile): void {
    const nextSession = copyStoredSession(this.currentSession)
    nextSession.username = profile.username
    nextSession.currentDeviceId = profile.currentDeviceId
    nextSession.isAdmin = profile.isAdmin
    nextSession.storageQuotaBytes = profile.storageQuotaBytes
    nextSession.storageUsedBytes = profile.storageUsedBytes
    nextSession.storageFreeBytes = profile.storageFreeBytes
    nextSession.uploadBandwidthKbps = profile.uploadBandwidthKbps
    nextSession.downloadBandwidthKbps = profile.downloadBandwidthKbps
    nextSession.maxUploadFileBytes = profile.maxUploadFileBytes
    this.currentSession = nextSession
    this.sessionStore?.saveProfileSnapshot(this.currentSession)
    this.authAllowRegistrationHint = profile.limits.allowRegistration
    this.filesMaxUploadBytes = profile.maxUploadFileBytes
  }

  private applyTokens(tokens: TokenBundle): void {
    const nextSession = copyStoredSession(this.currentSession)
    nextSession.accessToken = tokens.accessToken
    nextSession.refreshToken = tokens.refreshToken
    this.currentSession = nextSession
    this.sessionStore?.updateTokens(tokens)
  }

  private async testServerConnection(): Promise<void> {
    if (this.authBaseUrl.trim().length === 0) {
      this.authMessage = '请先填写服务地址'
      return
    }
    this.authLoading = true
    try {
      await this.requiredApi().testConnection(this.authBaseUrl.trim())
      this.showToast('连接测试成功')
    } catch (error) {
      this.authMessage = this.extractErrorMessage(error)
    } finally {
      this.authLoading = false
    }
  }

  private async uploadManualText(): Promise<void> {
    const content = this.homeManualText.trim()
    if (content.length === 0) {
      this.showToast('请输入要上传的文本')
      return
    }
    this.homeActionLoading = true
    try {
      await this.requiredApi().uploadText(content)
      this.homeManualText = ''
      await this.loadHistory(true)
      this.showToast('文本已上传')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.showToast(this.extractErrorMessage(error))
    } finally {
      this.homeActionLoading = false
    }
  }

  private async uploadSystemClipboard(): Promise<void> {
    const text = await this.readClipboardText()
    if (text.trim().length === 0) {
      this.showToast('系统剪贴板当前没有可上传的文本')
      return
    }
    this.homeManualText = text
    await this.uploadManualText()
  }

  private async uploadHistoryDraft(): Promise<void> {
    const content = this.historyDraftText.trim()
    if (content.length === 0) {
      this.showToast('请输入要上传的历史文本')
      return
    }
    try {
      await this.requiredApi().uploadText(content)
      this.historyDraftText = ''
      await this.loadHistory(true)
      this.showToast('历史文本已上传')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.historyError = this.extractErrorMessage(error)
    }
  }

  private async pullRemoteClipboard(): Promise<void> {
    this.homeActionLoading = true
    try {
      const result = await this.requiredApi().pullSync(this.historyAckSeq, PULL_LIMIT)
      if (result.items.length === 0) {
        this.showToast('没有新的远端文本')
        return
      }

      const latestRemote = this.pickLatestRemoteItem(result.items)
      if (latestRemote !== null) {
        await this.copyText(latestRemote.textContent, '已写回系统剪贴板')
      }

      const ackSeq = result.nextSinceSeq > 0 ? result.nextSinceSeq : result.items[result.items.length - 1].seq
      await this.requiredApi().ackSync(ackSeq)
      this.historyAckSeq = ackSeq
      this.sessionStore?.saveLastAckSeq(ackSeq)
      await this.loadHistory(true)
      this.showToast(`已补拉 ${result.items.length} 条更新`)
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.showToast(this.extractErrorMessage(error))
    } finally {
      this.homeActionLoading = false
    }
  }

  private pickLatestRemoteItem(items: ClipboardItem[]): ClipboardItem | null {
    for (let index = items.length - 1; index >= 0; index -= 1) {
      if (!items[index].isCurrentDeviceOrigin) {
        return items[index]
      }
    }
    return items.length > 0 ? items[items.length - 1] : null
  }

  private async loadHistory(refresh: boolean): Promise<void> {
    if (refresh) {
      this.historyLoading = true
      this.historyError = ''
    } else {
      if (!this.historyHasMore) {
        return
      }
      this.historyLoadingMore = true
    }

    try {
      const beforeSeq = refresh || this.historyBeforeSeq <= 0 ? null : this.historyBeforeSeq
      const result = await this.requiredApi().listClipboardItems(PAGE_SIZE, beforeSeq)
      this.historyItems = refresh ? result.items : this.historyItems.concat(result.items)
      this.historyHasMore = result.hasMore
      this.historyLatestSeq = result.latestSeq
      this.historyAckSeq = Math.max(this.historyAckSeq, result.currentDeviceAckSeq)
      this.sessionStore?.saveLastAckSeq(this.historyAckSeq)
      if (result.items.length > 0) {
        this.historyBeforeSeq = result.items[result.items.length - 1].seq
      } else if (refresh) {
        this.historyBeforeSeq = 0
      }
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.historyError = this.extractErrorMessage(error)
    } finally {
      this.historyLoading = false
      this.historyLoadingMore = false
    }
  }

  private async deleteHistoryItem(itemId: string): Promise<void> {
    try {
      await this.requiredApi().deleteClipboardItem(itemId)
      await this.loadHistory(true)
      this.showToast('历史记录已删除')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.historyError = this.extractErrorMessage(error)
    }
  }

  private async loadHistorySettings(): Promise<void> {
    this.historySettingsLoading = true
    try {
      const settings = await this.requiredApi().getHistorySettings()
      this.historySettings = settings
      this.historyRetentionDaysInput = `${settings.retentionDays}`
      this.historyLimitInput = `${settings.historyLimit}`
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.historyError = this.extractErrorMessage(error)
    } finally {
      this.historySettingsLoading = false
    }
  }

  private async saveHistorySettings(): Promise<void> {
    this.historySettingsLoading = true
    try {
      const result = await this.requiredApi().updateHistorySettings(
        positiveOrZero(this.historyRetentionDaysInput),
        positiveOrFallback(this.historyLimitInput, 1000),
      )
      this.historySettings = result.settings
      this.historyRetentionDaysInput = `${result.settings.retentionDays}`
      this.historyLimitInput = `${result.settings.historyLimit}`
      this.historyAckSeq = Math.max(this.historyAckSeq, result.currentDeviceAckSeq)
      this.showToast('历史设置已保存')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.historyError = this.extractErrorMessage(error)
    } finally {
      this.historySettingsLoading = false
    }
  }

  private async cleanupHistory(): Promise<void> {
    this.historySettingsLoading = true
    try {
      const result = await this.requiredApi().cleanupClipboardHistory(positiveOrZero(this.historyRetentionDaysInput))
      this.historySettings = result.settings
      this.historyAckSeq = Math.max(this.historyAckSeq, result.currentDeviceAckSeq)
      await this.loadHistory(true)
      this.showToast(`清理完成，共删除 ${result.deletedCount} 条记录`)
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.historyError = this.extractErrorMessage(error)
    } finally {
      this.historySettingsLoading = false
    }
  }

  private async clearHistory(): Promise<void> {
    this.historySettingsLoading = true
    try {
      const result = await this.requiredApi().clearClipboardHistory()
      this.historyItems = []
      this.historyHasMore = false
      this.historyBeforeSeq = 0
      this.historyLatestSeq = result.latestSeq
      this.historyAckSeq = Math.max(this.historyAckSeq, result.currentDeviceAckSeq)
      this.showToast(`已清空 ${result.deletedCount} 条历史记录`)
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.historyError = this.extractErrorMessage(error)
    } finally {
      this.historySettingsLoading = false
    }
  }

  private async loadDevices(): Promise<void> {
    this.devicesLoading = true
    this.devicesError = ''
    try {
      const devices = await this.requiredApi().listDevices()
      this.devices = devices
      const drafts: Record<string, string> = {}
      for (let index = 0; index < devices.length; index += 1) {
        drafts[devices[index].id] = devices[index].deviceName
      }
      this.deviceRenameDrafts = drafts
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.devicesError = this.extractErrorMessage(error)
    } finally {
      this.devicesLoading = false
    }
  }

  private async renameDevice(deviceId: string): Promise<void> {
    const name = this.deviceRenameDrafts[deviceId]?.trim() ?? ''
    if (name.length === 0) {
      this.showToast('设备名称不能为空')
      return
    }
    try {
      const updated = await this.requiredApi().updateDeviceName(deviceId, name)
      if (updated.id === this.currentSession.currentDeviceId) {
        const nextSession = copyStoredSession(this.currentSession)
        nextSession.deviceName = updated.deviceName
        this.currentSession = nextSession
        this.sessionStore?.saveDeviceName(updated.deviceName)
        this.sessionStore?.saveProfileSnapshot(this.currentSession)
      }
      await this.loadDevices()
      this.showToast('设备名已更新')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.devicesError = this.extractErrorMessage(error)
    }
  }

  private async forceOfflineDevice(deviceId: string): Promise<void> {
    try {
      const result = await this.requiredApi().forceOfflineDevice(deviceId)
      if (result.currentDeviceForcedOffline) {
        await this.logout(true, '当前设备已被强制下线')
        return
      }
      await this.loadDevices()
      this.showToast('设备已下线')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.devicesError = this.extractErrorMessage(error)
    }
  }

  private async loadFiles(page: number): Promise<void> {
    this.filesLoading = true
    this.filesError = ''
    try {
      const result = await this.requiredApi().listFiles(page, PAGE_SIZE)
      this.files = result.files
      this.filesPage = result.page
      this.filesTotalPages = result.totalPages
      this.filesTotalFiles = result.totalFiles
      this.filesTotalBytes = result.totalBytes
      this.filesMaxUploadBytes = result.maxUploadBytes
      const drafts: Record<string, string> = {}
      for (let index = 0; index < result.files.length; index += 1) {
        drafts[result.files[index].id] = result.files[index].originalName
      }
      this.fileRenameDrafts = drafts
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.filesError = this.extractErrorMessage(error)
    } finally {
      this.filesLoading = false
    }
  }

  private async uploadFile(): Promise<void> {
    try {
      const uploaded = await this.fileTransfer?.uploadFile()
      if (uploaded === null || uploaded === undefined) {
        return
      }
      await this.loadFiles(1)
      this.showToast(`文件已上传：${uploaded.originalName}`)
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.filesError = this.extractErrorMessage(error)
    }
  }

  private async downloadFile(file: FileRecord): Promise<void> {
    try {
      await this.fileTransfer?.downloadFile(file)
      this.showToast(`文件已保存：${file.originalName}`)
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.filesError = this.extractErrorMessage(error)
    }
  }

  private async renameFile(fileId: string): Promise<void> {
    const name = this.fileRenameDrafts[fileId]?.trim() ?? ''
    if (name.length === 0) {
      this.showToast('文件名不能为空')
      return
    }
    try {
      await this.requiredApi().renameFile(fileId, name)
      await this.loadFiles(this.filesPage)
      this.showToast('文件名已更新')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.filesError = this.extractErrorMessage(error)
    }
  }

  private async deleteFile(fileId: string): Promise<void> {
    try {
      await this.requiredApi().deleteFile(fileId)
      await this.loadFiles(this.filesPage)
      this.showToast('文件已删除')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.filesError = this.extractErrorMessage(error)
    }
  }

  private async loadShares(page: number): Promise<void> {
    this.sharesLoading = true
    this.sharesError = ''
    try {
      const result = await this.requiredApi().listShares(page, PAGE_SIZE, this.sharesStatus)
      this.shares = result.shares
      this.sharesPage = result.page
      this.sharesTotalPages = result.totalPages
      this.sharesTotalCount = result.totalShares
      this.sharesMaxUploadBytes = result.maxUploadBytes
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.sharesError = this.extractErrorMessage(error)
    } finally {
      this.sharesLoading = false
    }
  }

  private async createShare(): Promise<void> {
    this.sharesCreating = true
    try {
      const policy = buildSharePolicyPayload(this.shareRules, this.shareStrategy, true)
      let share: ShareRecord | null = null
      if (this.shareComposeMode === ShareComposeMode.Text) {
        if (this.shareTextInput.trim().length === 0) {
          this.showToast('请输入要分享的文本')
          return
        }
        share = await this.requiredApi().createTextShare(this.shareTextInput.trim(), policy)
        this.shareTextInput = ''
      } else {
        share = await this.fileTransfer?.createFileShare(policy) ?? null
      }

      if (share !== null) {
        this.shareLastLink = this.resolveShareLink(share)
        await this.loadShares(1)
        this.showToast('分享已创建')
      }
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.sharesError = this.extractErrorMessage(error)
    } finally {
      this.sharesCreating = false
    }
  }

  private async revokeShare(shareId: string): Promise<void> {
    try {
      await this.requiredApi().revokeShare(shareId)
      await this.loadShares(this.sharesPage)
      this.showToast('分享已撤销')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.sharesError = this.extractErrorMessage(error)
    }
  }

  private saveShareRules(): void {
    this.shareRulesStore?.saveRules(this.shareRules)
    this.showToast('本地分享规则已保存')
  }

  private async loadRequests(): Promise<void> {
    this.requestsLoading = true
    this.requestsError = ''
    try {
      this.quotaRequests = await this.requiredApi().listQuotaRequests(this.requestStatus)
      this.bandwidthRequests = await this.requiredApi().listBandwidthRequests(this.requestStatus)
      this.adminRequests = await this.requiredApi().listAdminRequests(this.requestStatus)
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.requestsError = this.extractErrorMessage(error)
    } finally {
      this.requestsLoading = false
    }
  }

  private async submitQuotaRequest(): Promise<void> {
    try {
      await this.requiredApi().createQuotaRequest(positiveOrFallback(this.quotaRequestMbInput, 0), this.quotaRequestReason.trim())
      this.quotaRequestMbInput = ''
      this.quotaRequestReason = ''
      await this.loadRequests()
      this.showToast('配额申请已提交')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.requestsError = this.extractErrorMessage(error)
    }
  }

  private async submitBandwidthRequest(): Promise<void> {
    try {
      await this.requiredApi().createBandwidthRequest(
        bandwidthMbToKbps(this.bandwidthUploadInput, 0),
        bandwidthMbToKbps(this.bandwidthDownloadInput, 0),
        this.bandwidthReason.trim(),
      )
      this.bandwidthUploadInput = ''
      this.bandwidthDownloadInput = ''
      this.bandwidthReason = ''
      await this.loadRequests()
      this.showToast('带宽申请已提交')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.requestsError = this.extractErrorMessage(error)
    }
  }

  private async submitAdminRequest(): Promise<void> {
    try {
      await this.requiredApi().createAdminRequest(this.adminRequestReason.trim())
      this.adminRequestReason = ''
      await this.loadRequests()
      this.showToast('管理员申请已提交')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.requestsError = this.extractErrorMessage(error)
    }
  }

  private async loadAdminSettings(): Promise<void> {
    this.adminSettingsLoading = true
    this.adminSettingsError = ''
    try {
      const result = await this.requiredApi().getAdminSettings()
      this.adminSettings = result.settings
      this.adminCurrentUserCount = result.currentUserCount
      this.adminMaxUserCountInput = `${result.settings.maxUserCount}`
      this.adminDefaultQuotaMbInput = `${bytesToMb(result.settings.defaultStorageQuotaBytes)}`
      this.adminDefaultUploadInput = bandwidthKbpsToMbInput(result.settings.defaultUploadBandwidthKbps)
      this.adminDefaultDownloadInput = bandwidthKbpsToMbInput(result.settings.defaultDownloadBandwidthKbps)
      this.adminMaxUserUploadInput = bandwidthKbpsToMbInput(result.settings.maxUserUploadBandwidthKbps)
      this.adminMaxUserDownloadInput = bandwidthKbpsToMbInput(result.settings.maxUserDownloadBandwidthKbps)
      this.adminMaxUploadFileMbInput = `${bytesToMb(result.settings.maxUploadFileBytes)}`
      this.adminAllowRegistration = result.settings.allowRegistration
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminSettingsError = this.extractErrorMessage(error)
    } finally {
      this.adminSettingsLoading = false
    }
  }

  private async saveAdminSettings(): Promise<void> {
    this.adminSettingsLoading = true
    try {
      const result = await this.requiredApi().updateAdminSettings({
        maxUserCount: positiveOrFallback(this.adminMaxUserCountInput, 0),
        defaultStorageQuotaMb: positiveOrFallback(this.adminDefaultQuotaMbInput, 0),
        defaultUploadBandwidthKbps: bandwidthMbToKbps(this.adminDefaultUploadInput, 0),
        defaultDownloadBandwidthKbps: bandwidthMbToKbps(this.adminDefaultDownloadInput, 0),
        maxUserUploadBandwidthKbps: bandwidthMbToKbps(this.adminMaxUserUploadInput, 0),
        maxUserDownloadBandwidthKbps: bandwidthMbToKbps(this.adminMaxUserDownloadInput, 0),
        maxUploadFileMb: positiveOrFallback(this.adminMaxUploadFileMbInput, 0),
        allowRegistration: this.adminAllowRegistration,
      })
      this.adminSettings = result.settings
      this.adminCurrentUserCount = result.currentUserCount
      const nextSession = copyStoredSession(this.currentSession)
      nextSession.maxUploadFileBytes = result.settings.maxUploadFileBytes
      this.currentSession = nextSession
      this.sessionStore?.saveProfileSnapshot(this.currentSession)
      this.showToast('全局设置已保存')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminSettingsError = this.extractErrorMessage(error)
    } finally {
      this.adminSettingsLoading = false
    }
  }

  private async loadAdminUsers(): Promise<void> {
    this.adminUsersLoading = true
    this.adminUsersError = ''
    try {
      const users = await this.requiredApi().listAdminUsers()
      this.adminUsers = users
      const drafts: Record<string, AdminUserDraft> = {}
      for (let index = 0; index < users.length; index += 1) {
        const user = users[index]
        drafts[user.id] = {
          storageQuotaMb: `${bytesToMb(user.storageQuotaBytes)}`,
          uploadBandwidthKbps: bandwidthKbpsToMbInput(user.uploadBandwidthKbps),
          downloadBandwidthKbps: bandwidthKbpsToMbInput(user.downloadBandwidthKbps),
          isAdmin: user.isAdmin,
        }
      }
      this.adminUserDrafts = drafts
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminUsersError = this.extractErrorMessage(error)
    } finally {
      this.adminUsersLoading = false
    }
  }

  private async saveAdminUser(userId: string): Promise<void> {
    try {
      const draft = this.adminUserDrafts[userId]
      await this.requiredApi().updateAdminUser(userId, {
        storageQuotaMb: positiveOrFallback(draft.storageQuotaMb, 0),
        uploadBandwidthKbps: bandwidthMbToKbps(draft.uploadBandwidthKbps, 0),
        downloadBandwidthKbps: bandwidthMbToKbps(draft.downloadBandwidthKbps, 0),
        isAdmin: draft.isAdmin,
      })
      await this.loadAdminUsers()
      this.showToast('用户配置已保存')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminUsersError = this.extractErrorMessage(error)
    }
  }

  private async deleteAdminUser(userId: string): Promise<void> {
    try {
      await this.requiredApi().deleteAdminUser(userId)
      await this.loadAdminUsers()
      this.showToast('用户已删除')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminUsersError = this.extractErrorMessage(error)
    }
  }

  private async loadAdminReviews(): Promise<void> {
    this.adminReviewsLoading = true
    this.adminReviewsError = ''
    try {
      this.pendingQuotaReviews = await this.requiredApi().listPendingQuotaRequests()
      this.pendingBandwidthReviews = await this.requiredApi().listPendingBandwidthRequests()
      this.pendingAdminReviews = await this.requiredApi().listPendingAdminRequests()

      const quotaDrafts: Record<string, QuotaReviewDraft> = {}
      for (let index = 0; index < this.pendingQuotaReviews.length; index += 1) {
        const item = this.pendingQuotaReviews[index]
        quotaDrafts[item.id] = {
          approvedQuotaMb: `${bytesToMb(item.requestedQuotaBytes)}`,
          reviewNote: '',
        }
      }
      this.quotaReviewDrafts = quotaDrafts

      const bandwidthDrafts: Record<string, BandwidthReviewDraft> = {}
      for (let index = 0; index < this.pendingBandwidthReviews.length; index += 1) {
        const item = this.pendingBandwidthReviews[index]
        bandwidthDrafts[item.id] = {
          approvedUploadKbps: bandwidthKbpsToMbInput(item.requestedUploadKbps),
          approvedDownloadKbps: bandwidthKbpsToMbInput(item.requestedDownloadKbps),
          reviewNote: '',
        }
      }
      this.bandwidthReviewDrafts = bandwidthDrafts

      const reviewNotes: Record<string, string> = {}
      for (let index = 0; index < this.pendingAdminReviews.length; index += 1) {
        reviewNotes[this.pendingAdminReviews[index].id] = ''
      }
      this.adminReviewNotes = reviewNotes
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminReviewsError = this.extractErrorMessage(error)
    } finally {
      this.adminReviewsLoading = false
    }
  }

  private async approveQuotaRequest(requestId: string): Promise<void> {
    try {
      const draft = this.quotaReviewDrafts[requestId]
      await this.requiredApi().approveQuotaRequest(
        requestId,
        positiveOrFallback(draft.approvedQuotaMb, 0),
        draft.reviewNote.trim(),
      )
      await this.loadAdminReviews()
      this.showToast('配额申请已批准')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminReviewsError = this.extractErrorMessage(error)
    }
  }

  private async rejectQuotaRequest(requestId: string): Promise<void> {
    try {
      const draft = this.quotaReviewDrafts[requestId]
      await this.requiredApi().rejectQuotaRequest(requestId, draft.reviewNote.trim())
      await this.loadAdminReviews()
      this.showToast('配额申请已拒绝')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminReviewsError = this.extractErrorMessage(error)
    }
  }

  private async approveBandwidthRequest(requestId: string): Promise<void> {
    try {
      const draft = this.bandwidthReviewDrafts[requestId]
      await this.requiredApi().approveBandwidthRequest(
        requestId,
        bandwidthMbToKbps(draft.approvedUploadKbps, 0),
        bandwidthMbToKbps(draft.approvedDownloadKbps, 0),
        draft.reviewNote.trim(),
      )
      await this.loadAdminReviews()
      this.showToast('带宽申请已批准')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminReviewsError = this.extractErrorMessage(error)
    }
  }

  private async rejectBandwidthRequest(requestId: string): Promise<void> {
    try {
      const draft = this.bandwidthReviewDrafts[requestId]
      await this.requiredApi().rejectBandwidthRequest(requestId, draft.reviewNote.trim())
      await this.loadAdminReviews()
      this.showToast('带宽申请已拒绝')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminReviewsError = this.extractErrorMessage(error)
    }
  }

  private async approveAdminRequest(requestId: string): Promise<void> {
    try {
      await this.requiredApi().approveAdminRequest(requestId, this.adminReviewNote(requestId).trim())
      await this.loadAdminReviews()
      await this.refreshCurrentProfile()
      this.showToast('管理员申请已批准')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminReviewsError = this.extractErrorMessage(error)
    }
  }

  private async rejectAdminRequest(requestId: string): Promise<void> {
    try {
      await this.requiredApi().rejectAdminRequest(requestId, this.adminReviewNote(requestId).trim())
      await this.loadAdminReviews()
      this.showToast('管理员申请已拒绝')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.adminReviewsError = this.extractErrorMessage(error)
    }
  }

  private async refreshCurrentProfile(): Promise<void> {
    try {
      const profile = await this.requiredApi().getCurrentAccount()
      this.applyProfile(profile)
    } catch (_) {
      // 刷新快照只是补充动作，不额外打断当前操作。
    }
  }

  private async saveServiceAddress(): Promise<void> {
    const nextBaseUrl = this.serviceAddressDraft.trim()
    if (nextBaseUrl.length === 0) {
      this.showToast('服务地址不能为空')
      return
    }
    this.sessionStore?.saveBaseUrl(nextBaseUrl)
    this.authBaseUrl = nextBaseUrl
    await this.logout(true, '服务地址已更新，请重新登录', false)
  }

  private async changePassword(): Promise<void> {
    const currentPassword = this.securityCurrentPassword.trim()
    const newPassword = this.securityNewPassword.trim()
    const confirmPassword = this.securityConfirmPassword.trim()
    if (currentPassword.length === 0 || newPassword.length === 0) {
      this.showToast('请完整填写密码')
      return
    }
    if (newPassword !== confirmPassword) {
      this.showToast('两次输入的新密码不一致')
      return
    }

    this.securityLoading = true
    try {
      await this.requiredApi().changePassword(currentPassword, newPassword)
      this.securityCurrentPassword = ''
      this.securityNewPassword = ''
      this.securityConfirmPassword = ''
      this.showToast('密码已更新')
    } catch (error) {
      if (await this.handleProtectedError(error)) {
        return
      }
      this.showToast(this.extractErrorMessage(error))
    } finally {
      this.securityLoading = false
    }
  }

  private async logout(clearOnly: boolean, message: string = '已退出登录', callServer: boolean = true): Promise<void> {
    if (callServer) {
      await this.requiredApi().logout()
    }
    this.sessionStore?.clearAuth()
    const clearedSession = emptySession()
    clearedSession.baseUrl = this.sessionStore?.readSession().baseUrl ?? this.authBaseUrl
    clearedSession.deviceName = this.authDeviceName
    this.currentSession = clearedSession
    this.loggedIn = false
    this.authMessage = message
    this.detailPage = DetailPage.None
    this.setActiveTab(MainTab.Home)
    this.resetProtectedData(true)
    this.refreshClipboardObserver()
    if (!clearOnly) {
      this.showToast(message)
    }
  }

  private resetProtectedData(resetServiceDraft: boolean): void {
    this.historyItems = []
    this.historyHasMore = false
    this.historyBeforeSeq = 0
    this.historyLatestSeq = 0
    this.historyError = ''
    this.historyDraftText = ''
    this.devices = []
    this.devicesError = ''
    this.files = []
    this.filesError = ''
    this.filesPage = 1
    this.filesTotalPages = 0
    this.filesTotalFiles = 0
    this.filesTotalBytes = 0
    this.fileRenameDrafts = {}
    this.shares = []
    this.sharesError = ''
    this.sharesPage = 1
    this.sharesTotalPages = 0
    this.sharesTotalCount = 0
    this.shareTextInput = ''
    this.shareLastLink = ''
    this.requestsError = ''
    this.quotaRequests = []
    this.bandwidthRequests = []
    this.adminRequests = []
    this.adminSettings = emptyAdminSettings()
    this.adminUsers = []
    this.pendingQuotaReviews = []
    this.pendingBandwidthReviews = []
    this.pendingAdminReviews = []
    this.devicePendingOfflineId = ''
    this.filePendingDeleteId = ''
    this.sharePendingRevokeId = ''
    this.adminUserPendingDeleteId = ''
    this.securityCurrentPassword = ''
    this.securityNewPassword = ''
    this.securityConfirmPassword = ''
    if (resetServiceDraft) {
      this.serviceAddressDraft = this.authBaseUrl
    }
  }

  private async handleProtectedError(error: Object, message: string = '登录已失效，请重新登录'): Promise<boolean> {
    if (error instanceof ApiError && error.httpCode === 401) {
      await this.logout(true, message, false)
      return true
    }
    return false
  }

  private extractErrorMessage(error: Object): string {
    if (error instanceof ApiError) {
      return error.message
    }
    if (error instanceof Error) {
      return error.message
    }
    return '操作失败'
  }

  private showToast(message: string): void {
    if (message.trim().length === 0) {
      return
    }
    promptAction.showToast({ message })
  }

  private async toggleHomeSyncEnabled(): Promise<void> {
    if (!this.syncEnabled) {
      // 中文注释：同步一旦开启，前台自动上传和退后台补传都会读系统剪贴板，
      // 所以这里先把权限申请补齐，避免用户开启后才发现后台链路根本读不到内容。
      const granted = await this.ensurePasteboardReadPermission()
      if (!granted) {
        return
      }
    }

    const nextValue = !this.syncEnabled

    // 中文注释：首页大图标除了切换视觉状态，还会控制自动上传和退后台补传能力是否可用。
    this.syncEnabled = nextValue
    this.sessionStore?.saveSyncEnabled(nextValue)
    this.refreshClipboardObserver()
  }

  private async ensurePasteboardReadPermission(): Promise<boolean> {
    // 读取系统剪贴板前先主动申请权限，避免用户点了“上传剪贴板”却没有任何结果。
    const context = getContext(this) as common.UIAbilityContext
    const manager = abilityAccessCtrl.createAtManager()
    const permissions: Permissions[] = [READ_PASTEBOARD_PERMISSION as Permissions]
    const result = await manager.requestPermissionsFromUser(
      context,
      permissions,
    )
    if (result.authResults.length > 0 && result.authResults[0] === 0) {
      return true
    }
    this.showToast('未授予读取剪贴板权限')
    return false
  }

  private async readClipboardText(): Promise<string> {
    if (!await this.ensurePasteboardReadPermission()) {
      return ''
    }
    const systemPasteboard = pasteboard.getSystemPasteboard()
    if (!systemPasteboard.hasDataSync()) {
      return ''
    }
    const data = await systemPasteboard.getData()
    return data.getPrimaryText()
  }

  private async copyText(text: string, message: string): Promise<void> {
    const systemPasteboard = pasteboard.getSystemPasteboard()
    const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text)
    // 中文注释：应用自己往系统剪贴板写内容时，不要再触发一次自动上传，
    // 否则“补拉写回”或“历史页复制”都会被当成新的本地复制。
    this.ignoreNextClipboardUpdate = true
    await systemPasteboard.setData(data)
    this.showToast(message)
  }

  private openDetailPage(page: DetailPage): void {
    this.detailPage = page
    if (page === DetailPage.Files && this.files.length === 0) {
      this.runAsync(() => this.loadFiles(1))
    }
    if (page === DetailPage.Shares && this.shares.length === 0) {
      this.runAsync(() => this.loadShares(1))
    }
    if (page === DetailPage.HistorySettings) {
      this.runAsync(() => this.loadHistorySettings())
    }
    if (page === DetailPage.Devices && this.devices.length === 0) {
      this.runAsync(() => this.loadDevices())
    }
    if (page === DetailPage.Requests) {
      this.runAsync(() => this.loadRequests())
    }
    if (page === DetailPage.AdminSettings) {
      this.runAsync(() => this.loadAdminSettings())
    }
    if (page === DetailPage.AdminUsers) {
      this.runAsync(() => this.loadAdminUsers())
    }
    if (page === DetailPage.AdminReviews) {
      this.runAsync(() => this.loadAdminReviews())
    }
  }

  private selectTab(tab: MainTab): void {
    this.setActiveTab(tab)
    this.detailPage = DetailPage.None
    if (this.activeTab === MainTab.History && this.historyItems.length === 0) {
      this.runAsync(() => this.loadHistory(true))
    }
  }

  private setActiveTab(tab: MainTab): void {
    // 中文注释：这里同时维护“业务 tab 枚举”和“底栏选中索引”，
    // 避免 ArkUI 在自定义底栏里出现枚举状态更新了、视觉高亮却没刷新的情况。
    const resolvedTab = tab === MainTab.Ai ? MainTab.Home : tab
    this.activeTab = resolvedTab
    this.activeTabIndex = this.resolveTabIndex(resolvedTab)
  }

  private resolveTabIndex(tab: MainTab): number {
    switch (tab) {
      case MainTab.History:
        return 1
      case MainTab.Settings:
        return 2
      case MainTab.Home:
      case MainTab.Ai:
      default:
        return 0
    }
  }

  private refreshClipboardObserver(): void {
    if (this.loggedIn && this.syncEnabled) {
      this.registerClipboardObserver()
      return
    }

    this.unregisterClipboardObserver()
    this.lastAutoUploadedClipboardText = ''
    this.ignoreNextClipboardUpdate = false
  }

  private registerClipboardObserver(): void {
    if (this.clipboardObserverRegistered) {
      return
    }

    const systemPasteboard = pasteboard.getSystemPasteboard()
    this.clipboardUpdateListener = () => {
      this.runAsync(() => this.handleClipboardUpdated())
    }
    systemPasteboard.on('update', this.clipboardUpdateListener)
    this.clipboardObserverRegistered = true
  }

  private unregisterClipboardObserver(): void {
    if (!this.clipboardObserverRegistered || !this.clipboardUpdateListener) {
      return
    }

    const systemPasteboard = pasteboard.getSystemPasteboard()
    systemPasteboard.off('update', this.clipboardUpdateListener)
    this.clipboardUpdateListener = undefined
    this.clipboardObserverRegistered = false
  }

  private async handleClipboardUpdated(): Promise<void> {
    if (!this.loggedIn || !this.syncEnabled || this.clipboardAutoSyncRunning) {
      return
    }
    if (this.ignoreNextClipboardUpdate) {
      this.ignoreNextClipboardUpdate = false
      return
    }

    const text = await this.readClipboardTextSilently()
    if (text.length === 0 || text === this.lastAutoUploadedClipboardText) {
      return
    }

    this.clipboardAutoSyncRunning = true
    try {
      // 中文注释：前台自动同步只负责“复制后自动上传”，
      // 这是普通应用在鸿蒙端不依赖受限后台权限时，最接近 Android 的稳定方案。
      const result = await this.requiredClipboardSyncRuntime().uploadClipboardIfNeeded()
      if (!result.uploaded) {
        return
      }

      this.lastAutoUploadedClipboardText = text
      this.historyAckSeq = Math.max(this.historyAckSeq, result.latestAckSeq)
      if (this.activeTabIndex === 1 && this.detailPage === DetailPage.None) {
        await this.loadHistory(true)
      }
    } catch (error) {
      await this.handleProtectedError(error, '登录态已失效，请重新登录')
    } finally {
      this.clipboardAutoSyncRunning = false
    }
  }

  private async readClipboardTextSilently(): Promise<string> {
    try {
      const systemPasteboard = pasteboard.getSystemPasteboard()
      if (!systemPasteboard.hasDataSync()) {
        return ''
      }
      const data = await systemPasteboard.getData()
      const text = data.getPrimaryText()
      return typeof text === 'string' ? text.trim() : ''
    } catch (_) {
      return ''
    }
  }

  private resolvePageTitle(): string {
    switch (this.detailPage) {
      case DetailPage.Files:
        return '文件中心'
      case DetailPage.Shares:
        return '分享管理'
      case DetailPage.ShareRules:
        return '分享规则'
      case DetailPage.HistorySettings:
        return '历史设置'
      case DetailPage.Account:
        return '账号信息'
      case DetailPage.Security:
        return '安全'
      case DetailPage.Devices:
        return '设备管理'
      case DetailPage.Requests:
        return '申请记录'
      case DetailPage.AdminSettings:
        return '全局设置'
      case DetailPage.AdminUsers:
        return '用户管理'
      case DetailPage.AdminReviews:
        return '审批'
      default:
        break
    }

    switch (this.activeTabIndex) {
      case 0:
        return '首页'
      case 1:
        return '历史'
      case 2:
        return '设置'
      default:
        return 'ClipBridge'
    }
  }

  private filteredHistoryItems(): ClipboardItem[] {
    const keyword = this.historySearchQuery.trim().toLowerCase()
    if (keyword.length === 0) {
      return this.historyItems
    }
    return this.historyItems.filter((item: ClipboardItem) => {
      return item.textContent.toLowerCase().indexOf(keyword) >= 0
    })
  }

  private filteredFiles(): FileRecord[] {
    const keyword = this.filesSearchQuery.trim().toLowerCase()
    if (keyword.length === 0) {
      return this.files
    }

    return this.files.filter((file: FileRecord) => {
      return file.originalName.toLowerCase().indexOf(keyword) >= 0 ||
        file.contentType.toLowerCase().indexOf(keyword) >= 0 ||
        file.originDeviceName.toLowerCase().indexOf(keyword) >= 0
    })
  }

  private onlineDeviceCount(): number {
    return this.devices.filter((device: DeviceRecord) => device.isActive).length
  }

  private latestDeviceSeenText(): string {
    if (this.devices.length === 0) {
      return '-'
    }

    const sortedDevices = this.devices.slice().sort((left: DeviceRecord, right: DeviceRecord) => {
      return right.lastSeenAt.localeCompare(left.lastSeenAt)
    })
    return formatTime(sortedDevices[0].lastSeenAt)
  }

  private resolveShareLink(share: ShareRecord): string {
    return publicShareLink(this.currentSession.baseUrl, share.token)
  }

  private shareStatusColor(status: string): string {
    const normalized = status.trim().toLowerCase()
    if (normalized === 'active') {
      return COLOR_ACCENT
    }
    if (normalized === 'pending') {
      return COLOR_WARN
    }
    if (normalized === 'rejected' || normalized === 'revoked') {
      return COLOR_DANGER
    }
    return COLOR_SUBTEXT
  }

  private currentShareStrategySummary(): ShareStrategySummary {
    return buildShareStrategySummary(this.shareRules, this.shareStrategy)
  }

  private buildAuthModeButtons(): SegmentOption[] {
    const loginOption: SegmentOption = {
      label: '登录',
      selected: this.authMode === AuthMode.Login,
      onClick: () => {
        this.authMode = AuthMode.Login
      },
    }
    const registerOption: SegmentOption = {
      label: '注册',
      selected: this.authMode === AuthMode.Register,
      onClick: () => {
        this.authMode = AuthMode.Register
      },
    }
    return [loginOption, registerOption]
  }

  private buildShareComposeButtons(): SegmentOption[] {
    const textOption: SegmentOption = {
      label: '文本分享',
      selected: this.shareComposeMode === ShareComposeMode.Text,
      onClick: () => {
        this.shareComposeMode = ShareComposeMode.Text
      },
    }
    const fileOption: SegmentOption = {
      label: '文件分享',
      selected: this.shareComposeMode === ShareComposeMode.File,
      onClick: () => {
        this.shareComposeMode = ShareComposeMode.File
      },
    }
    return [textOption, fileOption]
  }

  private buildShareStrategyButtons(): SegmentOption[] {
    return shareStrategyKeys().map((item: ShareStrategyKey): SegmentOption => {
      return {
        label: shareStrategyLabel(item),
        selected: this.shareStrategy === item,
        onClick: () => {
          this.shareStrategy = item
        },
      }
    })
  }

  private buildShareStatusButtons(): SegmentOption[] {
    return shareStatusFilters().map((item: ShareStatusFilter): SegmentOption => {
      return {
        label: shareStatusLabel(item),
        selected: this.sharesStatus === item,
        onClick: () => {
          this.sharesStatus = item
          this.runAsync(() => this.loadShares(1))
        },
      }
    })
  }

  private buildRequestStatusButtons(): SegmentOption[] {
    return requestStatusFilters().map((item: RequestStatusFilter): SegmentOption => {
      return {
        label: requestStatusLabel(item),
        selected: this.requestStatus === item,
        onClick: () => {
          this.requestStatus = item
          this.runAsync(() => this.loadRequests())
        },
      }
    })
  }

  private buildExpirePresetButtons(): SegmentOption[] {
    return shareExpirePresets().map((item: ShareExpirePreset): SegmentOption => {
      return {
        label: shareExpireLabel(item),
        selected: this.shareRules.expire.preset === item,
        onClick: () => {
          this.updateShareRuleExpirePreset(item)
        },
      }
    })
  }

  private buildCountdownPresetButtons(): SegmentOption[] {
    return shareCountdownPresets().map((item: ShareCountdownPreset): SegmentOption => {
      return {
        label: shareCountdownLabel(item),
        selected: this.shareRules.once.countdownPreset === item,
        onClick: () => {
          this.updateShareRuleOnceCountdownPreset(item)
        },
      }
    })
  }

  private deviceRenameDraft(device: DeviceRecord): string {
    return this.deviceRenameDrafts[device.id] ?? device.deviceName
  }

  private updateDeviceRenameDraft(deviceId: string, value: string): void {
    this.deviceRenameDrafts = copyRecordWithValue(this.deviceRenameDrafts, deviceId, value)
  }

  private fileRenameDraft(file: FileRecord): string {
    return this.fileRenameDrafts[file.id] ?? file.originalName
  }

  private updateFileRenameDraft(fileId: string, value: string): void {
    this.fileRenameDrafts = copyRecordWithValue(this.fileRenameDrafts, fileId, value)
  }

  private adminUserDraft(user: AdminUserRecord): AdminUserDraft {
    return this.adminUserDrafts[user.id] ?? {
      storageQuotaMb: `${bytesToMb(user.storageQuotaBytes)}`,
      uploadBandwidthKbps: bandwidthKbpsToMbInput(user.uploadBandwidthKbps),
      downloadBandwidthKbps: bandwidthKbpsToMbInput(user.downloadBandwidthKbps),
      isAdmin: user.isAdmin,
    }
  }

  private updateAdminUserDraft(userId: string, draft: AdminUserDraft): void {
    this.adminUserDrafts = copyRecordWithValue(this.adminUserDrafts, userId, draft)
  }

  private updateAdminUserStorageQuota(user: AdminUserRecord, value: string): void {
    const draft = copyAdminUserDraft(this.adminUserDraft(user))
    draft.storageQuotaMb = value
    this.updateAdminUserDraft(user.id, draft)
  }

  private updateAdminUserUploadBandwidth(user: AdminUserRecord, value: string): void {
    const draft = copyAdminUserDraft(this.adminUserDraft(user))
    draft.uploadBandwidthKbps = value
    this.updateAdminUserDraft(user.id, draft)
  }

  private updateAdminUserDownloadBandwidth(user: AdminUserRecord, value: string): void {
    const draft = copyAdminUserDraft(this.adminUserDraft(user))
    draft.downloadBandwidthKbps = value
    this.updateAdminUserDraft(user.id, draft)
  }

  private updateAdminUserAdminFlag(user: AdminUserRecord, value: boolean): void {
    const draft = copyAdminUserDraft(this.adminUserDraft(user))
    draft.isAdmin = value
    this.updateAdminUserDraft(user.id, draft)
  }

  private quotaReviewDraft(item: QuotaRequestRecord): QuotaReviewDraft {
    return this.quotaReviewDrafts[item.id] ?? {
      approvedQuotaMb: `${bytesToMb(item.requestedQuotaBytes)}`,
      reviewNote: '',
    }
  }

  private updateQuotaReviewApprovedQuota(item: QuotaRequestRecord, value: string): void {
    const draft = copyQuotaReviewDraft(this.quotaReviewDraft(item))
    draft.approvedQuotaMb = value
    this.quotaReviewDrafts = copyRecordWithValue(this.quotaReviewDrafts, item.id, draft)
  }

  private updateQuotaReviewNote(item: QuotaRequestRecord, value: string): void {
    const draft = copyQuotaReviewDraft(this.quotaReviewDraft(item))
    draft.reviewNote = value
    this.quotaReviewDrafts = copyRecordWithValue(this.quotaReviewDrafts, item.id, draft)
  }

  private bandwidthReviewDraft(item: BandwidthRequestRecord): BandwidthReviewDraft {
    return this.bandwidthReviewDrafts[item.id] ?? {
      approvedUploadKbps: bandwidthKbpsToMbInput(item.requestedUploadKbps),
      approvedDownloadKbps: bandwidthKbpsToMbInput(item.requestedDownloadKbps),
      reviewNote: '',
    }
  }

  private updateBandwidthReviewApprovedUpload(item: BandwidthRequestRecord, value: string): void {
    const draft = copyBandwidthReviewDraft(this.bandwidthReviewDraft(item))
    draft.approvedUploadKbps = value
    this.bandwidthReviewDrafts = copyRecordWithValue(this.bandwidthReviewDrafts, item.id, draft)
  }

  private updateBandwidthReviewApprovedDownload(item: BandwidthRequestRecord, value: string): void {
    const draft = copyBandwidthReviewDraft(this.bandwidthReviewDraft(item))
    draft.approvedDownloadKbps = value
    this.bandwidthReviewDrafts = copyRecordWithValue(this.bandwidthReviewDrafts, item.id, draft)
  }

  private updateBandwidthReviewNote(item: BandwidthRequestRecord, value: string): void {
    const draft = copyBandwidthReviewDraft(this.bandwidthReviewDraft(item))
    draft.reviewNote = value
    this.bandwidthReviewDrafts = copyRecordWithValue(this.bandwidthReviewDrafts, item.id, draft)
  }

  private adminReviewNote(requestId: string): string {
    return this.adminReviewNotes[requestId] ?? ''
  }

  private updateAdminReviewNote(requestId: string, value: string): void {
    this.adminReviewNotes = copyRecordWithValue(this.adminReviewNotes, requestId, value)
  }

  private updateShareRuleNeverAllowCopy(value: boolean): void {
    const nextRules = copyShareRuleConfig(this.shareRules)
    nextRules.never.allowCopyText = value
    this.shareRules = nextRules
  }

  private updateShareRuleExpireAllowCopy(value: boolean): void {
    const nextRules = copyShareRuleConfig(this.shareRules)
    nextRules.expire.allowCopyText = value
    this.shareRules = nextRules
  }

  private updateShareRuleExpirePreset(value: ShareExpirePreset): void {
    const nextRules = copyShareRuleConfig(this.shareRules)
    nextRules.expire.preset = value
    this.shareRules = nextRules
  }

  private updateShareRuleOnceShowCountdown(value: boolean): void {
    const nextRules = copyShareRuleConfig(this.shareRules)
    nextRules.once.showCountdown = value
    this.shareRules = nextRules
  }

  private updateShareRuleOnceAllowCopy(value: boolean): void {
    const nextRules = copyShareRuleConfig(this.shareRules)
    nextRules.once.allowCopyText = value
    this.shareRules = nextRules
  }

  private updateShareRuleOnceCountdownPreset(value: ShareCountdownPreset): void {
    const nextRules = copyShareRuleConfig(this.shareRules)
    nextRules.once.countdownPreset = value
    this.shareRules = nextRules
  }

  private pendingFlags(user: AdminUserRecord): string {
    const values: string[] = []
    if (user.hasPendingQuotaRequest) {
      values.push('配额')
    }
    if (user.hasPendingBandwidthRequest) {
      values.push('带宽')
    }
    if (user.hasPendingAdminRequest) {
      values.push('管理员')
    }
    return values.length > 0 ? values.join(' / ') : '无'
  }
}

function positiveOrFallback(value: string, fallback: number): number {
  const parsed = Number(value.trim())
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.max(0, Math.floor(parsed))
}

function positiveOrZero(value: string): number {
  return positiveOrFallback(value, 0)
}

function bandwidthKbpsToMbInput(value: number): string {
  if (value <= 0) {
    return ''
  }

  return trimTrailingZeros((value / 1024).toFixed(3))
}

function bandwidthMbToKbps(value: string, fallback: number): number {
  const parsed = Number(value.trim())
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback
  }

  // 中文注释：接口字段仍然沿用旧的 kbps 命名，但服务端历史上实际按 KB/s 处理。
  // 这里统一把界面输入的 MB/s 换算回旧字段，避免额外改动接口层。
  return Math.max(1, Math.round(parsed * 1024))
}

function trimTrailingZeros(value: string): string {
  return value.replace(/\.?0+$/, '')
}

function bytesToMb(value: number): number {
  if (value <= 0) {
    return 0
  }
  return Math.round(value / 1024 / 1024)
}

function copyStoredSession(source: StoredSession): StoredSession {
  return {
    baseUrl: source.baseUrl,
    accessToken: source.accessToken,
    refreshToken: source.refreshToken,
    currentDeviceId: source.currentDeviceId,
    username: source.username,
    deviceName: source.deviceName,
    isAdmin: source.isAdmin,
    storageQuotaBytes: source.storageQuotaBytes,
    storageUsedBytes: source.storageUsedBytes,
    storageFreeBytes: source.storageFreeBytes,
    uploadBandwidthKbps: source.uploadBandwidthKbps,
    downloadBandwidthKbps: source.downloadBandwidthKbps,
    maxUploadFileBytes: source.maxUploadFileBytes,
  }
}

function copyShareRuleConfig(source: ShareRuleConfig): ShareRuleConfig {
  return {
    never: {
      allowCopyText: source.never.allowCopyText,
    },
    expire: {
      preset: source.expire.preset,
      allowCopyText: source.expire.allowCopyText,
    },
    once: {
      showCountdown: source.once.showCountdown,
      countdownPreset: source.once.countdownPreset,
      allowCopyText: source.once.allowCopyText,
    },
  }
}

function copyAdminUserDraft(source: AdminUserDraft): AdminUserDraft {
  return {
    storageQuotaMb: source.storageQuotaMb,
    uploadBandwidthKbps: source.uploadBandwidthKbps,
    downloadBandwidthKbps: source.downloadBandwidthKbps,
    isAdmin: source.isAdmin,
  }
}

function copyQuotaReviewDraft(source: QuotaReviewDraft): QuotaReviewDraft {
  return {
    approvedQuotaMb: source.approvedQuotaMb,
    reviewNote: source.reviewNote,
  }
}

function copyBandwidthReviewDraft(source: BandwidthReviewDraft): BandwidthReviewDraft {
  return {
    approvedUploadKbps: source.approvedUploadKbps,
    approvedDownloadKbps: source.approvedDownloadKbps,
    reviewNote: source.reviewNote,
  }
}

function copyRecordWithValue<T>(source: Record<string, T>, key: string, value: T): Record<string, T> {
  const result: Record<string, T> = {}
  const sourceKeys = Object.keys(source)
  for (let index = 0; index < sourceKeys.length; index += 1) {
    const sourceKey = sourceKeys[index]
    result[sourceKey] = source[sourceKey]
  }
  result[key] = value
  return result
}
