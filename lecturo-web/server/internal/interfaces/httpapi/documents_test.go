package httpapi_test

import (
	"archive/zip"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"os"
	"testing"

	"lecturo/server/internal/infrastructure/blob"
	"lecturo/server/internal/infrastructure/db"
	"lecturo/server/internal/infrastructure/secretbox"
	"lecturo/server/internal/interfaces/httpapi"
)

// Opt in with both a database and an S3-compatible endpoint:
//
//	TEST_DATABASE_URL="postgres://postgres:postgres@localhost:55441/lecturo?sslmode=disable" \
//	TEST_BLOB_ENDPOINT="localhost:8333" go test ./internal/httpapi/ -run TestDocumentsFlow -v
func newLibraryServer(t *testing.T) (*httptest.Server, *http.Client) {
	t.Helper()

	dbURL := os.Getenv("TEST_DATABASE_URL")
	if dbURL == "" {
		t.Skip("TEST_DATABASE_URL not set")
	}
	blobEndpoint := os.Getenv("TEST_BLOB_ENDPOINT")
	if blobEndpoint == "" {
		t.Skip("TEST_BLOB_ENDPOINT not set")
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

	store, err := blob.New(ctx, blob.Config{
		Endpoint: blobEndpoint, AccessKey: "any", SecretKey: "any",
		Bucket: "lecturo-test", Region: "us-east-1",
	})
	if err != nil {
		t.Fatalf("blob.New: %v", err)
	}

	box, _ := secretbox.New(bytes.Repeat([]byte{4}, 32))
	srv := httpapi.New(pool, store, false, box)
	ts := httptest.NewServer(srv.Handler())
	t.Cleanup(ts.Close)

	jar, err := cookiejar.New(nil)
	if err != nil {
		t.Fatalf("cookiejar: %v", err)
	}
	client := &http.Client{Jar: jar}

	resp := post(t, client, ts.URL+"/api/v1/auth/register", map[string]string{
		"email": "library@example.com", "password": "a-long-enough-password",
	}, false)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("register: status %d", resp.StatusCode)
	}
	resp.Body.Close()

	return ts, client
}

type uploadedDoc struct {
	ID     string `json:"id"`
	Title  string `json:"title"`
	Format string `json:"format"`
}

func upload(t *testing.T, client *http.Client, base, filename string, content []byte) *http.Response {
	t.Helper()

	body := &bytes.Buffer{}
	mw := multipart.NewWriter(body)
	fw, err := mw.CreateFormFile("file", filename)
	if err != nil {
		t.Fatalf("multipart: %v", err)
	}
	if _, err := fw.Write(content); err != nil {
		t.Fatalf("write part: %v", err)
	}
	if err := mw.Close(); err != nil {
		t.Fatalf("close multipart: %v", err)
	}

	req, err := http.NewRequest(http.MethodPost, base+"/documents", body)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("X-CSRF-Token", csrfToken(t, client, base+"/documents"))

	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	return resp
}

