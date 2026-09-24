package chat

// Excerpt is what a conversation is about: a passage, a crop, or both. Once
// captured it is frozen — never edited or compacted for the life of the
// conversation.
type Excerpt struct {
	Text    *string
	ImageID *string
}

// IsEmpty reports that the excerpt holds nothing.
func (e Excerpt) IsEmpty() bool { return e.Text == nil && e.ImageID == nil }

// TextExcerpt builds a passage excerpt.
func TextExcerpt(text string) Excerpt {
	value := text
	return Excerpt{Text: &value}
}

// ImageExcerpt builds a crop excerpt.
func ImageExcerpt(imageID string) Excerpt {
	value := imageID
	return Excerpt{ImageID: &value}
}
