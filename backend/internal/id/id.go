package id

import (
	"crypto/rand"
	"fmt"
)

// NewUUID 生成一个标准 UUID v4 字符串。
// 当前项目只需要“稳定可读、无额外依赖”的 ID 生成方式，因此这里直接手写最小实现。
func NewUUID() (string, error) {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return "", fmt.Errorf("generate random uuid bytes failed: %w", err)
	}

	raw[6] = (raw[6] & 0x0f) | 0x40
	raw[8] = (raw[8] & 0x3f) | 0x80

	return fmt.Sprintf(
		"%08x-%04x-%04x-%04x-%012x",
		raw[0:4],
		raw[4:6],
		raw[6:8],
		raw[8:10],
		raw[10:16],
	), nil
}
