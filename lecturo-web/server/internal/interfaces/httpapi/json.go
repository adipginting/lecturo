package httpapi

import (
	"encoding/json"
	"log"
	"net/http"
)

type apiError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// Error codes shared with the client (see api/openapi.yaml).
const (
	codeInvalidPayload        = "invalid_payload"
	codeInvalidEmail          = "invalid_email"
	codeWeakPassword          = "weak_password"
	codeEmailTaken            = "email_taken"
	codeInvalidCreds          = "invalid_credentials"
	codeUnauthorized          = "unauthorized"
	codeCSRF                  = "csrf_failed"
	codeNotImplemented        = "not_implemented"
	codeInternal              = "internal_error"
	codeNotFound              = "not_found"
	codeNotABook              = "not_a_book"
	codePayloadTooLarge       = "payload_too_large"
	codeUpstreamFailed        = "upstream_failed"
	codePromptCap             = "prompt_cap_reached"
	codeProviderUnconfigured  = "provider_unconfigured"
	codeEncryptionUnavailable = "encryption_unavailable"
	codeNothingToCompact      = "nothing_to_compact"
	codeNotAnImage            = "not_an_image"
	codeVisionUnsupported     = "vision_unsupported"
)

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if v == nil {
		return
	}
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Printf("write json: %v", err)
	}
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, map[string]apiError{"error": {Code: code, Message: message}})
}

func decodeJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20))
	if err := dec.Decode(dst); err != nil {
		writeError(w, http.StatusBadRequest, codeInvalidPayload, "The request body is not valid JSON.")
		return false
	}
	return true
}
