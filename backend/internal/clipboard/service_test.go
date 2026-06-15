package clipboard

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestNormalizeHistorySettingsInput(t *testing.T) {
	t.Run("uses default limit when omitted", func(t *testing.T) {
		input, err := normalizeHistorySettingsInput(HistorySettingsInput{
			RetentionDays: 0,
			HistoryLimit:  0,
		})

		if err != nil {
			t.Fatalf("normalize failed: %v", err)
		}
		if input.RetentionDays != defaultRetentionDays {
			t.Fatalf("expected default retention days %d, got %d", defaultRetentionDays, input.RetentionDays)
		}
		if input.HistoryLimit != defaultStoredHistoryLimit {
			t.Fatalf("expected default history limit %d, got %d", defaultStoredHistoryLimit, input.HistoryLimit)
		}
	})

	t.Run("rejects negative retention days", func(t *testing.T) {
		_, err := normalizeHistorySettingsInput(HistorySettingsInput{
			RetentionDays: -1,
			HistoryLimit:  defaultStoredHistoryLimit,
		})

		if err == nil {
			t.Fatalf("expected validation error")
		}
	})

	t.Run("rejects too large history limit", func(t *testing.T) {
		_, err := normalizeHistorySettingsInput(HistorySettingsInput{
			RetentionDays: 0,
			HistoryLimit:  maxStoredHistoryLimit + 1,
		})

		if err == nil {
			t.Fatalf("expected validation error")
		}
	})
}

func TestPullAdvancesCursorWhenOnlyDeletedItemsRemain(t *testing.T) {
	repo := &fakeClipboardRepository{
		snapshot: SyncSnapshot{
			LatestSeq:           9,
			CurrentDeviceAckSeq: 3,
		},
		pullItems: []Item{},
	}
	service := NewService(repo)

	result, err := service.Pull(context.Background(), "user-1", "device-1", 3, 50)

	if err != nil {
		t.Fatalf("pull failed: %v", err)
	}
	if result.NextSinceSeq != 9 {
		t.Fatalf("expected next_since_seq to advance to latest seq, got %d", result.NextSinceSeq)
	}
}

func TestUpdateHistorySettingsAppliesRetention(t *testing.T) {
	repo := &fakeClipboardRepository{
		snapshot: SyncSnapshot{
			LatestSeq:           20,
			CurrentDeviceAckSeq: 10,
		},
		historySettings: HistorySettings{
			UserID:        "user-1",
			RetentionDays: 0,
			HistoryLimit:  defaultStoredHistoryLimit,
			UpdatedAt:     time.Now(),
		},
		olderCleanupCount: 2,
		limitCleanupCount: 3,
	}
	service := NewService(repo)

	result, err := service.UpdateHistorySettings(context.Background(), "user-1", "device-1", HistorySettingsInput{
		RetentionDays: 7,
		HistoryLimit:  100,
	})

	if err != nil {
		t.Fatalf("update history settings failed: %v", err)
	}
	if result.DeletedCount != 5 {
		t.Fatalf("expected 5 deleted records, got %d", result.DeletedCount)
	}
	if repo.updatedSettings.RetentionDays != 7 || repo.updatedSettings.HistoryLimit != 100 {
		t.Fatalf("unexpected settings input: %+v", repo.updatedSettings)
	}
	if repo.appliedLimit != 100 {
		t.Fatalf("expected limit cleanup to use 100, got %d", repo.appliedLimit)
	}
}

type fakeClipboardRepository struct {
	snapshot          SyncSnapshot
	pullItems         []Item
	historySettings   HistorySettings
	updatedSettings   HistorySettingsInput
	olderCleanupCount int
	limitCleanupCount int
	appliedLimit      int
}

func (r *fakeClipboardRepository) TouchDeviceLastSeen(context.Context, string, string) error {
	return nil
}

func (r *fakeClipboardRepository) CreateTextItem(context.Context, CreateTextItemParams) (CreateTextItemResult, error) {
	return CreateTextItemResult{}, errors.New("not implemented")
}

func (r *fakeClipboardRepository) ListHistory(context.Context, string, ListHistoryOptions) ([]Item, bool, error) {
	return nil, false, errors.New("not implemented")
}

func (r *fakeClipboardRepository) PullItems(context.Context, string, int64, int) ([]Item, bool, error) {
	return r.pullItems, false, nil
}

func (r *fakeClipboardRepository) AckDevice(context.Context, string, string, int64) (int64, error) {
	return 0, errors.New("not implemented")
}

func (r *fakeClipboardRepository) GetSyncSnapshot(context.Context, string, string) (SyncSnapshot, error) {
	return r.snapshot, nil
}

func (r *fakeClipboardRepository) DeleteHistoryItem(context.Context, string, string) (Item, bool, error) {
	return Item{}, false, errors.New("not implemented")
}

func (r *fakeClipboardRepository) ClearHistory(context.Context, string) (int, error) {
	return 0, errors.New("not implemented")
}

func (r *fakeClipboardRepository) CleanupHistoryOlderThan(_ context.Context, _ string, _ int) (int, error) {
	return r.olderCleanupCount, nil
}

func (r *fakeClipboardRepository) ApplyHistoryLimit(_ context.Context, _ string, limit int) (int, error) {
	r.appliedLimit = limit
	return r.limitCleanupCount, nil
}

func (r *fakeClipboardRepository) GetHistorySettings(context.Context, string) (HistorySettings, error) {
	if r.historySettings.UserID == "" {
		return HistorySettings{
			UserID:        "user-1",
			RetentionDays: defaultRetentionDays,
			HistoryLimit:  defaultStoredHistoryLimit,
			UpdatedAt:     time.Now(),
		}, nil
	}
	return r.historySettings, nil
}

func (r *fakeClipboardRepository) UpdateHistorySettings(_ context.Context, userID string, input HistorySettingsInput) (HistorySettings, error) {
	r.updatedSettings = input
	r.historySettings = HistorySettings{
		UserID:        userID,
		RetentionDays: input.RetentionDays,
		HistoryLimit:  input.HistoryLimit,
		UpdatedAt:     time.Now(),
	}
	return r.historySettings, nil
}
