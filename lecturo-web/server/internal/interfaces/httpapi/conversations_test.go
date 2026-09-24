package httpapi_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"lecturo/server/internal/infrastructure/db"
	"lecturo/server/internal/infrastructure/secretbox"
	"lecturo/server/internal/interfaces/httpapi"
)

// fakeChatUpstream answers OpenAI-shaped streaming completions.
func fakeChatUpstream(t *testing.T) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		flusher := w.(http.Flusher)
		for _, piece := range []string{"Hel", "lo, ", "world"} {
			fmt.Fprintf(w, "data: {\"choices\":[{\"delta\":{\"content\":%q}}]}\n\n", piece)
			flusher.Flush()
		}
		fmt.Fprint(w, "data: [DONE]\n\n")
		flusher.Flush()
	}))
}

func newChatServer(t *testing.T) (*httptest.Server, *http.Client) {
	t.Helper()

	dbURL := os.Getenv("TEST_DATABASE_URL")
	if dbURL == "" {
		t.Skip("TEST_DATABASE_URL not set")
	}

	ctx := context.Background()
	pool, err := db.Open(ctx, dbURL)
	if err != nil {
		t.Fatalf("db.Open: %v", err)
	}
	t.Cleanup(pool.Close)
	if err := db.Migrate(ctx, pool); err != nil {
		t.Fatalf("db.Migrate: %v", err)
	}
	if _, err := pool.Exec(ctx, `truncate users cascade`); err != nil {
		t.Fatalf("truncate: %v", err)
	}

	box, _ := secretbox.New(bytes.Repeat([]byte{5}, 32))
	srv := httpapi.New(pool, nil, false, box)
	ts := httptest.NewServer(srv.Handler())
	t.Cleanup(ts.Close)

	jar, _ := cookiejar.New(nil)
	client := &http.Client{Jar: jar}

	resp := post(t, client, ts.URL+"/api/v1/auth/register", map[string]string{
		"email": "chat@example.com", "password": "a-long-enough-password",
	}, false)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("register: status %d", resp.StatusCode)
	}
	resp.Body.Close()
	return ts, client
}

func configureProvider(t *testing.T, client *http.Client, base, upstreamURL string) {
	t.Helper()
	resp := putBody(t, client, base+"/provider", map[string]any{
		"provider": "openai",
		"baseUrl":  upstreamURL,
		"model":    "test-model",
		"apiKey":   "sk-test",
	}, true)
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("configure provider: status %d", resp.StatusCode)
	}
	resp.Body.Close()
}

func TestConversationsFlow(t *testing.T) {
	upstream := fakeChatUpstream(t)
	defer upstream.Close()

	ts, client := newChatServer(t)
	base := ts.URL + "/api/v1"
	configureProvider(t, client, base, upstream.URL)

	// create a conversation from an excerpt
	createResp := post(t, client, base+"/conversations", map[string]string{
		"excerpt":  "Throw away thy books; no longer distract thyself.",
		"locator":  "12",
		"provider": "openai",
	}, true)
	if createResp.StatusCode != http.StatusCreated {
		t.Fatalf("create conversation: status %d", createResp.StatusCode)
	}
	var conv struct {
		ID      string `json:"id"`
		Title   string `json:"title"`
		IsDraft bool   `json:"isDraft"`
	}
	_ = json.NewDecoder(createResp.Body).Decode(&conv)
	createResp.Body.Close()
	if conv.ID == "" || !conv.IsDraft || conv.Title != "New conversation" {
		t.Fatalf("a new conversation should be an untitled draft, got %+v", conv)
	}

	// non-streaming reply
	msgResp := post(t, client, base+"/conversations/"+conv.ID+"/messages?stream=false",
		map[string]string{"content": "Why throw away the books?"}, true)
	if msgResp.StatusCode != http.StatusOK {
		t.Fatalf("message: status %d", msgResp.StatusCode)
	}
	var assistant struct {
		Role    string `json:"role"`
		Content string `json:"content"`
	}
	_ = json.NewDecoder(msgResp.Body).Decode(&assistant)
	msgResp.Body.Close()
	if assistant.Role != "assistant" || assistant.Content != "Hello, world" {
		t.Fatalf("unexpected assistant message %+v", assistant)
	}

	// the conversation is named after the first user message
	getResp, _ := client.Get(base + "/conversations/" + conv.ID)
	var detail struct {
		Title    *string `json:"title"`
		Messages []struct {
			Role       string `json:"role"`
			Superseded bool   `json:"superseded"`
		} `json:"messages"`
	}
	_ = json.NewDecoder(getResp.Body).Decode(&detail)
	getResp.Body.Close()
	if detail.Title == nil || *detail.Title != "Why throw away the books?" {
		t.Fatalf("conversation not named: %+v", detail.Title)
	}
	if len(detail.Messages) != 2 {
		t.Fatalf("expected 2 messages, got %d", len(detail.Messages))
	}

	// a second exchange, then compaction
	msgResp2 := post(t, client, base+"/conversations/"+conv.ID+"/messages?stream=false",
		map[string]string{"content": "How do I practise it?"}, true)
	msgResp2.Body.Close()

	compactResp := post(t, client, base+"/conversations/"+conv.ID+"/compact", nil, true)
	if compactResp.StatusCode != http.StatusOK {
		t.Fatalf("compact: status %d", compactResp.StatusCode)
	}
	var summary struct {
		Role       string `json:"role"`
		IsSummary  bool   `json:"isSummary"`
		Superseded bool   `json:"superseded"`
	}
	_ = json.NewDecoder(compactResp.Body).Decode(&summary)
	compactResp.Body.Close()
	if !summary.IsSummary || summary.Role != "assistant" || summary.Superseded {
		t.Fatalf("a summary is an active assistant message flagged isSummary, got %+v", summary)
	}

	// the earlier exchanges are marked superseded but kept
	afterResp, _ := client.Get(base + "/conversations/" + conv.ID)
	var after struct {
		Messages []struct {
			Role       string `json:"role"`
			IsSummary  bool   `json:"isSummary"`
			Superseded bool   `json:"superseded"`
		} `json:"messages"`
	}
	_ = json.NewDecoder(afterResp.Body).Decode(&after)
	afterResp.Body.Close()
	superseded := 0
	hasSummary := false
	for _, m := range after.Messages {
		if m.Superseded {
			superseded++
		}
		if m.IsSummary {
			hasSummary = true
		}
	}
	if superseded != 4 || !hasSummary {
		t.Fatalf("expected 4 superseded and a summary, got %d / %v", superseded, hasSummary)
	}
}

