package llm

import (
	"encoding/json"
	"strings"
	"testing"
)

func sampleRequest() Request {
	return Request{
		Messages: []Message{
			{Role: "user", Content: "first question"},
			{Role: "assistant", Content: "first answer"},
			{Role: "user", Content: "what is this?"},
		},
		Images: []ImagePart{{MediaType: "image/png", Data: []byte("PNGDATA")}},
	}
}

func TestImagesAttachToTheLatestUserTurn(t *testing.T) {
	messages := buildOpenAIMessages(sampleRequest())
	if len(messages) != 3 {
		t.Fatalf("expected 3 messages, got %d", len(messages))
	}

	// earlier turns stay plain strings
	if _, ok := messages[0].Content.(string); !ok {
		t.Fatalf("first user turn should be a string, got %T", messages[0].Content)
	}
	// the latest user turn becomes multimodal
	parts, ok := messages[2].Content.([]any)
	if !ok {
		t.Fatalf("latest user turn should be parts, got %T", messages[2].Content)
	}
	if len(parts) != 2 {
		t.Fatalf("expected text + image parts, got %d", len(parts))
	}
	encoded, _ := json.Marshal(parts[1])
	for _, want := range []string{"image_url", "data:image/png;base64,"} {
		if !strings.Contains(string(encoded), want) {
			t.Fatalf("image part missing %q: %s", want, encoded)
		}
	}
}

func TestAnthropicImagesAttachToTheLatestUserTurn(t *testing.T) {
	req := sampleRequest()
	req.Messages = append([]Message{{Role: "summary", Content: "earlier"}}, req.Messages...)

	messages := buildAnthropicMessages(req)
	// summary is mapped to assistant
	if messages[0].Role != "assistant" {
		t.Fatalf("summary should map to assistant, got %q", messages[0].Role)
	}

	last := messages[len(messages)-1]
	blocks, ok := last.Content.([]any)
	if !ok {
		t.Fatalf("latest user turn should be blocks, got %T", last.Content)
	}
	encoded, _ := json.Marshal(blocks)
	// Anthropic carries the bytes base64-encoded.
	for _, want := range []string{`"type":"image"`, `"media_type":"image/png"`, "UE5HREFUQQ=="} {
		if !strings.Contains(string(encoded), want) {
			t.Fatalf("image block missing %q: %s", want, encoded)
		}
	}
}
