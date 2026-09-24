package llm

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// anthropicClient speaks Anthropic's native Messages API.
type anthropicClient struct{}

type anthropicRequest struct {
	Model     string             `json:"model"`
	MaxTokens int                `json:"max_tokens"`
	System    string             `json:"system,omitempty"`
	Messages  []anthropicMessage `json:"messages"`
	Stream    bool               `json:"stream"`
}

// anthropicMessage.Content is a string, or an array of content blocks.
type anthropicMessage struct {
	Role    string `json:"role"`
	Content any    `json:"content"`
}

func (anthropicClient) Stream(ctx context.Context, req Request, onDelta func(string) error) (Usage, error) {
	endpoint := anthropicEndpoint(req.BaseURL)

	maxTokens := req.MaxTokens
	if maxTokens == 0 {
		maxTokens = 1024
	}

	body, err := json.Marshal(anthropicRequest{
		Model: req.Model, MaxTokens: maxTokens, System: req.System,
		Messages: buildAnthropicMessages(req), Stream: true,
	})
	if err != nil {
		return Usage{}, err
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return Usage{}, err
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "text/event-stream")
	httpReq.Header.Set("anthropic-version", "2023-06-01")
	httpReq.Header.Set("x-api-key", req.APIKey)

	resp, err := streamClient.Do(httpReq)
	if err != nil {
		return Usage{}, fmt.Errorf("llm: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		detail, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return Usage{}, providerError(resp.Status, detail)
	}

	var usage Usage
	err = readSSE(resp.Body, func(ev sseEvent) error {
		if ev.Data == "" {
			return nil
		}
		var envelope struct {
			Type    string `json:"type"`
			Message *struct {
				Usage struct {
					InputTokens int `json:"input_tokens"`
				} `json:"usage"`
			} `json:"message"`
			Delta struct {
				Text       string `json:"text"`
				StopReason string `json:"stop_reason"`
			} `json:"delta"`
			Usage struct {
				OutputTokens int `json:"output_tokens"`
			} `json:"usage"`
			Error *struct {
				Message string `json:"message"`
			} `json:"error"`
		}
		if err := json.Unmarshal([]byte(ev.Data), &envelope); err != nil {
			return nil
		}

		switch envelope.Type {
		case "message_start":
			if envelope.Message != nil {
				usage.PromptTokens = envelope.Message.Usage.InputTokens
			}
		case "content_block_delta":
			if envelope.Delta.Text != "" {
				if err := onDelta(envelope.Delta.Text); err != nil {
					return err
				}
			}
		case "message_delta":
			usage.CompletionTokens = envelope.Usage.OutputTokens
		case "error":
			if envelope.Error != nil {
				return fmt.Errorf("llm: %s", envelope.Error.Message)
			}
		}
		return nil
	})
	return usage, err
}

// buildAnthropicMessages maps turns to Anthropic's shape: only user/assistant
// (summaries ride as assistant) and images as content blocks on the first user
// turn.
func buildAnthropicMessages(req Request) []anthropicMessage {
	out := make([]anthropicMessage, 0, len(req.Messages))
	attachAt := lastUserIndex(req.Messages)

	for i, m := range req.Messages {
		role := m.Role
		switch role {
		case "user", "assistant":
		case "summary", "system":
			role = "assistant"
		default:
			role = "user"
		}

		if i == attachAt && len(req.Images) > 0 {
			blocks := []any{map[string]any{"type": "text", "text": m.Content}}
			for _, img := range req.Images {
				blocks = append(blocks, map[string]any{
					"type": "image",
					"source": map[string]any{
						"type":       "base64",
						"media_type": img.MediaType,
						"data":       base64.StdEncoding.EncodeToString(img.Data),
					},
				})
			}
			out = append(out, anthropicMessage{Role: role, Content: blocks})
			continue
		}
		out = append(out, anthropicMessage{Role: role, Content: m.Content})
	}
	return out
}

func anthropicEndpoint(base string) string {
	base = strings.TrimRight(base, "/")
	if strings.HasSuffix(base, "/v1") {
		return base + "/messages"
	}
	return base + "/v1/messages"
}
