package remotelib

import (
	"context"
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"lecturo/server/internal/infrastructure/servers"
)

const rootFeed = `<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Calibre Library</title>
  <link rel="search" type="application/atom+xml" href="/opds/search?q={searchTerms}"/>
  <entry>
    <title>Inference Engineering</title>
    <id>book:1</id>
    <author><name>Ada Lovelace</name></author>
    <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/get/epub/1"/>
    <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="/get/pdf/1"/>
  </entry>
  <entry>
    <title>Series: Talks</title>
    <id>cat:1</id>
    <link rel="subsection" type="application/atom+xml" href="/opds/series/1"/>
  </entry>
</feed>`

const searchFeed = `<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Search results</title>
  <entry>
    <title>Meditations</title>
    <id>book:2</id>
    <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/get/epub/2"/>
  </entry>
</feed>`

func calibreServer(t *testing.T) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/opds":
			w.Header().Set("Content-Type", "application/atom+xml")
			_, _ = w.Write([]byte(rootFeed))
		case r.URL.Path == "/opds/search":
			if r.URL.Query().Get("q") == "" {
				t.Error("search query not forwarded")
			}
			_, _ = w.Write([]byte(searchFeed))
		case r.URL.Path == "/get/pdf/1":
			_, _ = w.Write([]byte("%PDF-1.7\n%%EOF\n"))
		default:
			http.NotFound(w, r)
		}
	}))
}

func TestCalibreBrowse(t *testing.T) {
	ts := calibreServer(t)
	defer ts.Close()

	src, _ := For("calibre")
	page, err := src.Browse(context.Background(), servers.Credentials{CalibreURL: ts.URL}, "")
	if err != nil {
		t.Fatalf("Browse: %v", err)
	}
	if page.Title != "Calibre Library" || !page.Searchable {
		t.Fatalf("unexpected page %+v", page)
	}
	if len(page.Items) != 3 {
		t.Fatalf("expected 3 items (2 formats + 1 catalog), got %d", len(page.Items))
	}

	var epub, pdf, catalog bool
	for _, it := range page.Items {
		switch {
		case it.IsCatalog:
			catalog = true
		case it.Format == "epub":
			epub = true
			if it.Author != "Ada Lovelace" {
				t.Fatalf("author not parsed: %+v", it)
			}
		case it.Format == "pdf":
			pdf = true
		}
	}
	if !epub || !pdf || !catalog {
		t.Fatalf("missing item kinds: epub=%v pdf=%v catalog=%v", epub, pdf, catalog)
	}
}

func TestCalibreSearch(t *testing.T) {
	ts := calibreServer(t)
	defer ts.Close()

	src, _ := For("calibre")
	page, err := src.Search(context.Background(), servers.Credentials{CalibreURL: ts.URL}, "meditations")
	if err != nil {
		t.Fatalf("Search: %v", err)
	}
	if !page.Searchable {
		t.Fatal("expected the search template to be detected")
	}
	if len(page.Items) != 1 || page.Items[0].Title != "Meditations" {
		t.Fatalf("unexpected search results %+v", page.Items)
	}
}

func TestCalibreBasicAuthAndFetch(t *testing.T) {
	var gotAuth string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		if r.URL.Path == "/get/pdf/1" {
			_, _ = w.Write([]byte("%PDF-1.7\n%%EOF\n"))
			return
		}
		http.NotFound(w, r)
	}))
	defer ts.Close()

	src, _ := For("calibre")
	data, _, err := src.Fetch(context.Background(), servers.Credentials{
		CalibreURL: ts.URL, CalibreUser: "reader", CalibrePassword: "secret",
	}, ts.URL+"/get/pdf/1")
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if !strings.HasPrefix(string(data), "%PDF-") {
		t.Fatalf("unexpected payload %q", data)
	}

	want := "Basic " + base64.StdEncoding.EncodeToString([]byte("reader:secret"))
	if gotAuth != want {
		t.Fatalf("basic auth header %q, want %q", gotAuth, want)
	}
}

func TestCalibreNotConfigured(t *testing.T) {
	src, _ := For("calibre")
	if _, err := src.Browse(context.Background(), servers.Credentials{}, ""); err != ErrNotConfigured {
		t.Fatalf("expected ErrNotConfigured, got %v", err)
	}
}
