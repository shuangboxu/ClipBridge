package shares

import (
	"regexp"
	"slices"
	"strconv"
	"testing"
)

func TestCreateTextShareSQLPlaceholderSequence(t *testing.T) {
	assertSQLPlaceholderSequence(t, createTextShareSQL, 25)
}

func TestCreateFileShareSQLPlaceholderSequence(t *testing.T) {
	assertSQLPlaceholderSequence(t, createFileShareSQL, 30)
}

func assertSQLPlaceholderSequence(t *testing.T, sql string, expectedCount int) {
	t.Helper()

	placeholderPattern := regexp.MustCompile(`\$(\d+)`)
	matches := placeholderPattern.FindAllStringSubmatch(sql, -1)
	if len(matches) != expectedCount {
		t.Fatalf("placeholder count mismatch: got %d want %d", len(matches), expectedCount)
	}

	numbers := make([]int, 0, len(matches))
	for _, match := range matches {
		number, err := strconv.Atoi(match[1])
		if err != nil {
			t.Fatalf("parse placeholder number failed: %v", err)
		}
		numbers = append(numbers, number)
	}

	expected := make([]int, expectedCount)
	for index := range expected {
		expected[index] = index + 1
	}

	if !slices.Equal(numbers, expected) {
		t.Fatalf("placeholder sequence mismatch: got %v want %v", numbers, expected)
	}
}
