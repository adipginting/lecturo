package chat

import (
	"testing"
	"time"
)

func draft() Conversation {
	return NewDraft("c1", "deepseek", TextExcerpt("Throw away thy books."), nil)
}

func TestANewConversationIsADraft(t *testing.T) {
	if !draft().IsDraft() {
		t.Fatal("a new conversation is a draft")
	}
	if draft().Title != Untitled {
		t.Fatalf("a draft is titled %q", draft().Title)
	}
}

func TestSendingNamesItFromTheFirstMessageAndMarksItSent(t *testing.T) {
	at := time.Unix(1000, 0)
	sent := draft().Send("Why throw away the books?", at)

	if sent.IsDraft() {
		t.Fatal("sending should clear the draft state")
	}
	if sent.Title != "Why throw away the books?" {
		t.Fatalf("title = %q", sent.Title)
	}
	if sent.SentAt == nil || !sent.SentAt.Equal(at) {
		t.Fatalf("sentAt = %v", sent.SentAt)
	}
}

func TestASentConversationCannotBeSentAgain(t *testing.T) {
	sent := draft().Send("first", time.Unix(1, 0))
	again := sent.Send("second", time.Unix(2, 0))

	if again.Title != sent.Title {
		t.Fatalf("resend changed the title: %q -> %q", sent.Title, again.Title)
	}
	if again.SentAt == nil || !again.SentAt.Equal(*sent.SentAt) {
		t.Fatal("resend changed sentAt")
	}
}

func TestRenamingNeverLeavesItNameless(t *testing.T) {
	sent := draft().Send("first", time.Unix(1, 0))

	if got := sent.Rename("   ").Title; got != "first" {
		t.Fatalf("blank rename gave %q, want the old title", got)
	}
	if got := sent.Rename("  Benjamin   Franklin  ").Title; got != "Benjamin Franklin" {
		t.Fatalf("rename gave %q", got)
	}
}

func TestADraftWithABlankFirstMessageFallsBackToUntitled(t *testing.T) {
	sent := draft().Send("   ", time.Unix(1, 0))
	if sent.Title != Untitled {
		t.Fatalf("title = %q, want %q", sent.Title, Untitled)
	}
}

func TestExcerptIsEmptyOnlyWhenItHoldsNothing(t *testing.T) {
	if !(Excerpt{}).IsEmpty() {
		t.Fatal("an empty excerpt holds nothing")
	}
	if TextExcerpt("x").IsEmpty() || ImageExcerpt("i").IsEmpty() {
		t.Fatal("a text or image excerpt is not empty")
	}
}

func TestMessageActivity(t *testing.T) {
	if !(Message{}).IsActive() {
		t.Fatal("a fresh message is active")
	}
	if (Message{Superseded: true}).IsActive() {
		t.Fatal("a superseded message is not active")
	}
}
