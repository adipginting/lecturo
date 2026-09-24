package httpapi_test

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
)

func fakeCalibre(t *testing.T) *httptest.Server {
	t.Helper()
	var ts *httptest.Server
	ts = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/opds":
			w.Header().Set("Content-Type", "application/atom+xml")
			fmt.Fprintf(w, `<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Calibre Library</title>
  <link rel="search" type="application/atom+xml" href="/opds/search?q={searchTerms}"/>
  <entry>
    <title>Inference Engineering</title><id>book:1</id>
    <author><name>Ada Lovelace</name></author>
    <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="%s/get/pdf/1"/>
  </entry>
  <entry>
    <title>Series: Talks</title><id>cat:1</id>
    <link rel="subsection" type="application/atom+xml" href="%s/opds/series/1"/>
  </entry>
</feed>`, ts.URL, ts.URL)
		case "/get/pdf/1":
			_, _ = w.Write([]byte("%PDF-1.7\n%%EOF\n"))
		default:
			http.NotFound(w, r)
		}
	}))
	return ts
}

func TestServersAndRemoteFlow(t *testing.T) {
	calibre := fakeCalibre(t)
	defer calibre.Close()

	ts, client := newLibraryServer(t)
	base := ts.URL + "/api/v1"

	// save Calibre settings (password write-only)
	cfg := putBody(t, client, base+"/servers", map[string]any{
		"calibreUrl":      calibre.URL,
		"calibreUser":     "reader",
		"calibrePassword": "secret",
	}, true)
	if cfg.StatusCode != http.StatusOK {
		t.Fatalf("save servers: status %d", cfg.StatusCode)
	}
	var settings struct {
		CalibreURL         string `json:"calibreUrl"`
		CalibreHasPassword bool   `json:"calibreHasPassword"`
	}
	_ = json.NewDecoder(cfg.Body).Decode(&settings)
	cfg.Body.Close()
	if settings.CalibreURL != calibre.URL || !settings.CalibreHasPassword {
		t.Fatalf("unexpected settings %+v", settings)
	}

	// browse
	browseResp, _ := client.Get(base + "/remote/calibre/browse")
	if browseResp.StatusCode != http.StatusOK {
		t.Fatalf("browse: status %d", browseResp.StatusCode)
	}
	var page struct {
		Title      string `json:"title"`
		Searchable bool   `json:"searchable"`
		Items      []struct {
			RemoteID   string `json:"remoteId"`
			Title      string `json:"title"`
			Format     string `json:"format"`
			IsCatalog  bool   `json:"isCatalog"`
			Downloaded bool   `json:"downloaded"`
			URL        string `json:"url"`
		} `json:"items"`
	}
	_ = json.NewDecoder(browseResp.Body).Decode(&page)
	browseResp.Body.Close()
	if page.Title != "Calibre Library" || !page.Searchable || len(page.Items) != 2 {
		t.Fatalf("unexpected page %+v", page)
	}

	var book *struct {
		RemoteID   string `json:"remoteId"`
		Title      string `json:"title"`
		Format     string `json:"format"`
		IsCatalog  bool   `json:"isCatalog"`
		Downloaded bool   `json:"downloaded"`
		URL        string `json:"url"`
	}
	for i := range page.Items {
		if page.Items[i].Format == "pdf" {
			book = &page.Items[i]
		}
	}
	if book == nil || book.Downloaded {
		t.Fatalf("expected an undownloaded pdf item: %+v", page.Items)
	}

	// download it into the library
	dlResp := post(t, client, base+"/remote/calibre/download", map[string]any{
		"remoteId": book.RemoteID,
		"url":      book.URL,
		"title":    book.Title,
	}, true)
	if dlResp.StatusCode != http.StatusCreated {
		t.Fatalf("download: status %d", dlResp.StatusCode)
	}
	var doc struct {
		ID     string  `json:"id"`
		Format string  `json:"format"`
		Source *string `json:"source"`
	}
	_ = json.NewDecoder(dlResp.Body).Decode(&doc)
	dlResp.Body.Close()
	if doc.ID == "" || doc.Format != "pdf" || doc.Source == nil || *doc.Source != "calibre" {
		t.Fatalf("unexpected document %+v", doc)
	}

	// browsing again marks it downloaded
	browseAgain, _ := client.Get(base + "/remote/calibre/browse")
	var page2 struct {
		Items []struct {
			Format     string  `json:"format"`
			Downloaded bool    `json:"downloaded"`
			DocumentID *string `json:"documentId"`
		} `json:"items"`
	}
	_ = json.NewDecoder(browseAgain.Body).Decode(&page2)
	browseAgain.Body.Close()
	marked := false
	for _, it := range page2.Items {
		if it.Format == "pdf" && it.Downloaded && it.DocumentID != nil && *it.DocumentID == doc.ID {
			marked = true
		}
	}
	if !marked {
		t.Fatalf("downloaded item was not marked: %+v", page2.Items)
	}
}

func TestRemoteSearchAndUnknownSource(t *testing.T) {
	ts, client := newLibraryServer(t)
	base := ts.URL + "/api/v1"

	// unknown source
	unknown, _ := client.Get(base + "/remote/dropbox/browse")
	if unknown.StatusCode != http.StatusNotFound {
		t.Fatalf("unknown source: status %d, want 404", unknown.StatusCode)
	}
	unknown.Body.Close()

	// calibre not configured
	notConfigured, _ := client.Get(base + "/remote/calibre/browse")
	if notConfigured.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("unconfigured: status %d, want 422", notConfigured.StatusCode)
	}
	notConfigured.Body.Close()

	// empty query is refused
	empty, _ := client.Get(base + "/remote/calibre/search?q=")
	if empty.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("empty query: status %d, want 422", empty.StatusCode)
	}
	empty.Body.Close()
}
