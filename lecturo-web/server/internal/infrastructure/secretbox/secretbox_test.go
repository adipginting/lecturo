package secretbox

import (
	"bytes"
	"testing"
)

func TestSealOpen(t *testing.T) {
	box, err := New(bytes.Repeat([]byte{7}, 32))
	if err != nil {
		t.Fatalf("New: %v", err)
	}

	sealed, err := box.Seal([]byte("sk-secret"))
	if err != nil {
		t.Fatalf("Seal: %v", err)
	}
	if bytes.Contains(sealed, []byte("sk-secret")) {
		t.Fatal("ciphertext must not contain the plaintext")
	}

	opened, err := box.Open(sealed)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	if string(opened) != "sk-secret" {
		t.Fatalf("got %q", opened)
	}
}

func TestNonceIsRandom(t *testing.T) {
	box, _ := New(bytes.Repeat([]byte{1}, 32))
	a, _ := box.Seal([]byte("same"))
	b, _ := box.Seal([]byte("same"))
	if bytes.Equal(a, b) {
		t.Fatal("ciphertexts should differ (random nonce)")
	}
}

func TestParseKey(t *testing.T) {
	for _, key := range []string{
		"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",                     // base64
		"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", // hex
	} {
		if _, err := ParseKey(key); err != nil {
			t.Fatalf("ParseKey(%q): %v", key, err)
		}
	}
	if _, err := ParseKey("too-short"); err == nil {
		t.Fatal("expected an error for a short key")
	}
}

func TestOpenRejectsWrongKey(t *testing.T) {
	boxA, _ := New(bytes.Repeat([]byte{2}, 32))
	boxB, _ := New(bytes.Repeat([]byte{3}, 32))
	sealed, _ := boxA.Seal([]byte("secret"))
	if _, err := boxB.Open(sealed); err == nil {
		t.Fatal("expected decryption to fail with the wrong key")
	}
}
