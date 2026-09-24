package remotelib

import (
	"context"
	"encoding/xml"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"lecturo/server/internal/infrastructure/servers"
)

const (
	opdsAcquisition = "http://opds-spec.org/acquisition"
	epubMIME        = "application/epub+zip"
	pdfMIME         = "application/pdf"
)

type calibreSource struct{}

type opdsFeed struct {
	Title   string      `xml:"title"`
	Links   []opdsLink  `xml:"link"`
	Entries []opdsEntry `xml:"entry"`
}

type opdsEntry struct {
	ID      string `xml:"id"`
	Title   string `xml:"title"`
	Authors []struct {
		Name string `xml:"name"`
	} `xml:"author"`
	Links []opdsLink `xml:"link"`
}

type opdsLink struct {
	Rel  string `xml:"rel,attr"`
	Type string `xml:"type,attr"`
	Href string `xml:"href,attr"`
}

func calibreBase(creds servers.Credentials) string {
	return strings.TrimRight(creds.CalibreURL, "/")
}

func (calibreSource) Browse(ctx context.Context, creds servers.Credentials, path string) (Page, error) {
	if creds.CalibreURL == "" {
		return Page{}, ErrNotConfigured
	}

	target := path
	if target == "" {
		target = calibreBase(creds) + "/opds"
	} else if !strings.HasPrefix(target, "http") {
		target = calibreBase(creds) + "/" + strings.TrimLeft(target, "/")
	}

	feed, err := fetchOPDS(ctx, creds, target)
	if err != nil {
		return Page{}, err
	}
	return pageFromOPDS(feed, path, target), nil
}

func (s calibreSource) Search(ctx context.Context, creds servers.Credentials, query string) (Page, error) {
	if creds.CalibreURL == "" {
		return Page{}, ErrNotConfigured
	}

	// Find the catalog's OpenSearch template, if it declares one.
	root, err := fetchOPDS(ctx, creds, calibreBase(creds)+"/opds")
	if err != nil {
		return Page{}, err
	}
	template := ""
	for _, link := range root.Links {
		if link.Rel == "search" {
			template = link.Href
			break
		}
	}

	var target string
	switch {
	case template != "":
		target = strings.ReplaceAll(template, "{searchTerms}", url.QueryEscape(query))
		if !strings.HasPrefix(target, "http") {
			target = resolveURL(calibreBase(creds)+"/opds", target)
		}
	default:
		target = calibreBase(creds) + "/opds/search/" + url.PathEscape(query)
	}

	feed, err := fetchOPDS(ctx, creds, target)
	if err != nil {
		return Page{}, err
	}
	page := pageFromOPDS(feed, "", target)
	page.Searchable = template != ""
	return page, nil
}

func (calibreSource) Fetch(ctx context.Context, creds servers.Credentials, target string) ([]byte, string, error) {
	req, err := newRequest(ctx, target, creds)
	if err != nil {
		return nil, "", err
	}
	resp, err := remoteHTTP.Do(req)
	if err != nil {
		return nil, "", fmt.Errorf("%w: %v", ErrUpstream, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, "", fmt.Errorf("%w: calibre answered %s", ErrUpstream, resp.Status)
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

func fetchOPDS(ctx context.Context, creds servers.Credentials, target string) (opdsFeed, error) {
	req, err := newRequest(ctx, target, creds)
	if err != nil {
		return opdsFeed{}, err
	}
	req.Header.Set("Accept", "application/atom+xml, application/xml")

	resp, err := remoteHTTP.Do(req)
	if err != nil {
		return opdsFeed{}, fmt.Errorf("%w: %v", ErrUpstream, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return opdsFeed{}, fmt.Errorf("%w: calibre answered %s", ErrUpstream, resp.Status)
	}

	var feed opdsFeed
	if err := xml.NewDecoder(io.LimitReader(resp.Body, 8<<20)).Decode(&feed); err != nil {
		return opdsFeed{}, fmt.Errorf("%w: could not parse the catalog: %v", ErrUpstream, err)
	}
	return feed, nil
}

func pageFromOPDS(feed opdsFeed, path, selfURL string) Page {
	page := Page{Title: feed.Title, Path: path}
	for _, link := range feed.Links {
		if link.Rel == "search" {
			page.Searchable = true
		}
	}

	for _, entry := range feed.Entries {
		author := ""
		if len(entry.Authors) > 0 {
			author = entry.Authors[0].Name
		}

		acquisitions := 0
		for _, link := range entry.Links {
			format := formatFromMIME(link.Type)
			if format == "" || !strings.HasPrefix(link.Rel, opdsAcquisition) {
				continue
			}
			acquisitions++
			page.Items = append(page.Items, Item{
				RemoteID: entry.ID + "#" + format,
				Title:    entry.Title,
				Format:   format,
				Author:   author,
				URL:      resolveURL(selfURL, link.Href),
			})
		}
		if acquisitions > 0 {
			continue
		}

		for _, link := range entry.Links {
			if link.Rel != "subsection" {
				continue
			}
			page.Items = append(page.Items, Item{
				RemoteID:  entry.ID,
				Title:     entry.Title,
				Author:    author,
				URL:       resolveURL(selfURL, link.Href),
				IsCatalog: true,
			})
			break
		}
	}
	return page
}

func formatFromMIME(mime string) string {
	switch {
	case strings.HasPrefix(mime, epubMIME):
		return "epub"
	case strings.HasPrefix(mime, pdfMIME):
		return "pdf"
	default:
		return ""
	}
}

func newRequest(ctx context.Context, target string, creds servers.Credentials) (*http.Request, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return nil, fmt.Errorf("remotelib: bad url: %w", err)
	}
	if creds.CalibreUser != "" {
		req.SetBasicAuth(creds.CalibreUser, creds.CalibrePassword)
	}
	return req, nil
}

func resolveURL(base, href string) string {
	if strings.HasPrefix(href, "http") {
		return href
	}
	b, err := url.Parse(base)
	if err != nil {
		return href
	}
	ref, err := url.Parse(href)
	if err != nil {
		return href
	}
	return b.ResolveReference(ref).String()
}

const maxRemoteBytes = 256 << 20

var remoteHTTP = &http.Client{Timeout: 60 * time.Second}
