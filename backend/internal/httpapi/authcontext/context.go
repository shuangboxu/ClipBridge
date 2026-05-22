package authcontext

import "context"

type contextKey string

const identityContextKey contextKey = "auth_identity"

// Identity 表示当前请求已经确认出来的调用者身份。
// 这一层先只关心用户 ID 和设备 ID，后续再补管理员标记、角色信息等字段。
type Identity struct {
	UserID   string `json:"user_id"`
	DeviceID string `json:"device_id"`
}

func WithIdentity(ctx context.Context, identity Identity) context.Context {
	return context.WithValue(ctx, identityContextKey, identity)
}

func Get(ctx context.Context) (Identity, bool) {
	value := ctx.Value(identityContextKey)
	if value == nil {
		return Identity{}, false
	}

	identity, ok := value.(Identity)
	if !ok {
		return Identity{}, false
	}
	return identity, true
}
