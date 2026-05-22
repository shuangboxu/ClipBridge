package config

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"strings"
)

var errEnvFileNotFound = errors.New("env file not found")

// LoadEnvFiles 会按顺序尝试读取多个 .env 文件。
// 这样做是为了兼容两种常见启动方式：
// 1. 先 cd 到 backend/ 再启动；
// 2. 在仓库根目录直接执行 backend 下的命令。
func LoadEnvFiles(paths ...string) error {
	for _, path := range paths {
		err := loadEnvFile(path)
		switch {
		case err == nil:
			return nil
		case errors.Is(err, errEnvFileNotFound):
			continue
		default:
			return err
		}
	}
	return nil
}

func loadEnvFile(path string) error {
	file, err := os.Open(path)
	if err != nil {
		if os.IsNotExist(err) {
			return errEnvFileNotFound
		}
		return fmt.Errorf("open %s failed: %w", path, err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	lineNumber := 0
	for scanner.Scan() {
		lineNumber++
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		key, value, ok := strings.Cut(line, "=")
		if !ok {
			return fmt.Errorf("%s:%d is not a valid KEY=VALUE line", path, lineNumber)
		}

		key = strings.TrimSpace(strings.TrimPrefix(key, "export "))
		value = strings.TrimSpace(value)
		value = trimMatchingQuotes(value)
		if key == "" {
			return fmt.Errorf("%s:%d has empty key", path, lineNumber)
		}

		// 如果外部环境已经显式设置过同名变量，则优先保留外部值。
		// 这样在 CI 或 IDE 里覆盖配置时会更直观。
		if _, exists := os.LookupEnv(key); exists {
			continue
		}

		if err := os.Setenv(key, value); err != nil {
			return fmt.Errorf("set env %s failed: %w", key, err)
		}
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("scan %s failed: %w", path, err)
	}
	return nil
}

func trimMatchingQuotes(value string) string {
	if len(value) < 2 {
		return value
	}
	if strings.HasPrefix(value, "\"") && strings.HasSuffix(value, "\"") {
		return value[1 : len(value)-1]
	}
	if strings.HasPrefix(value, "'") && strings.HasSuffix(value, "'") {
		return value[1 : len(value)-1]
	}
	return value
}
