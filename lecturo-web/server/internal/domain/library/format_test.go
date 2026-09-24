package library

import (
	"archive/zip"
	"bytes"
	"testing"
)

func TestDetectPDF(t *testing.T) {
	format, ok := Detect([]byte("%PDF-1.7\n%\xe2\xe3\xcf\xd3\n"))
	if !ok || format != FormatPDF {
		t.Fatalf("got (%q, %v), want (pdf, true)", format, ok)
	}
}

func TestDetectEPUB(t *testing.T) {
	buf := new(bytes.Buffer)
	zw := zip.NewWriter(buf)

	mw, _ := zw.CreateHeader(&zip.FileHeader{Name: "mimetype", Method: zip.Store})
	_, _ = mw.Write([]byte("application/epub+zip"))

	fw, _ := zw.Create("META-INF/container.xml")
	_, _ = fw.Write([]byte(`<?xml version="1.0"?><container/>`))
	_ = zw.Close()

	format, ok := Detect(buf.Bytes())
	if !ok || format != FormatEPUB {
		t.Fatalf("got (%q, %v), want (epub, true)", format, ok)
	}
}

func TestDetectRejects(t *testing.T) {
	cases := map[string][]byte{
		"html":     []byte("<!doctype html><html><body>Your download has started</body></html>"),
		"empty":    {},
		"text":     []byte("just some text"),
		"plainzip": zipWithoutMimetype(t),
	}
	for name, data := range cases {
		if format, ok := Detect(data); ok {
			t.Fatalf("%s: detected %q, want not-a-book", name, format)
		}
	}
}

func zipWithoutMimetype(t *testing.T) []byte {
	t.Helper()
	buf := new(bytes.Buffer)
	zw := zip.NewWriter(buf)
	fw, _ := zw.Create("readme.txt")
	_, _ = fw.Write([]byte("not a book"))
	_ = zw.Close()
	return buf.Bytes()
}