func TestMessageStreaming(t *testing.T) {
	upstream := fakeChatUpstream(t)
	defer upstream.Close()

	ts, client := newChatServer(t)
	base := ts.URL + "/api/v1"
	configureProvider(t, client, base, upstream.URL)

	createResp := post(t, client, base+"/conversations",
		map[string]string{"excerpt": "A passage.", "provider": "openai"}, true)
	var conv struct {
		ID string `json:"id"`
	}
	_ = json.NewDecoder(createResp.Body).Decode(&conv)
	createResp.Body.Close()

	body := bytes.NewBufferString(`{"content":"Tell me"}`)
	req, _ := http.NewRequest(http.MethodPost, base+"/conversations/"+conv.ID+"/messages", body)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-CSRF-Token", csrfToken(t, client, base+"/conversations"))
	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("stream request: %v", err)
	}
	defer resp.Body.Close()

	if ct := resp.Header.Get("Content-Type"); ct != "text/event-stream" {
		t.Fatalf("content-type %q", ct)
	}
	raw, _ := io.ReadAll(resp.Body)
	text := string(raw)

	for _, want := range []string{"event: delta", "event: done", `"text":"Hel"`, "Hello, world"} {
		if !strings.Contains(text, want) {
			t.Fatalf("stream missing %q; got:\n%s", want, text)
		}
	}
}

func TestMessageWithoutKeyIsRefused(t *testing.T) {
	ts, client := newChatServer(t)
	base := ts.URL + "/api/v1"

	// anthropic is selected but has no key
	cfg := putBody(t, client, base+"/provider", map[string]any{"provider": "anthropic"}, true)
	cfg.Body.Close()

	createResp := post(t, client, base+"/conversations",
		map[string]string{"excerpt": "A passage.", "provider": "anthropic"}, true)
	var conv struct {
		ID string `json:"id"`
	}
	_ = json.NewDecoder(createResp.Body).Decode(&conv)
	createResp.Body.Close()

	resp := post(t, client, base+"/conversations/"+conv.ID+"/messages?stream=false",
		map[string]string{"content": "hello"}, true)
	if resp.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("status %d, want 422", resp.StatusCode)
	}
	var apiErr struct {
		Error struct {
			Code string `json:"code"`
		} `json:"error"`
	}
	_ = json.NewDecoder(resp.Body).Decode(&apiErr)
	resp.Body.Close()
	if apiErr.Error.Code != "provider_unconfigured" {
		t.Fatalf("code %q, want provider_unconfigured", apiErr.Error.Code)
	}
}

func TestAutoCompactionOnBudget(t *testing.T) {
	upstream := fakeChatUpstream(t)
	defer upstream.Close()

	ts, client := newChatServer(t)
	base := ts.URL + "/api/v1"

	// A small-window model, so a big turn crosses the budget.
	cfg := putBody(t, client, base+"/provider", map[string]any{
		"provider": "openai", "baseUrl": upstream.URL, "model": "moonshot-v1-8k", "apiKey": "sk",
	}, true)
	cfg.Body.Close()

	create := post(t, client, base+"/conversations",
		map[string]string{"excerpt": "A passage.", "provider": "openai"}, true)
	var conv struct {
		ID string `json:"id"`
	}
	_ = json.NewDecoder(create.Body).Decode(&conv)
	create.Body.Close()

	big := strings.Repeat("word ", 8000) // ~40k chars, over the 8k-token budget
	msg := post(t, client, base+"/conversations/"+conv.ID+"/messages?stream=false",
		map[string]string{"content": big}, true)
	if msg.StatusCode != http.StatusOK {
		t.Fatalf("send: status %d", msg.StatusCode)
	}
	msg.Body.Close()

	get, _ := client.Get(base + "/conversations/" + conv.ID)
	var detail struct {
		Messages []struct {
			IsSummary bool `json:"isSummary"`
		} `json:"messages"`
	}
	_ = json.NewDecoder(get.Body).Decode(&detail)
	get.Body.Close()

	hasSummary := false
	for _, m := range detail.Messages {
		if m.IsSummary {
			hasSummary = true
		}
	}
	if !hasSummary {
		t.Fatal("a turn over the budget should compact first")
	}
}
