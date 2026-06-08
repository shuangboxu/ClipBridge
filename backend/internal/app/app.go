package app

import (
	"context"
	"errors"
	"fmt"

	"clipbridge/backend/internal/admin"
	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/clipboard"
	"clipbridge/backend/internal/config"
	"clipbridge/backend/internal/database"
	"clipbridge/backend/internal/files"
	"clipbridge/backend/internal/filestore"
	"clipbridge/backend/internal/realtime"
	"clipbridge/backend/internal/shares"
	"github.com/jackc/pgx/v5/pgxpool"
)

// App 用来集中保存当前阶段真正已经准备好的基础依赖。
// 这样做的好处是：
// 1. main.go 不需要知道太多细节；
// 2. 后续新增 store、service、ws hub 时，也有统一的装配入口；
// 3. 初学者阅读时，更容易看清“服务启动到底依赖哪些东西”。
type App struct {
	Config           config.Config
	DB               *pgxpool.Pool
	TokenManager     *auth.Manager
	AuthService      *auth.Service
	AdminService     *admin.Service
	ClipboardService *clipboard.Service
	FileService      *files.Service
	ShareService     *shares.Service
	RealtimeHub      *realtime.Hub
}

func New(ctx context.Context, cfg config.Config) (*App, error) {
	if ctx == nil {
		ctx = context.Background()
	}

	pool, err := database.NewPool(ctx, cfg.Database)
	if err != nil {
		return nil, fmt.Errorf("connect database failed: %w", err)
	}

	// 第一步就执行迁移，确保“代码期望的表结构”和“数据库里真实的表结构”尽量一致。
	// 这能减少后面出现“代码写好了，但表不存在”的调试时间。
	if err := database.RunMigrations(ctx, pool); err != nil {
		pool.Close()
		return nil, fmt.Errorf("run migrations failed: %w", err)
	}

	tokenManager := auth.NewManager(cfg.Auth.JWTSecret, cfg.Auth.AccessTokenTTL)
	fileStore := filestore.NewLocalStore(cfg.Files.StorageDir)
	if err := fileStore.EnsureBaseDir(); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ensure file storage dir failed: %w", err)
	}

	adminRepo := admin.NewPostgresRepository(pool)
	bandwidthManager := admin.NewBandwidthManager()
	adminService := admin.NewService(adminRepo, bandwidthManager, fileStore)
	if err := adminService.SeedSystemSettings(ctx, admin.SeedSettings{
		DefaultStorageQuotaBytes:     admin.DefaultStorageQuotaBytes,
		DefaultUploadBandwidthKbps:   admin.DefaultUploadBandwidthKbps,
		DefaultDownloadBandwidthKbps: admin.DefaultDownloadBandwidthKbps,
		MaxUserCount:                 admin.DefaultMaxUserCount,
		MaxUserUploadBandwidthKbps:   admin.DefaultMaxUserUploadBandwidthKbps,
		MaxUserDownloadBandwidthKbps: admin.DefaultMaxUserDownloadBandwidthKbps,
		MaxUploadFileBytes:           cfg.Files.MaxUploadBytes,
		AllowRegistration:            cfg.Auth.AllowRegistration,
	}); err != nil {
		pool.Close()
		return nil, fmt.Errorf("seed system settings failed: %w", err)
	}
	authRepo := auth.NewPostgresRepository(pool)
	clipboardRepo := clipboard.NewPostgresRepository(pool)
	fileRepo := files.NewPostgresRepository(pool)
	shareRepo := shares.NewPostgresRepository(pool)

	return &App{
		Config:           cfg,
		DB:               pool,
		TokenManager:     tokenManager,
		AuthService:      auth.NewService(authRepo, tokenManager, cfg.Auth.RefreshTokenTTL),
		AdminService:     adminService,
		ClipboardService: clipboard.NewService(clipboardRepo),
		FileService:      files.NewService(fileRepo, fileStore, adminService),
		ShareService:     shares.NewService(shareRepo, fileStore, adminService),
		RealtimeHub:      realtime.NewHub(),
	}, nil
}

func (a *App) Close() error {
	if a == nil {
		return nil
	}
	if a.DB != nil {
		a.DB.Close()
	}
	return nil
}

func (a *App) PingDatabase(ctx context.Context) error {
	if a == nil || a.DB == nil {
		return errors.New("database pool is not initialized")
	}
	return a.DB.Ping(ctx)
}
