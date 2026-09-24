package auth

import "testing"

func TestHashAndVerify(t *testing.T) {
	const password = "correct horse battery staple"

	hash, err := HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if hash == password {
		t.Fatal("hash must not equal the password")
	}

	ok, err := VerifyPassword(hash, password)
	if err != nil {
		t.Fatalf("VerifyPassword: %v", err)
	}
	if !ok {
		t.Fatal("expected the password to verify")
	}

	ok, err = VerifyPassword(hash, "not the password")
	if err != nil {
		t.Fatalf("VerifyPassword(wrong): %v", err)
	}
	if ok {
		t.Fatal("wrong password must not verify")
	}
}

func TestHashIsSalted(t *testing.T) {
	a, _ := HashPassword("same password")
	b, _ := HashPassword("same password")
	if a == b {
		t.Fatal("hashes of the same password should differ (random salt)")
	}
}

func TestVerifyRejectsMalformed(t *testing.T) {
	for _, bad := range []string{"", "not-a-hash", "$argon2id$v=19$m=65536,t=1,p=4$only-four-parts"} {
		if _, err := VerifyPassword(bad, "x"); err == nil {
			t.Fatalf("expected an error for %q", bad)
		}
	}
}
