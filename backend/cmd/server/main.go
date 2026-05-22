package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os/signal"
	"syscall"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/config"
	apirouter "clipbridge/backend/internal/httpapi/router"
)

func main() {
	// 第一步先把配置读出来。
	// 如果这里失败，说明服务连“怎么启动”都还没准备好，应该立刻退出。
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("load config failed: %v", err)
	}

	// 用带信号的上下文统一管理整个服务生命周期。
	// 这样收到 Ctrl+C 或系统停止信号时，可以进入统一的优雅退出流程。
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	application, err := app.New(ctx, cfg)
	if err != nil {
		log.Fatalf("build application failed: %v", err)
	}
	defer application.Close()

	router := apirouter.New(application)

	httpServer := &http.Server{
		Addr:              cfg.Server.Addr,
		Handler:           router,
		ReadHeaderTimeout: cfg.Server.ReadHeaderTimeout,
	}

	go func() {
		log.Printf("%s listening on %s", cfg.Server.Name, cfg.Server.Addr)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("http server failed: %v", err)
		}
	}()

	// 阻塞在这里，直到外部要求服务停止。
	<-ctx.Done()
	log.Printf("shutdown signal received, stopping %s", cfg.Server.Name)

	shutdownCtx, cancel := context.WithTimeout(context.Background(), cfg.Server.ShutdownTimeout)
	defer cancel()

	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		log.Printf("graceful shutdown failed: %v", err)
	}
}
