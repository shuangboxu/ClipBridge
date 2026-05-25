package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Server   ServerConfig
	Database DatabaseConfig
	Auth     AuthConfig
	CORS     CORSConfig
}

type ServerConfig struct {
	Name              string
	Addr              string
	ReadHeaderTimeout time.Duration
	ShutdownTimeout   time.Duration
}

type DatabaseConfig struct {
	URL               string
	ApplicationName   string
	MaxConns          int32
	MinConns          int32
	MaxConnLifetime   time.Duration
	MaxConnIdleTime   time.Duration
	HealthCheckPeriod time.Duration
	PingTimeout       time.Duration
}

type AuthConfig struct {
	JWTSecret         string
	AccessTokenTTL    time.Duration
	RefreshTokenTTL   time.Duration
	AllowRegistration bool
}

type CORSConfig struct {
	AllowOrigins []string
}

func Load() (Config, error) {
	// 为了让你在本地开发时更省事，这里会自动尝试读取 .env。
	// 如果当前工作目录是 backend/，会读取 ./ .env；
	// 如果当前工作目录是仓库根目录，会读取 backend/.env。
	if err := LoadEnvFiles(".env", "backend/.env"); err != nil {
		return Config{}, fmt.Errorf("load env files failed: %w", err)
	}

	cfg := Config{
		Server: ServerConfig{
			Name:              getEnv("APP_NAME", "clipbridge-backend"),
			Addr:              getEnv("APP_ADDR", ":18080"),
			ReadHeaderTimeout: time.Duration(getEnvInt("APP_READ_HEADER_TIMEOUT_SECONDS", 10)) * time.Second,
			ShutdownTimeout:   time.Duration(getEnvInt("APP_SHUTDOWN_TIMEOUT_SECONDS", 10)) * time.Second,
		},
		Database: DatabaseConfig{
			URL:               getEnv("DATABASE_URL", "postgres://clipbridge:clipbridge@127.0.0.1:15432/clipbridge?sslmode=disable"),
			ApplicationName:   getEnv("APP_NAME", "clipbridge-backend"),
			MaxConns:          int32(getEnvInt("DB_MAX_CONNS", 20)),
			MinConns:          int32(getEnvInt("DB_MIN_CONNS", 2)),
			MaxConnLifetime:   time.Duration(getEnvInt("DB_MAX_CONN_LIFETIME_MINUTES", 120)) * time.Minute,
			MaxConnIdleTime:   time.Duration(getEnvInt("DB_MAX_CONN_IDLE_TIME_MINUTES", 30)) * time.Minute,
			HealthCheckPeriod: time.Duration(getEnvInt("DB_HEALTH_CHECK_SECONDS", 60)) * time.Second,
			PingTimeout:       time.Duration(getEnvInt("DB_PING_TIMEOUT_SECONDS", 3)) * time.Second,
		},
		Auth: AuthConfig{
			JWTSecret:         getEnv("JWT_SECRET", "please-change-this-secret"),
			AccessTokenTTL:    time.Duration(getEnvInt("ACCESS_TOKEN_TTL_MINUTES", 120)) * time.Minute,
			RefreshTokenTTL:   time.Duration(getEnvInt("REFRESH_TOKEN_TTL_HOURS", 24*30)) * time.Hour,
			AllowRegistration: getEnvBool("AUTH_ALLOW_REGISTRATION", false),
		},
		CORS: CORSConfig{
			AllowOrigins: splitCSV(getEnv("CORS_ALLOW_ORIGINS", "*")),
		},
	}

	if len(cfg.CORS.AllowOrigins) == 0 {
		cfg.CORS.AllowOrigins = []string{"*"}
	}

	if err := cfg.Validate(); err != nil {
		return Config{}, err
	}

	return cfg, nil
}

func (c Config) Validate() error {
	if strings.TrimSpace(c.Server.Name) == "" {
		return errors.New("APP_NAME cannot be empty")
	}
	if strings.TrimSpace(c.Server.Addr) == "" {
		return errors.New("APP_ADDR cannot be empty")
	}
	if c.Server.ReadHeaderTimeout <= 0 {
		return errors.New("APP_READ_HEADER_TIMEOUT_SECONDS must be greater than 0")
	}
	if c.Server.ShutdownTimeout <= 0 {
		return errors.New("APP_SHUTDOWN_TIMEOUT_SECONDS must be greater than 0")
	}
	if strings.TrimSpace(c.Database.URL) == "" {
		return errors.New("DATABASE_URL cannot be empty")
	}
	if strings.TrimSpace(c.Database.ApplicationName) == "" {
		return errors.New("database application name cannot be empty")
	}
	if c.Database.MaxConns <= 0 {
		return errors.New("DB_MAX_CONNS must be greater than 0")
	}
	if c.Database.MinConns < 0 {
		return errors.New("DB_MIN_CONNS cannot be negative")
	}
	if c.Database.MinConns > c.Database.MaxConns {
		return errors.New("DB_MIN_CONNS cannot be greater than DB_MAX_CONNS")
	}
	if c.Database.MaxConnLifetime <= 0 {
		return errors.New("DB_MAX_CONN_LIFETIME_MINUTES must be greater than 0")
	}
	if c.Database.MaxConnIdleTime <= 0 {
		return errors.New("DB_MAX_CONN_IDLE_TIME_MINUTES must be greater than 0")
	}
	if c.Database.HealthCheckPeriod <= 0 {
		return errors.New("DB_HEALTH_CHECK_SECONDS must be greater than 0")
	}
	if c.Database.PingTimeout <= 0 {
		return errors.New("DB_PING_TIMEOUT_SECONDS must be greater than 0")
	}
	if strings.TrimSpace(c.Auth.JWTSecret) == "" {
		return errors.New("JWT_SECRET cannot be empty")
	}
	if c.Auth.AccessTokenTTL <= 0 {
		return errors.New("ACCESS_TOKEN_TTL_MINUTES must be greater than 0")
	}
	if c.Auth.RefreshTokenTTL <= 0 {
		return errors.New("REFRESH_TOKEN_TTL_HOURS must be greater than 0")
	}
	return nil
}

func getEnv(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func getEnvInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}

	number, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return number
}

func getEnvBool(key string, fallback bool) bool {
	value := strings.TrimSpace(strings.ToLower(os.Getenv(key)))
	if value == "" {
		return fallback
	}

	switch value {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		return fallback
	}
}

func splitCSV(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, item := range parts {
		item = strings.TrimSpace(item)
		if item != "" {
			result = append(result, item)
		}
	}
	return result
}
