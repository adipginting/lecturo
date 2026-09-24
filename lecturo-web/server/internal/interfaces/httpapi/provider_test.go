package httpapi_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"os"
	"testing"

	"lecturo/server/internal/infrastructure/db"
	"lecturo/server/internal/infrastructure/secretbox"
	"lecturo/server/internal/interfaces/httpapi"
)

func newProviderServer(t *testing.T) (*httptest.Server, *http.Client) {
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

	box, err := secretbox.New(bytes.Repeat([]byte{9}, 32))
	if err != nil {
		t.Fatalf("secretbox: %v", err)
	}

	srv := httpapi.New(pool, nil, false, box)
	ts := httptest.NewServer(srv.Handler())
	t.Cleanup(ts.Close)

	jar, _ := cookiejar.New(nil)
	client := &http.Client{Jar: jar}

	resp := post(t, client, ts.URL+"/api/v1/auth/register", map[string]string{
		"email": "provider@example.com", "password": "a-long-enough-password",
	}, false)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("register: status %d", resp.StatusCode)
	}
	resp.Body.Close()

	return ts, client
}

func TestProviderSettingsFlow(t *testing.T) {
	ts, client := newProviderServer(t)
	base := ts.URL + "/api/v1"

	// defaults
	getResp, _ := client.Get(base + "/provider")
	var settings struct {
		Provider  string `json:"provider"`
		BaseURL   string `json:"baseUrl"`
		Model     string `json:"model"`
		HasKey    bool   `json:"hasKey"`
		Providers []struct {
			ID         string `json:"id"`
			Configured bool   `json:"configured"`
		} `json:"providers"`
	}
	_ = json.NewDecoder(getResp.Body).Decode(&settings)
	getResp.Body.Close()
	if settings.Provider != "deepseek" || settings.HasKey {
		t.Fatalf("unexpected defaults %+v", settings)
	}
	if len(settings.Providers) != 7 {
		t.Fatalf("expected 7 providers, got %d", len(settings.Providers))
	}

	// save a key and model (write-only)
	saveResp := putBody(t, client, base+"/provider", map[string]any{
		"provider": "openai",
		"model":    "gpt-4o-mini",
		"apiKey":   "sk-test-123",
	}, true)
	if saveResp.StatusCode != http.StatusOK {
		t.Fatalf("save: status %d", saveResp.StatusCode)
	}
	var saved struct {
		Provider string `json:"provider"`
		Model    string `json:"model"`
		HasKey   bool   `json:"hasKey"`
	}
	_ = json.NewDecoder(saveResp.Body).Decode(&saved)
	saveResp.Body.Close()
	if saved.Provider != "openai" || saved.Model != "gpt-4o-mini" || !saved.HasKey {
		t.Fatalf("unexpected saved settings %+v", saved)
	}

	// the key is never returned
	raw, _ := json.Marshal(saved)
	if bytes.Contains(raw, []byte("sk-test-123")) {
		t.Fatal("the response must not contain the API key")
	}

	// configured flag for openai
	getResp2, _ := client.Get(base + "/provider")
	_ = json.NewDecoder(getResp2.Body).Decode(&settings)
	getResp2.Body.Close()
	configured := map[string]bool{}
	for _, p := range settings.Providers {
		configured[p.ID] = p.Configured
	}
	if !configured["openai"] {
		t.Fatal("openai should read as configured")
	}

	// clearing the key
	clearResp := putBody(t, client, base+"/provider", map[string]any{
		"provider": "openai",
		"apiKey":   "",
	}, true)
	var cleared struct {
		HasKey bool `json:"hasKey"`
	}
	_ = json.NewDecoder(clearResp.Body).Decode(&cleared)
	clearResp.Body.Close()
	if cleared.HasKey {
		t.Fatal("expected the key to be cleared")
	}

	// unsupported provider
	badResp := putBody(t, client, base+"/provider", map[string]any{"provider": "copilot"}, true)
	if badResp.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("copilot: status %d, want 422", badResp.StatusCode)
	}
	badResp.Body.Close()
}

func putBody(t *testing.T, client *http.Client, url string, body any, csrf bool) *http.Response {
	t.Helper()
	buf, err := json.Marshal(body)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	req, err := http.NewRequest(http.MethodPut, url, bytes.NewReader(buf))
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if csrf {
		req.Header.Set("X-CSRF-Token", csrfToken(t, client, url))
	}
	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	return resp
}
