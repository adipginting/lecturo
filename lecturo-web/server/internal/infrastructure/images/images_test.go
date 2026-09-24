package images

import "testing"

func TestDetect(t *testing.T) {
	cases := map[string]string{
		"png":  "image/png",
		"jpeg": "image/jpeg",
		"gif":  "image/gif",
		"webp": "image/webp",
		"text": "",
	}

	fixtures := map[string][]byte{
		"png":  append([]byte{0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}, make([]byte, 8)...),
		"jpeg": {0xff, 0xd8, 0xff, 0xe0, 0x00},
		"gif":  []byte("GIF89a...."),
		"webp": []byte("RIFF\x00\x00\x00\x00WEBP"),
		"text": []byte("this is not an image at all"),
	}

	for name, want := range cases {
		if got := Detect(fixtures[name]); got != want {
			t.Fatalf("%s: got %q, want %q", name, got, want)
		}
	}
}
