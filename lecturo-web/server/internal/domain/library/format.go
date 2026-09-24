package library

import (
	"archive/zip"
	"bytes"
	"io"
)

// Detect identifies a PDF or EPUB from the file's bytes. The whole payload is
// needed for EPUB (the mimetype entry lives in the ZIP central directory).
func Detect(data []byte) (string, bool) {
	if bytes.HasPrefix(data, []byte("%PDF-")) {
		return FormatPDF, true
	}
	if isZip(data) && isEPUB(data) {
		return FormatEPUB, true
	}
	return "", false
}

// ContentType is the MIME type for a format.
func ContentType(format string) string {
	switch format {
	case FormatPDF:
		return "application/pdf"
	case FormatEPUB:
		return "application/epub+zip"
	default:
		return "application/octet-stream"
	}
}

func isZip(b []byte) bool {
	return len(b) >= 4 &&
		b[0] == 'P' && b[1] == 'K' &&
		(b[2] == 3 || b[2] == 5 || b[2] == 7) &&
		(b[3] == 4 || b[3] == 6 || b[3] == 8)
}

// isEPUB confirms the ZIP is an EPUB: the spec requires an uncompressed
// `mimetype` entry reading "application/epub+zip"; as a fallback an OPF
// container is accepted (some tools rewrite the mimetype entry).
func isEPUB(b []byte) bool {
	zr, err := zip.NewReader(bytes.NewReader(b), int64(len(b)))
	if err != nil {
		return false
	}

	hasContainer := false
	for _, f := range zr.File {
		switch f.Name {
		case "mimetype":
			rc, err := f.Open()
			if err != nil {
				return false
			}
			head := make([]byte, 64)
			n, _ := io.ReadFull(rc, head)
			_ = rc.Close()
			if bytes.HasPrefix(head[:n], []byte("application/epub+zip")) {
				return true
			}
		case "META-INF/container.xml":
			hasContainer = true
		}
	}
	return hasContainer
}