func TestDocumentsFlow(t *testing.T) {
	ts, client := newLibraryServer(t)
	base := ts.URL + "/api/v1"

	pdfBytes := []byte("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n")
	epubBytes := minimalEPUB(t)

	// upload a PDF
	resp := upload(t, client, base, "Inference Engineering.pdf", pdfBytes)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("upload pdf: status %d", resp.StatusCode)
	}
	var pdf uploadedDoc
	_ = json.NewDecoder(resp.Body).Decode(&pdf)
	resp.Body.Close()
	if pdf.Format != "pdf" || pdf.Title != "Inference Engineering" {
		t.Fatalf("unexpected pdf doc %+v", pdf)
	}

	// upload an EPUB
	resp = upload(t, client, base, "meditations.epub", epubBytes)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("upload epub: status %d", resp.StatusCode)
	}
	var epub uploadedDoc
	_ = json.NewDecoder(resp.Body).Decode(&epub)
	resp.Body.Close()
	if epub.Format != "epub" {
		t.Fatalf("unexpected epub doc %+v", epub)
	}

	// a web page is refused by name
	resp = upload(t, client, base, "download.html", []byte("<!doctype html><html></html>"))
	if resp.StatusCode != http.StatusUnsupportedMediaType {
		t.Fatalf("upload html: status %d, want 415", resp.StatusCode)
	}
	resp.Body.Close()

	// list
	listResp, err := client.Get(base + "/documents")
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	var list struct {
		Items []uploadedDoc `json:"items"`
	}
	if err := json.NewDecoder(listResp.Body).Decode(&list); err != nil {
		t.Fatalf("decode list: %v", err)
	}
	listResp.Body.Close()
	if len(list.Items) != 2 {
		t.Fatalf("expected 2 documents, got %d", len(list.Items))
	}

	// file bytes round-trip
	fileResp, err := client.Get(base + "/documents/" + pdf.ID + "/file")
	if err != nil {
		t.Fatalf("file: %v", err)
	}
	got, _ := io.ReadAll(fileResp.Body)
	fileResp.Body.Close()
	if fileResp.Header.Get("Content-Type") != "application/pdf" {
		t.Fatalf("file content-type %q", fileResp.Header.Get("Content-Type"))
	}
	if !bytes.Equal(got, pdfBytes) {
		t.Fatal("file bytes differ from what was uploaded")
	}

	// position
	posBody, _ := json.Marshal(map[string]string{"locator": "15"})
	posReq, _ := http.NewRequest(http.MethodPut, base+"/documents/"+pdf.ID+"/position", bytes.NewReader(posBody))
	posReq.Header.Set("Content-Type", "application/json")
	posReq.Header.Set("X-CSRF-Token", csrfToken(t, client, base+"/documents"))
	posResp, err := client.Do(posReq)
	if err != nil {
		t.Fatalf("position: %v", err)
	}
	if posResp.StatusCode != http.StatusNoContent {
		t.Fatalf("position: status %d", posResp.StatusCode)
	}
	posResp.Body.Close()

	// position persisted
	getResp, _ := client.Get(base + "/documents/" + pdf.ID)
	var after struct {
		Locator *string `json:"locator"`
	}
	_ = json.NewDecoder(getResp.Body).Decode(&after)
	getResp.Body.Close()
	if after.Locator == nil || *after.Locator != "15" {
		t.Fatalf("locator not persisted: %+v", after.Locator)
	}

	// delete (CSRF required)
	delReq, _ := http.NewRequest(http.MethodDelete, base+"/documents/"+pdf.ID, nil)
	delReq.Header.Set("X-CSRF-Token", csrfToken(t, client, base+"/documents"))
	delResp, err := client.Do(delReq)
	if err != nil {
		t.Fatalf("delete: %v", err)
	}
	if delResp.StatusCode != http.StatusNoContent {
		t.Fatalf("delete: status %d", delResp.StatusCode)
	}
	delResp.Body.Close()

	afterDelete, _ := client.Get(base + "/documents/" + pdf.ID)
	if afterDelete.StatusCode != http.StatusNotFound {
		t.Fatalf("get after delete: status %d, want 404", afterDelete.StatusCode)
	}
	afterDelete.Body.Close()
}

func TestImportUrlFollowsDownloadPage(t *testing.T) {
	ts, client := newLibraryServer(t)
	base := ts.URL + "/api/v1"

	var download *httptest.Server
	download = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/page":
			w.Header().Set("Content-Type", "text/html")
			fmt.Fprintf(w, `<!doctype html><html><head>
<meta http-equiv="refresh" content="0; url=%s/real.pdf"></head>
<body>Your download has started</body></html>`, download.URL)
		case "/real.pdf":
			_, _ = w.Write([]byte("%PDF-1.7\n%%EOF\n"))
		default:
			http.NotFound(w, r)
		}
	}))
	defer download.Close()

	resp := post(t, client, base+"/documents/import-url", map[string]string{
		"url": download.URL + "/page",
	}, true)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("import url: status %d", resp.StatusCode)
	}
	var doc uploadedDoc
	_ = json.NewDecoder(resp.Body).Decode(&doc)
	resp.Body.Close()
	if doc.Format != "pdf" || doc.Title != "real" {
		t.Fatalf("unexpected document %+v", doc)
	}

	// A page with no link to a book is refused by name.
	bare := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html")
		_, _ = w.Write([]byte("<!doctype html><p>Nothing to see</p>"))
	}))
	defer bare.Close()

	refused := post(t, client, base+"/documents/import-url", map[string]string{
		"url": bare.URL + "/page",
	}, true)
	if refused.StatusCode != http.StatusUnsupportedMediaType {
		t.Fatalf("bare page: status %d, want 415", refused.StatusCode)
	}
	refused.Body.Close()
}

func minimalEPUB(t *testing.T) []byte {
	t.Helper()
	buf := new(bytes.Buffer)
	zw := zip.NewWriter(buf)
	mw, _ := zw.CreateHeader(&zip.FileHeader{Name: "mimetype", Method: zip.Store})
	_, _ = mw.Write([]byte("application/epub+zip"))
	cw, _ := zw.Create("META-INF/container.xml")
	_, _ = cw.Write([]byte(`<?xml version="1.0"?><container/>`))
	_ = zw.Close()
	return buf.Bytes()
}
