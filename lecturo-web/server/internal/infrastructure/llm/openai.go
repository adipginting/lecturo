package llm

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// openAIClient speaks the OpenAI chat-completions API. OpenAI, Kimi,
// OpenRouter, DeepSeek and the Lecturo relay all use it.
type openAIClient struct{}

type openAIRequest struct {
	Model     string          `json:"model"`
	Messages  []openAIMessage `json:"messages"`
	Stream    bool            `json:"stream"`
	MaxTokens int             `json:"max_tokens,omitempty"`
}

// openAIMessage.Content is a string, or an array of parts for a vision turn.
type openAIMessage struct {
	Role    string `json:"role"`
	Content any    `json:"content"`
}

func buildOpenAIMessages(req Request) []openAIMessage {
	out := make([]openAIMessage, 0, len(req.Messages)+1)
	if req.System != "" {
		out = append(out, openAIMessage{Role: "system", Content: req.System})
	}

	// Images ride on the latest user turn (the question being asked now).
	attachAt := lastUserIndex(req.Messages)
	for i, m := range req.Messages {
		if i == attachAt && len(req.Images) > 0 {
			parts := []any{map[string]any{"type": "text", "text": m.Content}}
			for _, img := range req.Images {
				parts = append(parts, map[string]any{
					"type":      "image_url",
					"image_url": map[string]any{"url": dataURL(img)},
				})
			}
			out = append(out, openAIMessage{Role: m.Role, Content: parts})
			continue
		}
		out = append(out, openAIMessage{Role: m.Role, Content: m.Content})
	}
	return out
}

func lastUserIndex(messages []Message) int {
	for i := len(messages) - 1; i >= 0; i-- {
		if messages[i].Role == "user" {
			return i
		}
	}
	return -1
}

type openAIChunk struct {
	Choices []struct {
		Delta struct {
			Content string `json:"content"`
		} `json:"delta"`
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
	Usage *struct {
		PromptTokens     int `json:"prompt_tokens"`
		CompletionTokens int `json:"completion_tokens"`
	} `json:"usage"`
}

func (openAIClient) Stream(ctx context.Context, req Request, onDelta func(string) error) (Usage, error) {
	endpoint := strings.TrimRight(req.BaseURL, "/") + "/chat/completions"

	body, err := json.Marshal(openAIRequest{
		Model: req.Model, Messages: buildOpenAIMessages(req), Stream: true, MaxTokens: req.MaxTokens,
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
	if req.APIKey != "" {
		httpReq.Header.Set("Authorization", "Bearer "+req.APIKey)
	}

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
		if ev.Data == "" || ev.Data == "[DONE]" {
			return nil
		}
		var chunk openAIChunk
		if err := json.Unmarshal([]byte(ev.Data), &chunk); err != nil {
			return nil // ignore keep-alives and non-JSON
		}
		if chunk.Usage != nil {
			usage.PromptTokens = chunk.Usage.PromptTokens
			usage.CompletionTokens = chunk.Usage.CompletionTokens
		}
		for _, choice := range chunk.Choices {
			text := choice.Delta.Content
			if text == "" {
				text = choice.Message.Content
			}
			if text != "" {
				if err := onDelta(text); err != nil {
					return err
				}
			}
		}
		return nil
	})
	return usage, err
}
