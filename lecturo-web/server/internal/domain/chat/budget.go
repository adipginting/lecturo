package chat

import "strings"

// One budget drives compaction. It comes from the model's window, less room for
// the reply and a safety margin, floored and capped: a window of a million is an
// invitation to a bill, and three constants would drift.
const (
	// MinBudget never compacts below this.
	MinBudget = 8000
	// MaxBudget never lets a call grow past this.
	MaxBudget = 32000
	// replyReserve is left for the reply plus a safety margin.
	replyReserve = 2000
	// defaultWindow is used for a model we do not know.
	defaultWindow = 32000
	// charsPerToken estimates a call before the provider reports real usage.
	charsPerToken = 4
)

type modelWindow struct {
	prefix string
	window int
}

// Known windows, matched by prefix so a dated model id still lands.
var windows = []modelWindow{
	{"gpt-4o", 128000},
	{"gpt-4", 128000},
	{"o1", 200000},
	{"deepseek", 64000},
	{"moonshot-v1-8k", 8000},
	{"moonshot-v1-32k", 32000},
	{"moonshot-v1-128k", 128000},
	{"claude-3-5", 200000},
	{"claude-3", 200000},
}

// WindowFor reports the context window of a model.
func WindowFor(model string) int {
	lower := strings.ToLower(model)
	for _, w := range windows {
		if strings.HasPrefix(lower, w.prefix) {
			return w.window
		}
	}
	return defaultWindow
}

// Budget compacts against this many tokens: the window less the reply reserve,
// clamped.
func Budget(window int) int {
	budget := window - replyReserve
	if budget < MinBudget {
		return MinBudget
	}
	if budget > MaxBudget {
		return MaxBudget
	}
	return budget
}

// EstimateTokens estimates a call at four characters to a token.
func EstimateTokens(text string) int {
	if text == "" {
		return 0
	}
	return (len(text) + charsPerToken - 1) / charsPerToken
}
