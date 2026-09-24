package library

import domain "lecturo/server/internal/domain/library"

// Detect identifies a PDF or EPUB from the file's bytes (domain policy).
func Detect(data []byte) (string, bool) { return domain.Detect(data) }

func contentType(format string) string { return domain.ContentType(format) }
