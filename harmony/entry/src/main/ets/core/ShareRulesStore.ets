import { common } from '@kit.AbilityKit'
import { preferences } from '@kit.ArkData'
import {
  ShareCountdownPreset,
  ShareExpirePreset,
  ShareRuleConfig,
  buildDefaultShareRules,
  shareCountdownPresetFromValue,
  shareExpirePresetFromValue,
} from './Models'

const STORE_NAME = 'clipbridge_share_rules'
const KEY_SHARE_RULES = 'share_rules'

export class ShareRulesStore {
  private readonly store: preferences.Preferences

  constructor(context: common.UIAbilityContext) {
    this.store = preferences.getPreferencesSync(context, { name: STORE_NAME })
  }

  readRules(): ShareRuleConfig {
    const fallback = buildDefaultShareRules()
    const rawValue = String(this.store.getSync(KEY_SHARE_RULES, ''))
    if (rawValue.trim().length === 0) {
      return fallback
    }

    const values = this.parsePayload(rawValue)
    if (Object.keys(values).length === 0) {
      return fallback
    }

    return {
      never: {
        allowCopyText: this.readBoolean(values['never.allow_copy_text'], fallback.never.allowCopyText),
      },
      expire: {
        preset: shareExpirePresetFromValue(values['expire.preset'] ?? ShareExpirePreset.OneDay),
        allowCopyText: this.readBoolean(values['expire.allow_copy_text'], fallback.expire.allowCopyText),
      },
      once: {
        showCountdown: this.readBoolean(values['once.show_countdown'], fallback.once.showCountdown),
        countdownPreset: shareCountdownPresetFromValue(
          values['once.countdown_preset'] ?? ShareCountdownPreset.TenSeconds,
        ),
        allowCopyText: this.readBoolean(values['once.allow_copy_text'], fallback.once.allowCopyText),
      },
    }
  }

  saveRules(rules: ShareRuleConfig): void {
    // 这里沿用 Android 的 key=value 文本格式，便于多端排查时直接肉眼阅读。
    const payload = [
      'version=1',
      `never.allow_copy_text=${rules.never.allowCopyText}`,
      `expire.preset=${rules.expire.preset}`,
      `expire.allow_copy_text=${rules.expire.allowCopyText}`,
      `once.show_countdown=${rules.once.showCountdown}`,
      `once.countdown_preset=${rules.once.countdownPreset}`,
      `once.allow_copy_text=${rules.once.allowCopyText}`,
    ].join('\n')

    this.store.putSync(KEY_SHARE_RULES, payload)
    this.store.flush().catch(() => {})
  }

  private parsePayload(rawValue: string): Record<string, string> {
    if (rawValue.indexOf('=') < 0) {
      return {}
    }

    const result: Record<string, string> = {}
    const lines = rawValue.split('\n')
    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index].trim()
      const separatorIndex = line.indexOf('=')
      if (separatorIndex <= 0) {
        continue
      }

      const key = line.substring(0, separatorIndex).trim()
      const value = line.substring(separatorIndex + 1).trim()
      if (key.length > 0) {
        result[key] = value
      }
    }
    return result
  }

  private readBoolean(rawValue: string | undefined, fallback: boolean): boolean {
    if (rawValue === 'true') {
      return true
    }
    if (rawValue === 'false') {
      return false
    }
    return fallback
  }
}
