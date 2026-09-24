package llm

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestOpenAIStreamParsesDeltas(t *testing.T) {
	var gotBody openAIRequest

	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			http.NotFound(w, r)
			return
		}
		if auth := r.Header.Get("Authorization"); auth != "Bearer sk-test" {
			t.Errorf("unexpected auth header %q", auth)
		}
		_ = json.NewDecoder(r.Body).Decode(&gotBody)

		w.Header().Set("Content-Type", "text/event-stream")
		flusher := w.(http.Flusher)
		for _, piece := range []string{"Hel", "lo, ", "world"} {
			fmt.Fprintf(w, "data: {\"choices\":[{\"delta\":{\"content\":%q}}]}\n\n", piece)
			flusher.Flush()
		}
		fmt.Fprint(w, "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":3}}\n\n")
		fmt.Fprint(w, "data: [DONE]\n\n")
		flusher.Flush()
	}))
	defer upstream.Close()

	client, err := For("openai")
	if err != nil {
		t.Fatalf("For: %v", err)
	}

	var out strings.Builder
	usage, err := client.Stream(context.Background(), Request{
		Provider: "openai",
		BaseURL:  upstream.URL,
		APIKey:   "sk-test",
		Model:    "test-model",
		System:   "be terse",
		Messages: []Message{{Role: "user", Content: "hi"}},
	}, func(text string) error {
		out.WriteString(text)
		return nil
	})
	if err != nil {
		t.Fatalf("Stream: %v", err)
	}

	if out.String() != "Hello, world" {
		t.Fatalf("got %q", out.String())
	}
	if usage.PromptTokens != 11 || usage.CompletionTokens != 3 {
		t.Fatalf("unexpected usage %+v", usage)
	}
	if gotBody.Model != "test-model" {
		t.Fatalf("model not forwarded: %q", gotBody.Model)
	}
	if len(gotBody.Messages) != 2 || gotBody.Messages[0].Role != "system" {
		t.Fatalf("system prompt not prepended: %+v", gotBody.Messages)
	}
	if !gotBody.Stream {
		t.Fatal("expected stream=true")
	}
}

func TestOpenAIReportsProviderErrors(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"error":{"message":"bad key"}}`))
	}))
	defer upstream.Close()

	client, _ := For("openai")
	_, err := client.Stream(context.Background(), Request{
		BaseURL: upstream.URL, Model: "m", Messages: []Message{{Role: "user", Content: "x"}},
	}, func(string) error { return nil })
	if err == nil || !strings.Contains(err.Error(), "bad key") {
		t.Fatalf("expected a provider error carrying the upstream message, got %v", err)
	}
}

func TestAnthropicStreamParsesDeltas(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/messages" {
			http.NotFound(w, r)
			return
		}
		if r.Header.Get("x-api-key") != "ak-test" {
			t.Errorf("missing x-api-key")
		}
		w.Header().Set("Content-Type", "text/event-stream")
		flusher := w.(http.Flusher)
		fmt.Fprint(w, "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}\n\n")
		fmt.Fprint(w, "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"Hi \"}}\n\n")
		fmt.Fprint(w, "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"there\"}}\n\n")
		fmt.Fprint(w, "event: message_delta\ndata: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":2}}\n\n")
		flusher.Flush()
	}))
	defer upstream.Close()

	client, _ := For("anthropic")
	var out strings.Builder
	usage, err := client.Stream(context.Background(), Request{
		BaseURL: upstream.URL, APIKey: "ak-test", Model: "claude",
		Messages: []Message{{Role: "user", Content: "hi"}},
	}, func(text string) error { out.WriteString(text); return nil })
	if err != nil {
		t.Fatalf("Stream: %v", err)
	}
	if out.String() != "Hi there" {
		t.Fatalf("got %q", out.String())
	}
	if usage.PromptTokens != 7 || usage.CompletionTokens != 2 {
		t.Fatalf("unexpected usage %+v", usage)
	}
}
