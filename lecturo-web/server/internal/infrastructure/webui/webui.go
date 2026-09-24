// Package webui serves the built Angular app from inside the Go binary.
//
// `make web` builds the SPA and copies it into internal/webui/dist; `go:embed`
// bakes it in, so production is a single artifact. Unknown non-API paths fall
// back to index.html for client-side routing. Before the SPA has been built, a
// small placeholder is served instead.
package webui

import (
	"embed"
	"io/fs"
	"net/http"
	"strings"
)

//go:embed all:dist
var dist embed.FS

//go:embed placeholder.html
var placeholder []byte

// Handler serves the embedded SPA.
func Handler() http.Handler {
	sub, err := fs.Sub(dist, "dist")
	if err != nil {
		panic(err)
	}
	files := http.FileServer(http.FS(sub))
	_, indexErr := fs.Stat(sub, "index.html")

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}

		p := strings.TrimPrefix(r.URL.Path, "/")
		if p == "" {
			p = "index.html"
		}

		if _, err := fs.Stat(sub, p); err == nil {
			files.ServeHTTP(w, r)
			return
		}

		// SPA route: serve the shell (or the placeholder before `make web`).
		if indexErr == nil {
			clone := r.Clone(r.Context())
			clone.URL.Path = "/"
			files.ServeHTTP(w, clone)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		_, _ = w.Write(placeholder)
	})
}
