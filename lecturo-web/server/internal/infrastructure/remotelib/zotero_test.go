package remotelib

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"lecturo/server/internal/infrastructure/servers"
)

const zoteroItems = `[
  {
    "key": "PDFKEY1",
    "data": {
      "itemType": "attachment",
      "title": "Inference Engineering (PDF)",
      "filename": "inference.pdf",
      "contentType": "application/pdf",
      "creators": [{"firstName": "Ada", "lastName": "Lovelace"}]
    }
  },
  {
    "key": "EPUBKEY1",
    "data": {
      "itemType": "attachment",
      "title": "",
      "filename": "meditations.epub",
      "contentType": "application/epub+zip",
      "creators": []
    }
  },
  {
    "key": "NOTE1",
    "data": {"itemType": "note", "title": "A note", "filename": "", "creators": []}
  }
]`

func zoteroServer(t *testing.T) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Zotero-API-Key") == "" {
			t.Error("missing Zotero-API-Key")
		}
		switch {
		case r.URL.Path == "/users/123/items":
			if r.URL.Query().Get("q") == "meditations" && r.URL.Query().Get("format") != "json" {
				t.Error("expected format=json")
			}
			_, _ = w.Write([]byte(zoteroItems))
		case strings.HasSuffix(r.URL.Path, "/file"):
			_, _ = w.Write([]byte("%PDF-1.7\n%%EOF\n"))
		default:
			http.NotFound(w, r)
		}
	}))
}

func TestZoteroBrowse(t *testing.T) {
	ts := zoteroServer(t)
	defer ts.Close()
	t.Setenv("LECTURO_ZOTERO_BASE_URL", ts.URL)

	creds := servers.Credentials{ZoteroUserID: "123", ZoteroAPIKey: "key"}
	src, _ := For("zotero")

	page, err := src.Browse(context.Background(), creds, "")
	if err != nil {
		t.Fatalf("Browse: %v", err)
	}
	if !page.Searchable {
		t.Fatal("zotero should be searchable")
	}
	if len(page.Items) != 2 {
		t.Fatalf("expected 2 attachments (note filtered out), got %d", len(page.Items))
	}

	var pdf, epub *Item
	for i := range page.Items {
		switch page.Items[i].Format {
		case "pdf":
			pdf = &page.Items[i]
		case "epub":
			epub = &page.Items[i]
		}
	}
	if pdf == nil || epub == nil {
		t.Fatalf("missing formats: %+v", page.Items)
	}
	if pdf.Author != "Ada Lovelace" {
		t.Fatalf("creator not combined: %+v", pdf)
	}
	if epub.Title != "meditations" {
		t.Fatalf("filename fallback failed: %q", epub.Title)
	}
	if !strings.HasSuffix(pdf.URL, "/items/PDFKEY1/file") {
		t.Fatalf("unexpected file url %q", pdf.URL)
	}
}

func TestZoteroFetch(t *testing.T) {
	ts := zoteroServer(t)
	defer ts.Close()
	t.Setenv("LECTURO_ZOTERO_BASE_URL", ts.URL)

	src, _ := For("zotero")
	data, _, err := src.Fetch(context.Background(),
		servers.Credentials{ZoteroUserID: "123", ZoteroAPIKey: "key"},
		ts.URL+"/users/123/items/PDFKEY1/file")
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if !strings.HasPrefix(string(data), "%PDF-") {
		t.Fatalf("unexpected payload %q", data)
	}
}

func TestZoteroNotConfigured(t *testing.T) {
	src, _ := For("zotero")
	if _, err := src.Browse(context.Background(), servers.Credentials{}, ""); err != ErrNotConfigured {
		t.Fatalf("expected ErrNotConfigured, got %v", err)
	}
}
