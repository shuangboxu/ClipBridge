package admin

import (
	"context"
	"io"
	"strings"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

const (
	maxWaitChunkBytes = 32 * 1024
	minBurstBytes     = maxWaitChunkBytes
	maxBurstBytes     = 512 * 1024
)

// BandwidthManager 负责按用户维度复用限速器。
// 上传和下载分别维护一组 limiter，避免互相抢占令牌。
type BandwidthManager struct {
	mu          sync.Mutex
	uploadMap   map[string]*rate.Limiter
	downloadMap map[string]*rate.Limiter
}

func NewBandwidthManager() *BandwidthManager {
	return &BandwidthManager{
		uploadMap:   make(map[string]*rate.Limiter),
		downloadMap: make(map[string]*rate.Limiter),
	}
}

func (m *BandwidthManager) WrapUploadReader(ctx context.Context, userID string, kbps int64, src io.Reader) io.Reader {
	if src == nil || kbps <= 0 {
		return src
	}

	limiter := m.getLimiter(m.uploadMap, normalizeBandwidthUserID(userID), kbps)
	if limiter == nil {
		return src
	}
	return &throttledReader{
		ctx:     ctx,
		src:     src,
		limiter: limiter,
	}
}

func (m *BandwidthManager) WrapDownloadWriter(ctx context.Context, userID string, kbps int64, dst io.Writer) io.Writer {
	if dst == nil || kbps <= 0 {
		return dst
	}

	limiter := m.getLimiter(m.downloadMap, normalizeBandwidthUserID(userID), kbps)
	if limiter == nil {
		return dst
	}
	return &throttledWriter{
		ctx:     ctx,
		dst:     dst,
		limiter: limiter,
	}
}

func (m *BandwidthManager) getLimiter(group map[string]*rate.Limiter, userID string, kbps int64) *rate.Limiter {
	if kbps <= 0 {
		return nil
	}
	if userID == "" {
		userID = "anonymous"
	}

	bytesPerSecond := kbps * 1024
	if bytesPerSecond <= 0 {
		return nil
	}

	limit := rate.Limit(float64(bytesPerSecond))
	burst := int(bytesPerSecond / 4)
	if burst < minBurstBytes {
		burst = minBurstBytes
	}
	if burst > maxBurstBytes {
		burst = maxBurstBytes
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	if limiter, ok := group[userID]; ok {
		limiter.SetLimitAt(time.Now(), limit)
		limiter.SetBurstAt(time.Now(), burst)
		return limiter
	}

	limiter := rate.NewLimiter(limit, burst)
	group[userID] = limiter
	return limiter
}

type throttledReader struct {
	ctx     context.Context
	src     io.Reader
	limiter *rate.Limiter
}

func (r *throttledReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return r.src.Read(p)
	}
	if err := waitWithLimiter(r.ctx, r.limiter, len(p)); err != nil {
		return 0, err
	}
	return r.src.Read(p)
}

type throttledWriter struct {
	ctx     context.Context
	dst     io.Writer
	limiter *rate.Limiter
}

func (w *throttledWriter) Write(p []byte) (int, error) {
	if len(p) == 0 {
		return w.dst.Write(p)
	}
	if err := waitWithLimiter(w.ctx, w.limiter, len(p)); err != nil {
		return 0, err
	}
	return w.dst.Write(p)
}

func waitWithLimiter(ctx context.Context, limiter *rate.Limiter, totalBytes int) error {
	if limiter == nil || totalBytes <= 0 {
		return nil
	}

	remaining := totalBytes
	for remaining > 0 {
		step := remaining
		if step > maxWaitChunkBytes {
			step = maxWaitChunkBytes
		}
		if err := limiter.WaitN(ctx, step); err != nil {
			return err
		}
		remaining -= step
	}
	return nil
}

func normalizeBandwidthUserID(value string) string {
	return strings.TrimSpace(value)
}
