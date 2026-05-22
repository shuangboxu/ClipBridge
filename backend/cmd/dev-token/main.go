package main

import (
	"flag"
	"fmt"
	"log"

	"clipbridge/backend/internal/auth"
	"clipbridge/backend/internal/config"
)

func main() {
	// 这里给出固定格式的默认值，主要是为了让你第一次运行时不用纠结该填什么。
	// 等后续接上真实用户和设备表后，再把这些值换成真实 ID 即可。
	userID := flag.String("user-id", "11111111-1111-1111-1111-111111111111", "测试用户 ID")
	deviceID := flag.String("device-id", "22222222-2222-2222-2222-222222222222", "测试设备 ID")
	flag.Parse()

	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("load config failed: %v", err)
	}

	manager := auth.NewManager(cfg.Auth.JWTSecret, cfg.Auth.AccessTokenTTL)
	token, expiresAt, err := manager.GenerateAccessToken(*userID, *deviceID)
	if err != nil {
		log.Fatalf("generate token failed: %v", err)
	}

	fmt.Println("开发期测试 Token 已生成：")
	fmt.Printf("user_id: %s\n", *userID)
	fmt.Printf("device_id: %s\n", *deviceID)
	fmt.Printf("expires_at: %s\n", expiresAt.Format("2006-01-02 15:04:05 -07:00"))
	fmt.Printf("authorization_header: Bearer %s\n", token)
	fmt.Printf("token_only: %s\n", token)
}
