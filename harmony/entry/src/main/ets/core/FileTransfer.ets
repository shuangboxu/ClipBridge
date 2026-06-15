import { common } from '@kit.AbilityKit'
import { fileIo, fileUri, picker } from '@kit.CoreFileKit'
import { ApiClient } from './ApiClient'
import { FileRecord, LocalFile, SharePolicyPayload, ShareRecord } from './Models'

export class FileTransfer {
  private readonly getContext: () => common.UIAbilityContext
  private readonly apiClient: ApiClient

  constructor(getContext: () => common.UIAbilityContext, apiClient: ApiClient) {
    this.getContext = getContext
    this.apiClient = apiClient
  }

  async pickLocalFile(fileSuffixFilters: string[] = []): Promise<LocalFile | null> {
    const pickerInstance = new picker.DocumentViewPicker(this.getContext())
    const uris = await pickerInstance.select({
      maxSelectNumber: 1,
      fileSuffixFilters,
    })
    if (uris.length === 0) {
      return null
    }
    return this.buildLocalFile(uris[0])
  }

  async uploadFile(): Promise<FileRecord | null> {
    const localFile = await this.pickLocalFile()
    if (localFile === null) {
      return null
    }
    return this.apiClient.uploadFile(localFile)
  }

  async createFileShare(policy: SharePolicyPayload): Promise<ShareRecord | null> {
    const localFile = await this.pickLocalFile()
    if (localFile === null) {
      return null
    }
    return this.apiClient.createFileShare(localFile, policy)
  }

  async downloadFile(record: FileRecord): Promise<void> {
    // 先让用户决定落盘位置，再请求文件内容，避免下载成功却没有保存位置。
    const pickerInstance = new picker.DocumentViewPicker(this.getContext())
    const uris = await pickerInstance.save({
      newFileNames: [record.originalName],
    })
    if (uris.length === 0) {
      return
    }

    const targetPath = this.resolvePath(uris[0])
    const buffer = await this.apiClient.downloadFileBytes(record.id)
    const file = fileIo.openSync(
      targetPath,
      fileIo.OpenMode.CREATE | fileIo.OpenMode.WRITE_ONLY | fileIo.OpenMode.TRUNC,
    )
    try {
      fileIo.writeSync(file.fd, buffer)
    } finally {
      fileIo.closeSync(file)
    }
  }

  private buildLocalFile(uriValue: string): LocalFile {
    const path = this.resolvePath(uriValue)
    const metadata = fileIo.statSync(path)
    let displayName = ''
    try {
      displayName = new fileUri.FileUri(uriValue).name
    } catch (_) {
      displayName = path.substring(path.lastIndexOf('/') + 1)
    }

    return {
      uri: uriValue,
      path,
      displayName,
      contentType: guessContentType(displayName),
      sizeBytes: Number(metadata.size),
    }
  }

  private resolvePath(uriValue: string): string {
    try {
      return new fileUri.FileUri(uriValue).path
    } catch (_) {
      return uriValue
    }
  }
}

function guessContentType(fileName: string): string {
  const lowered = fileName.trim().toLowerCase()
  if (lowered.endsWith('.txt')) {
    return 'text/plain'
  }
  if (lowered.endsWith('.md')) {
    return 'text/markdown'
  }
  if (lowered.endsWith('.json')) {
    return 'application/json'
  }
  if (lowered.endsWith('.pdf')) {
    return 'application/pdf'
  }
  if (lowered.endsWith('.png')) {
    return 'image/png'
  }
  if (lowered.endsWith('.jpg') || lowered.endsWith('.jpeg')) {
    return 'image/jpeg'
  }
  if (lowered.endsWith('.gif')) {
    return 'image/gif'
  }
  if (lowered.endsWith('.webp')) {
    return 'image/webp'
  }
  if (lowered.endsWith('.zip')) {
    return 'application/zip'
  }
  if (lowered.endsWith('.doc')) {
    return 'application/msword'
  }
  if (lowered.endsWith('.docx')) {
    return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  }
  if (lowered.endsWith('.xls')) {
    return 'application/vnd.ms-excel'
  }
  if (lowered.endsWith('.xlsx')) {
    return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  }
  if (lowered.endsWith('.ppt')) {
    return 'application/vnd.ms-powerpoint'
  }
  if (lowered.endsWith('.pptx')) {
    return 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
  }
  return 'application/octet-stream'
}
