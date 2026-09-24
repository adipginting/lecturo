package settings

import "strings"

// VisionCapability says whether a model can read images. It defaults by model
// family, with a per-model override the user can set.
//
// A model that cannot see images is never sent a crop: the reader dims its
// camera and explains itself rather than failing at send time.

// Vision reports the default capability for a model id.
func Vision(model string) bool {
	m := strings.ToLower(strings.TrimSpace(model))
	// OpenRouter-style ids carry a vendor prefix: "openai/gpt-4o".
	if i := strings.LastIndex(m, "/"); i >= 0 {
		m = m[i+1:]
	}

	switch {
	case strings.HasPrefix(m, "gpt-4o"),
		strings.HasPrefix(m, "gpt-4-turbo"),
		strings.HasPrefix(m, "gpt-4.1"),
		strings.HasPrefix(m, "o1"),
		strings.HasPrefix(m, "o3"):
		return true
	case strings.HasPrefix(m, "claude-3"),
		strings.HasPrefix(m, "claude-4"):
		return true
	case strings.HasPrefix(m, "gemini"):
		return true
	default:
		// Kimi (moonshot-v1) and DeepSeek are text-only.
		return false
	}
}

// VisionWithOverride applies a stored per-model override, falling back to the
// family default.
func VisionWithOverride(model string, override *bool) bool {
	if override != nil {
		return *override
	}
	return Vision(model)
}
