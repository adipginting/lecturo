// Package llm holds the streaming chat clients for each provider family.
// Requests are made server-side with the user's stored key; the browser never
// talks to a provider.
package llm

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
)

// dataURL renders an image part as a base64 data URL.
func dataURL(img ImagePart) string {
	return "data:" + img.MediaType + ";base64," + base64.StdEncoding.EncodeToString(img.Data)
}

// Message is one turn sent to the model.
type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// ImagePart is an image attached to the first user turn (a screenshot crop).
type ImagePart struct {
	MediaType string
	Data      []byte
}

// Request is a normalised completion request.
type Request struct {
	Provider  string
	BaseURL   string
	APIKey    string
	Model     string
	System    string
	Messages  []Message
	MaxTokens int
	// Images are attached to the first user message (vision providers only).
	Images []ImagePart
}

// Usage is what the provider reported for the call (may be zero).
type Usage struct {
	PromptTokens     int
	CompletionTokens int
}

// Client streams a completion, invoking onDelta for each text fragment.
type Client interface {
	Stream(ctx context.Context, req Request, onDelta func(text string) error) (Usage, error)
}

// ErrUnsupported means no client exists for the provider.
var ErrUnsupported = errors.New("llm: unsupported provider")

// For returns the client for a provider id.
func For(providerID string) (Client, error) {
	switch providerID {
	case "openai", "kimi", "openrouter", "deepseek", "lecturo":
		return openAIClient{}, nil
	case "anthropic":
		return anthropicClient{}, nil
	default:
		return nil, fmt.Errorf("%w: %s", ErrUnsupported, providerID)
	}
}
