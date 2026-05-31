package clipboard

import "testing"

func TestShouldDeduplicateLatestItem(t *testing.T) {
	t.Run("same latest hash is always deduplicated", func(t *testing.T) {
		latestItem := Item{
			ContentHash: "hash-a",
		}

		deduplicated := shouldDeduplicateLatestItem(true, latestItem, "hash-a")

		if !deduplicated {
			t.Fatalf("expected latest identical item to be deduplicated")
		}
	})

	t.Run("different latest hash is not deduplicated", func(t *testing.T) {
		latestItem := Item{
			ContentHash: "hash-a",
		}

		deduplicated := shouldDeduplicateLatestItem(true, latestItem, "hash-b")

		if deduplicated {
			t.Fatalf("expected different latest item to create a new record")
		}
	})

	t.Run("missing latest item is not deduplicated", func(t *testing.T) {
		deduplicated := shouldDeduplicateLatestItem(false, Item{}, "hash-a")

		if deduplicated {
			t.Fatalf("expected first item to create a new record")
		}
	})
}
