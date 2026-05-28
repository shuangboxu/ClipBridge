package clipboard

import "time"

const (
	ContentTypeText = "text"
)

type Item struct {
	ID             string
	UserID         string
	Seq            int64
	ContentType    string
	TextContent    string
	ContentHash    string
	OriginDeviceID string
	CreatedAt      time.Time
}

type SyncSnapshot struct {
	LatestSeq           int64
	CurrentDeviceAckSeq int64
}

type CreateTextItemParams struct {
	UserID         string
	OriginDeviceID string
	TextContent    string
	ContentHash    string
}

type CreateTextItemResult struct {
	Item         Item
	Deduplicated bool
}

type ListHistoryOptions struct {
	BeforeSeq *int64
	Limit     int
}

type HistoryResult struct {
	Items               []Item
	HasMore             bool
	NextBeforeSeq       *int64
	LatestSeq           int64
	CurrentDeviceAckSeq int64
}

type PullResult struct {
	Items               []Item
	SinceSeq            int64
	NextSinceSeq        int64
	HasMore             bool
	LatestSeq           int64
	CurrentDeviceAckSeq int64
}

type AckResult struct {
	Seq                 int64
	LatestSeq           int64
	CurrentDeviceAckSeq int64
}
