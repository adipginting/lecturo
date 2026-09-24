package chat

import "testing"

func TestWindowFor(t *testing.T) {
	cases := map[string]int{
		"gpt-4o-mini":              128000,
		"deepseek-chat":            64000,
		"claude-3-5-sonnet-latest": 200000,
		"moonshot-v1-8k":           8000,
		"something-unknown":        defaultWindow,
		"":                         defaultWindow,
	}
	for model, want := range cases {
		if got := WindowFor(model); got != want {
			t.Fatalf("WindowFor(%q) = %d, want %d", model, got, want)
		}
	}
}

func TestBudgetIsFlooredAndCapped(t *testing.T) {
	if got := Budget(8000); got != MinBudget {
		t.Fatalf("a small window should floor at %d, got %d", MinBudget, got)
	}
	if got := Budget(200000); got != MaxBudget {
		t.Fatalf("a huge window should cap at %d, got %d", MaxBudget, got)
	}
	if got := Budget(16000); got != 14000 {
		t.Fatalf("mid window = %d, want 14000", got)
	}
}

func TestEstimateTokens(t *testing.T) {
	if got := EstimateTokens(""); got != 0 {
		t.Fatalf("empty = %d", got)
	}
	if got := EstimateTokens("abcd"); got != 1 {
		t.Fatalf("four chars = %d, want 1", got)
	}
	if got := EstimateTokens("abcdefgh"); got != 2 {
		t.Fatalf("eight chars = %d, want 2", got)
	}
}
