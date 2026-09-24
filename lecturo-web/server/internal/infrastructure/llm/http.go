package llm

import (
	"fmt"
	"net/http"
	"strings"

	"errors"
)

// streamClient has no overall timeout: streaming replies run until the caller's
// context deadline. Connect/response-header timeouts are still enforced.
var streamClient = &http.Client{}

// ErrProvider is returned when a provider answers with a non-2xx status.
var ErrProvider = errors.New("llm: provider error")

func providerError(status string, body []byte) error {
	detail := strings.TrimSpace(string(body))
	if len(detail) > 500 {
		detail = detail[:500]
	}
	if detail == "" {
		return fmt.Errorf("%w: %s", ErrProvider, status)
	}
	return fmt.Errorf("%w: %s: %s", ErrProvider, status, detail)
}
