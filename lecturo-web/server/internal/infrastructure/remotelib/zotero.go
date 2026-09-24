package remotelib

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path"
	"strings"
	"time"

	"lecturo/server/internal/infrastructure/servers"
)

type zoteroSource struct{}

type zoteroItem struct {
	Key  string `json:"key"`
	Data struct {
		ItemType    string `json:"itemType"`
		Title       string `json:"title"`
		Filename    string `json:"filename"`
		ContentType string `json:"contentType"`
		Creators    []struct {
			Name      string `json:"name"`
			FirstName string `json:"firstName"`
			LastName  string `json:"lastName"`
		} `json:"creators"`
	} `json:"data"`
}

func zoteroBase() string {
	if v := os.Getenv("LECTURO_ZOTERO_BASE_URL"); v != "" {
		return strings.TrimRight(v, "/")
	}
	return "https://api.zotero.org"
}

func (zoteroSource) Browse(ctx context.Context, creds servers.Credentials, _ string) (Page, error) {
	return zoteroList(ctx, creds, "")
}

func (zoteroSource) Search(ctx context.Context, creds servers.Credentials, query string) (Page, error) {
	return zoteroList(ctx, creds, query)
}

func zoteroList(ctx context.Context, creds servers.Credentials, query string) (Page, error) {
	if creds.ZoteroUserID == "" || creds.ZoteroAPIKey == "" {
		return Page{}, ErrNotConfigured
	}

	target := fmt.Sprintf("%s/users/%s/items?format=json&limit=100",
		zoteroBase(), url.PathEscape(creds.ZoteroUserID))
	if query != "" {
		target += "&q=" + url.QueryEscape(query)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return Page{}, fmt.Errorf("remotelib: bad url: %w", err)
	}
	req.Header.Set("Zotero-API-Key", creds.ZoteroAPIKey)
	req.Header.Set("Accept", "application/json")

	resp, err := remoteHTTP.Do(req)
	if err != nil {
		return Page{}, fmt.Errorf("%w: %v", ErrUpstream, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return Page{}, fmt.Errorf("%w: zotero answered %s", ErrUpstream, resp.Status)
	}

	var items []zoteroItem
	if err := json.NewDecoder(io.LimitReader(resp.Body, 8<<20)).Decode(&items); err != nil {
		return Page{}, fmt.Errorf("%w: could not parse the library: %v", ErrUpstream, err)
	}

	page := Page{Title: "Zotero", Searchable: true}
	for _, it := range items {
		if it.Data.ItemType != "attachment" {
			continue
		}
		format := formatFromName(it.Data.Filename, it.Data.ContentType)
		if format == "" {
			continue
		}
		page.Items = append(page.Items, Item{
			RemoteID: it.Key,
			Title:    titleOrFilename(it.Data.Title, it.Data.Filename),
			Format:   format,
			Author:   creatorName(it.Data.Creators),
			URL: fmt.Sprintf("%s/users/%s/items/%s/file",
				zoteroBase(), url.PathEscape(creds.ZoteroUserID), url.PathEscape(it.Key)),
		})
	}
	return page, nil
}

func (zoteroSource) Fetch(ctx context.Context, creds servers.Credentials, target string) ([]byte, string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return nil, "", fmt.Errorf("remotelib: bad url: %w", err)
	}
	if creds.ZoteroAPIKey != "" {
		req.Header.Set("Zotero-API-Key", creds.ZoteroAPIKey)
	}

	resp, err := zoteroHTTP.Do(req)
	if err != nil {
		return nil, "", fmt.Errorf("%w: %v", ErrUpstream, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, "", fmt.Errorf("%w: zotero answered %s", ErrUpstream, resp.Status)
	}

	data, err := io.ReadAll(io.LimitReader(resp.Body, maxRemoteBytes+1))
	if err != nil {
		return nil, "", fmt.Errorf("%w: %v", ErrUpstream, err)
	}
	if int64(len(data)) > maxRemoteBytes {
		return nil, "", fmt.Errorf("%w: file is too large", ErrUpstream)
	}
	return data, resp.Header.Get("Content-Type"), nil
}

func formatFromName(filename, contentType string) string {
	switch {
	case strings.HasPrefix(contentType, epubMIME), strings.EqualFold(path.Ext(filename), ".epub"):
		return "epub"
	case strings.HasPrefix(contentType, pdfMIME), strings.EqualFold(path.Ext(filename), ".pdf"):
		return "pdf"
	default:
		return ""
	}
}

func titleOrFilename(title, filename string) string {
	if strings.TrimSpace(title) != "" {
		return title
	}
	base := path.Base(filename)
	return strings.TrimSuffix(base, path.Ext(base))
}

func creatorName(creators []struct {
	Name      string `json:"name"`
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
}) string {
	if len(creators) == 0 {
		return ""
	}
	c := creators[0]
	if strings.TrimSpace(c.Name) != "" {
		return c.Name
	}
	return strings.TrimSpace(c.FirstName + " " + c.LastName)
}

// zoteroHTTP follows Zotero's redirect to signed storage URLs, dropping the API
// key when the host changes so it is never sent to the storage backend.
var zoteroHTTP = &http.Client{
	Timeout: 90 * time.Second,
	CheckRedirect: func(req *http.Request, via []*http.Request) error {
		if len(via) > 0 && req.URL.Host != via[0].URL.Host {
			req.Header.Del("Zotero-API-Key")
		}
		return nil
	},
}
