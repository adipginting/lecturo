package settings

import "testing"

func TestVisionDefaultsByFamily(t *testing.T) {
	cases := map[string]bool{
		"gpt-4o-mini":              true,
		"gpt-4o":                   true,
		"claude-3-5-sonnet-latest": true,
		"openai/gpt-4o":            true, // OpenRouter prefix
		"gemini-1.5-pro":           true,
		"deepseek-chat":            false,
		"moonshot-v1-8k":           false,
		"":                         false,
	}
	for model, want := range cases {
		if got := Vision(model); got != want {
			t.Fatalf("Vision(%q) = %v, want %v", model, got, want)
		}
	}
}

func TestVisionOverrideWins(t *testing.T) {
	yes, no := true, false

	if !VisionWithOverride("deepseek-chat", &yes) {
		t.Fatal("an override of true should allow a text model to read images")
	}
	if VisionWithOverride("gpt-4o", &no) {
		t.Fatal("an override of false should disable a vision model")
	}
	if !VisionWithOverride("gpt-4o", nil) {
		t.Fatal("no override falls back to the family default")
	}
}
