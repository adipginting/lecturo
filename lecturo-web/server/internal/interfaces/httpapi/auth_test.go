package httpapi_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	neturl "net/url"
	"os"
	"testing"

	"lecturo/server/internal/infrastructure/auth"
	"lecturo/server/internal/infrastructure/db"
	"lecturo/server/internal/interfaces/httpapi"
)

// Integration test against a real Postgres. Opt in with:
//
//	TEST_DATABASE_URL="postgres://postgres:postgres@localhost:55441/lecturo?sslmode=disable" \
//	  go test ./internal/httpapi/
func newTestServer(t *testing.T) (*httptest.Server, *http.Client) {
	t.Helper()

	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL not set")
	}

	ctx := context.Background()
	pool, err := db.Open(ctx, url)
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

	srv := httpapi.New(pool, nil, false, nil) // Secure cookies off: the test server is plain HTTP
	ts := httptest.NewServer(srv.Handler())
	t.Cleanup(ts.Close)

	jar, err := cookiejar.New(nil)
	if err != nil {
		t.Fatalf("cookiejar: %v", err)
	}
	return ts, &http.Client{Jar: jar}
}

func post(t *testing.T, client *http.Client, url string, body any, csrf bool) *http.Response {
	t.Helper()
	buf, err := json.Marshal(body)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(buf))
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

func csrfToken(t *testing.T, client *http.Client, rawurl string) string {
	t.Helper()
	u, err := neturl.Parse(rawurl)
	if err != nil {
		t.Fatalf("parse url: %v", err)
	}
	for _, c := range client.Jar.Cookies(u) {
		if c.Name == auth.CSRFCookie {
			return c.Value
		}
	}
	return ""
}

func TestAuthFlow(t *testing.T) {
	ts, client := newTestServer(t)
	base := ts.URL + "/api/v1/auth"

	// register
	resp := post(t, client, base+"/register", map[string]string{
		"email": "Reader@Example.com", "password": "a-long-enough-password",
	}, false)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("register: status %d", resp.StatusCode)
	}
	var user struct {
		ID    string `json:"id"`
		Email string `json:"email"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&user); err != nil {
		t.Fatalf("decode: %v", err)
	}
	resp.Body.Close()
	if user.ID == "" || user.Email != "reader@example.com" {
		t.Fatalf("unexpected user %+v", user)
	}

	// me (session cookie present)
	meResp, err := client.Get(base + "/me")
	if err != nil {
		t.Fatalf("me: %v", err)
	}
	if meResp.StatusCode != http.StatusOK {
		t.Fatalf("me: status %d", meResp.StatusCode)
	}
	meResp.Body.Close()

	// logout requires CSRF
	if got := post(t, client, base+"/logout", nil, false); got.StatusCode != http.StatusForbidden {
		t.Fatalf("logout without CSRF: status %d, want 403", got.StatusCode)
	}

	// logout with CSRF
	logoutResp := post(t, client, base+"/logout", nil, true)
	if logoutResp.StatusCode != http.StatusNoContent {
		t.Fatalf("logout: status %d", logoutResp.StatusCode)
	}
	logoutResp.Body.Close()

	// me now unauthenticated
	meResp2, _ := client.Get(base + "/me")
	if meResp2.StatusCode != http.StatusUnauthorized {
		t.Fatalf("me after logout: status %d, want 401", meResp2.StatusCode)
	}
	meResp2.Body.Close()

	// login again
	loginResp := post(t, client, base+"/login", map[string]string{
		"email": "reader@example.com", "password": "a-long-enough-password",
	}, false)
	if loginResp.StatusCode != http.StatusOK {
		t.Fatalf("login: status %d", loginResp.StatusCode)
	}
	loginResp.Body.Close()

	// duplicate register
	dupResp := post(t, client, base+"/register", map[string]string{
		"email": "reader@example.com", "password": "a-long-enough-password",
	}, false)
	if dupResp.StatusCode != http.StatusConflict {
		t.Fatalf("duplicate register: status %d, want 409", dupResp.StatusCode)
	}
	dupResp.Body.Close()

	// weak password
	weakResp := post(t, client, base+"/register", map[string]string{
		"email": "other@example.com", "password": "short",
	}, false)
	if weakResp.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("weak password: status %d, want 422", weakResp.StatusCode)
	}
	weakResp.Body.Close()

	// wrong password
	badResp := post(t, client, base+"/login", map[string]string{
		"email": "reader@example.com", "password": "definitely-not-it",
	}, false)
	if badResp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("wrong password: status %d, want 401", badResp.StatusCode)
	}
	badResp.Body.Close()
}
