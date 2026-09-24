package httpapi_test

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"testing"
)

func deleteJSON(t *testing.T, client *http.Client, url, csrfFrom string) *http.Response {
	t.Helper()
	req, err := http.NewRequest(http.MethodDelete, url, nil)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	req.Header.Set("X-CSRF-Token", csrfToken(t, client, csrfFrom))
	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	return resp
}

func TestSavedPromptsFlow(t *testing.T) {
	ts, client := newLibraryServer(t)
	base := ts.URL + "/api/v1"

	// create up to the cap
	var firstID string
	for i := 0; i < 12; i++ {
		resp := post(t, client, base+"/prompts", map[string]string{
			"title": fmt.Sprintf("Prompt %d", i),
			"body":  "Explain this simply.",
		}, true)
		if resp.StatusCode != http.StatusCreated {
			t.Fatalf("create %d: status %d", i, resp.StatusCode)
		}
		if i == 0 {
			var p struct {
				ID string `json:"id"`
			}
			_ = json.NewDecoder(resp.Body).Decode(&p)
			firstID = p.ID
		}
		resp.Body.Close()
	}

	// the thirteenth is refused
	capped := post(t, client, base+"/prompts", map[string]string{
		"title": "One too many", "body": "no",
	}, true)
	if capped.StatusCode != http.StatusConflict {
		t.Fatalf("cap: status %d, want 409", capped.StatusCode)
	}
	capped.Body.Close()

	// list
	listResp, _ := client.Get(base + "/prompts")
	var list struct {
		Items []struct {
			ID string `json:"id"`
		} `json:"items"`
	}
	_ = json.NewDecoder(listResp.Body).Decode(&list)
	listResp.Body.Close()
	if len(list.Items) != 12 {
		t.Fatalf("expected 12 prompts, got %d", len(list.Items))
	}

	// update (partial: title only)
	updateResp := put(t, client, base+"/prompts/"+firstID, map[string]string{"title": "Renamed"}, true)
	if updateResp.StatusCode != http.StatusOK {
		t.Fatalf("update: status %d", updateResp.StatusCode)
	}
	var updated struct {
		Title string `json:"title"`
		Body  string `json:"body"`
	}
	_ = json.NewDecoder(updateResp.Body).Decode(&updated)
	updateResp.Body.Close()
	if updated.Title != "Renamed" || updated.Body != "Explain this simply." {
		t.Fatalf("unexpected update %+v", updated)
	}

	// delete frees a slot
	delResp := deleteJSON(t, client, base+"/prompts/"+firstID, base+"/prompts")
	if delResp.StatusCode != http.StatusNoContent {
		t.Fatalf("delete: status %d", delResp.StatusCode)
	}
	delResp.Body.Close()

	recreate := post(t, client, base+"/prompts", map[string]string{
		"title": "Back", "body": "yes",
	}, true)
	if recreate.StatusCode != http.StatusCreated {
		t.Fatalf("recreate after delete: status %d", recreate.StatusCode)
	}
	recreate.Body.Close()
}

func put(t *testing.T, client *http.Client, url string, body any, csrf bool) *http.Response {
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
