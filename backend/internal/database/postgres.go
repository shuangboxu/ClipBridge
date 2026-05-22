package database

import (
	"context"
	"fmt"

	"clipbridge/backend/internal/config"
	"github.com/jackc/pgx/v5/pgxpool"
)

func NewPool(ctx context.Context, cfg config.DatabaseConfig) (*pgxpool.Pool, error) {
	parsedConfig, err := pgxpool.ParseConfig(cfg.URL)
	if err != nil {
		return nil, fmt.Errorf("parse database url failed: %w", err)
	}

	// 连接池参数统一在这里设置，后续如果要调优数据库连接行为，只需要看这一个文件。
	parsedConfig.MaxConns = cfg.MaxConns
	parsedConfig.MinConns = cfg.MinConns
	parsedConfig.MaxConnLifetime = cfg.MaxConnLifetime
	parsedConfig.MaxConnIdleTime = cfg.MaxConnIdleTime
	parsedConfig.HealthCheckPeriod = cfg.HealthCheckPeriod

	if parsedConfig.ConnConfig.RuntimeParams == nil {
		parsedConfig.ConnConfig.RuntimeParams = map[string]string{}
	}
	parsedConfig.ConnConfig.RuntimeParams["application_name"] = cfg.ApplicationName

	pool, err := pgxpool.NewWithConfig(ctx, parsedConfig)
	if err != nil {
		return nil, fmt.Errorf("create database pool failed: %w", err)
	}

	// 服务刚启动时先主动 ping 一次，尽早暴露数据库地址、账号密码、端口等问题。
	pingCtx, cancel := context.WithTimeout(ctx, cfg.PingTimeout)
	defer cancel()

	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping database failed: %w", err)
	}

	return pool, nil
}
