import { AbilityConstant, ConfigurationConstant, UIAbility, Want, common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { ClipboardSyncRuntime } from '../core/ClipboardSyncRuntime';

const DOMAIN = 0x0000;

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    try {
      this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET);
    } catch (err) {
      hilog.error(DOMAIN, 'testTag', 'Failed to set colorMode. Cause: %{public}s', JSON.stringify(err));
    }
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onCreate');
  }

  onDestroy(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    // Main window is created, set main page for this ability
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageCreate');

    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, 'testTag', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err));
        return;
      }
      hilog.info(DOMAIN, 'testTag', 'Succeeded in loading the content.');
    });
  }

  onWindowStageDestroy(): void {
    // Main window is destroyed, release UI related resources
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageDestroy');
  }

  onForeground(): void {
    // Ability has brought to foreground
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onForeground');
  }

  onBackground(): void {
    // Ability has back to background
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onBackground');

    // 中文注释：当前鸿蒙端还没有接入像 Android 那样的常驻剪贴板服务，
    // 所以这里先补一个“退到后台时立即尝试上传一次”的链路，至少保证用户离开应用前能带走最新内容。
    const syncRuntime = new ClipboardSyncRuntime(this.context as common.UIAbilityContext);
    syncRuntime.uploadClipboardIfNeeded()
      .then((result) => {
        hilog.info(
          DOMAIN,
          'testTag',
          'Background clipboard sync attempted=%{public}s uploaded=%{public}s reason=%{public}s',
          String(result.attempted),
          String(result.uploaded),
          result.reason,
        );
      })
      .catch((error: Object) => {
        hilog.error(DOMAIN, 'testTag', 'Background clipboard sync failed: %{public}s', JSON.stringify(error));
      });
  }
}
