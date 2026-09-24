package httpapi_test

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// newRecordingUpstream answers an OpenAI-shaped stream and records the request.
func newRecordingUpstream(t *testing.T, sink *[]byte) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			http.NotFound(w, r)
			return
		}
		body, _ := io.ReadAll(r.Body)
		*sink = body

		w.Header().Set("Content-Type", "text/event-stream")
		flusher := w.(http.Flusher)
		fmt.Fprint(w, "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n")
		fmt.Fprint(w, "data: [DONE]\n\n")
		flusher.Flush()
	}))
}

func uploadImage(t *testing.T, client *http.Client, base string, content []byte) *http.Response {
	t.Helper()

	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	part, err := writer.CreateFormFile("file", "crop.png")
	if err != nil {
		t.Fatalf("multipart: %v", err)
	}
	if _, err := part.Write(content); err != nil {
		t.Fatalf("write part: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart: %v", err)
	}

	req, err := http.NewRequest(http.MethodPost, base+"/images", body)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-CSRF-Token", csrfToken(t, client, base+"/images"))

	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	return resp
}

func pngBytes() []byte {
	return append([]byte{0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}, bytes.Repeat([]byte{0}, 32)...)
}

func TestImagesFlow(t *testing.T) {
	ts, client := newLibraryServer(t)
	base := ts.URL + "/api/v1"

	raw := pngBytes()

	// upload
	resp := uploadImage(t, client, base, raw)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("upload: status %d", resp.StatusCode)
	}
	var image struct {
		ID          string `json:"id"`
		ContentType string `json:"contentType"`
	}
	_ = json.NewDecoder(resp.Body).Decode(&image)
	resp.Body.Close()
	if image.ID == "" || image.ContentType != "image/png" {
		t.Fatalf("unexpected image %+v", image)
	}

	// stream back
	getResp, err := client.Get(base + "/images/" + image.ID)
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	got, _ := io.ReadAll(getResp.Body)
	getResp.Body.Close()
	if getResp.Header.Get("Content-Type") != "image/png" {
		t.Fatalf("content-type %q", getResp.Header.Get("Content-Type"))
	}
	if !bytes.Equal(got, raw) {
		t.Fatal("image bytes differ")
	}

	// not an image is refused
	bad := uploadImage(t, client, base, []byte("just some text"))
	if bad.StatusCode != http.StatusUnsupportedMediaType {
		t.Fatalf("text upload: status %d, want 415", bad.StatusCode)
	}
	bad.Body.Close()
}

func TestImageConversationVision(t *testing.T) {
	var captured []byte
	upstream := newRecordingUpstream(t, &captured)
	defer upstream.Close()

	ts, client := newLibraryServer(t)
	base := ts.URL + "/api/v1"

	// upload a crop
	resp := uploadImage(t, client, base, pngBytes())
	var image struct {
		ID string `json:"id"`
	}
	_ = json.NewDecoder(resp.Body).Decode(&image)
	resp.Body.Close()

	// a vision provider receives the image as a multimodal part
	configure := putBody(t, client, base+"/provider", map[string]any{
		"provider": "openai", "baseUrl": upstream.URL, "model": "gpt-4o-mini", "apiKey": "sk",
	}, true)
	configure.Body.Close()

	create := post(t, client, base+"/conversations", map[string]string{
		"excerpt": "", "provider": "openai", "imageId": image.ID,
	}, true)
	if create.StatusCode != http.StatusCreated {
		t.Fatalf("create conversation: status %d", create.StatusCode)
	}
	var conversation struct {
		ID             string  `json:"id"`
		ContextImageID *string `json:"contextImageId"`
	}
	_ = json.NewDecoder(create.Body).Decode(&conversation)
	create.Body.Close()
	if conversation.ContextImageID == nil || *conversation.ContextImageID != image.ID {
		t.Fatalf("context image not stored: %+v", conversation)
	}

	msg := post(t, client, base+"/conversations/"+conversation.ID+"/messages?stream=false",
		map[string]string{"content": "What does this say?"}, true)
	if msg.StatusCode != http.StatusOK {
		t.Fatalf("message: status %d", msg.StatusCode)
	}
	msg.Body.Close()

	if !strings.Contains(string(captured), "image_url") ||
		!strings.Contains(string(captured), "data:image/png;base64,") {
		t.Fatalf("upstream did not receive a vision payload: %s", captured)
	}
	// the question that was just sent is part of the payload
	if !strings.Contains(string(captured), "What does this say?") {
		t.Fatalf("the new user message was not sent: %s", captured)
	}

	// a non-vision provider refuses the crop
	configureNoVision := putBody(t, client, base+"/provider", map[string]any{
		"provider": "deepseek", "baseUrl": upstream.URL, "model": "deepseek-chat", "apiKey": "sk",
	}, true)
	configureNoVision.Body.Close()

	create2 := post(t, client, base+"/conversations", map[string]string{
		"excerpt": "", "provider": "deepseek", "imageId": image.ID,
	}, true)
	var conversation2 struct {
		ID string `json:"id"`
	}
	_ = json.NewDecoder(create2.Body).Decode(&conversation2)
	create2.Body.Close()

	refused := post(t, client, base+"/conversations/"+conversation2.ID+"/messages?stream=false",
		map[string]string{"content": "What is this?"}, true)
	if refused.StatusCode != http.StatusUnprocessableEntity {
		t.Fatalf("non-vision provider: status %d, want 422", refused.StatusCode)
	}
	var apiErr struct {
		Error struct {
			Code string `json:"code"`
		} `json:"error"`
	}
	_ = json.NewDecoder(refused.Body).Decode(&apiErr)
	refused.Body.Close()
	if apiErr.Error.Code != "vision_unsupported" {
		t.Fatalf("code %q, want vision_unsupported", apiErr.Error.Code)
	}
}
