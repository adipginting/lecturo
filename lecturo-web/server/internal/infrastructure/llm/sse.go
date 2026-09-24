package llm

import (
	"bufio"
	"io"
	"strings"
)

// sseEvent is one server-sent event.
type sseEvent struct {
	Event string
	Data  string
}

// readSSE scans a text/event-stream, calling fn for each event. Data lines are
// joined with newlines per the SSE spec.
func readSSE(r io.Reader, fn func(sseEvent) error) error {
	scanner := bufio.NewScanner(r)
	scanner.Buffer(make([]byte, 0, 64*1024), 4*1024*1024)

	var event, data []string
	flush := func() error {
		if len(data) == 0 && len(event) == 0 {
			return nil
		}
		ev := sseEvent{Event: strings.Join(event, "\n"), Data: strings.Join(data, "\n")}
		event, data = nil, nil
		return fn(ev)
	}

	for scanner.Scan() {
		line := scanner.Text()
		switch {
		case line == "":
			if err := flush(); err != nil {
				return err
			}
		case strings.HasPrefix(line, ":"):
			// comment / keep-alive
		case strings.HasPrefix(line, "event:"):
			event = append(event, strings.TrimSpace(strings.TrimPrefix(line, "event:")))
		case strings.HasPrefix(line, "data:"):
			data = append(data, strings.TrimSpace(strings.TrimPrefix(line, "data:")))
		}
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	return flush()
}
