package skills

import (
	"strings"
	"testing"
)

func TestParseCatalogAcceptsAnEnvelope(t *testing.T) {
	json := `{"skills":[{"id":"summarize","name":"Summarize","price":"free"}]}`

	got, err := ParseCatalog(strings.NewReader(json))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(got) != 1 || got[0].SkillID != "summarize" || got[0].Name != "Summarize" {
		t.Fatalf("got %+v", got)
	}
	if got[0].IsPaid() {
		t.Fatalf("a free skill should not be paid")
	}
}

func TestParseCatalogAcceptsABareArrayAndDropsEntriesWithoutAnId(t *testing.T) {
	json := `[{"id":"a","name":"A"},{"name":"no id"},{"id":"b","price":"$5"}]`

	got, err := ParseCatalog(strings.NewReader(json))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(got) != 2 {
		t.Fatalf("want 2 skills, got %d", len(got))
	}
	if !got[1].IsPaid() {
		t.Fatalf("a priced skill should be paid")
	}
}

func TestParseCatalogFillsDefaults(t *testing.T) {
	got, err := ParseCatalog(strings.NewReader(`[{"id":"a"}]`))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got[0].Name != "a" || got[0].Scope != "message" || got[0].Trigger != "on_demand" {
		t.Fatalf("defaults not applied: %+v", got[0])
	}
}
